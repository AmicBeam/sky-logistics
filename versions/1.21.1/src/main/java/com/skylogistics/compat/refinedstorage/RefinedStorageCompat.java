package com.skylogistics.compat.refinedstorage;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class RefinedStorageCompat {
    private static final String REFINED_STORAGE = "refinedstorage";
    private static final String STORAGE_COMPONENT =
            "com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent";
    private static final String ACTION = "com.refinedmods.refinedstorage.api.core.Action";
    private static final String ACTOR = "com.refinedmods.refinedstorage.api.storage.Actor";
    private static final String ITEM_RESOURCE = "com.refinedmods.refinedstorage.common.support.resource.ItemResource";
    private static final String FLUID_RESOURCE = "com.refinedmods.refinedstorage.common.support.resource.FluidResource";
    private static boolean warned;

    private RefinedStorageCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(REFINED_STORAGE);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        return isLoaded() ? new ItemHandler(host) : EmptyExternalHandlers.Items.INSTANCE;
    }

    public static IFluidHandler createFluidHandler(BlockEntity host) {
        return isLoaded() ? new FluidHandler(host) : EmptyExternalHandlers.Fluids.INSTANCE;
    }

    private static Object storage(BlockEntity host) {
        Object network = network(host);
        if (network == null) {
            return null;
        }
        try {
            return storageFromNetwork(network);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static Object network(BlockEntity host) {
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        BlockPos pos = host.getBlockPos();
        for (Direction direction : Direction.values()) {
            BlockPos targetPos = pos.relative(direction);
            if (!level.isLoaded(targetPos)) {
                continue;
            }
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null) {
                continue;
            }
            try {
                Object network = networkFromContainerProvider(target);
                if (network != null && storageFromNetwork(network) != null) {
                    return network;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
            }
        }
        return null;
    }

    private static Object networkFromContainerProvider(BlockEntity target) throws ReflectiveOperationException {
        Object provider = Reflect.tryInvoke(target, "getContainerProvider");
        if (provider == null) {
            return null;
        }
        Object containers = Reflect.invoke(provider, "getContainers");
        if (!(containers instanceof Iterable<?> iterable)) {
            return null;
        }
        for (Object container : iterable) {
            Object removed = Reflect.tryInvoke(container, "isRemoved");
            if (Boolean.TRUE.equals(removed)) {
                continue;
            }
            Object node = Reflect.invoke(container, "getNode");
            Object network = node == null ? null : Reflect.invoke(node, "getNetwork");
            if (network != null) {
                return network;
            }
        }
        return null;
    }

    private static Object storageFromNetwork(Object network) throws ReflectiveOperationException {
        return Reflect.invoke(network, "getComponent", Class.forName(STORAGE_COMPONENT));
    }

    private static Object action(boolean simulate) {
        return Reflect.enumConstant(ACTION, simulate ? "SIMULATE" : "EXECUTE");
    }

    private static Object action(IFluidHandler.FluidAction action) {
        return action(action.simulate());
    }

    private static Object actor() throws ReflectiveOperationException {
        return Class.forName(ACTOR).getField("EMPTY").get(null);
    }

    private static List<ResourceEntry> entries(Object storage, String resourceClassName) {
        if (storage == null) {
            return List.of();
        }
        try {
            Class<?> resourceClass = Class.forName(resourceClassName);
            Object all = Reflect.invoke(storage, "getAll");
            if (!(all instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<ResourceEntry> entries = new ArrayList<>();
            for (Object entry : iterable) {
                Object resource = Reflect.invoke(entry, "resource");
                Object amount = Reflect.invoke(entry, "amount");
                long count = amount instanceof Number number ? number.longValue() : 0L;
                if (resourceClass.isInstance(resource) && count > 0L) {
                    entries.add(new ResourceEntry(resource, count));
                }
            }
            entries.sort(Comparator.comparing(entry -> entry.resource().toString()));
            return entries;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return List.of();
        }
    }

    private static List<ResourceEntry> itemEntries(Object storage) {
        return entries(storage, ITEM_RESOURCE);
    }

    private static List<ResourceEntry> fluidEntries(Object storage) {
        return entries(storage, FLUID_RESOURCE);
    }

    private static ItemStack itemStack(ResourceEntry entry) {
        try {
            Object stack = Reflect.invoke(entry.resource(), "toItemStack");
            if (!(stack instanceof ItemStack itemStack)) {
                return ItemStack.EMPTY;
            }
            int amount = (int) Math.min(entry.amount(), itemStack.getMaxStackSize());
            return toItemStack(entry.resource(), amount);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack toItemStack(Object resource, long amount) throws ReflectiveOperationException {
        Object stack = Reflect.invoke(resource, "toItemStack", amount);
        return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
    }

    private static Object itemResource(ItemStack stack) {
        try {
            return Reflect.invokeStatic(Class.forName(ITEM_RESOURCE), "ofItemStack", stack);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static FluidStack fluidStack(ResourceEntry entry) {
        try {
            Object fluid = Reflect.invoke(entry.resource(), "fluid");
            if (!(fluid instanceof Fluid minecraftFluid)) {
                return FluidStack.EMPTY;
            }
            int amount = (int) Math.min(entry.amount(), Integer.MAX_VALUE);
            FluidStack stack = new FluidStack(minecraftFluid, amount);
            Object components = Reflect.invoke(entry.resource(), "components");
            if (components instanceof DataComponentPatch patch) {
                stack.applyComponents(patch);
            }
            return stack;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return FluidStack.EMPTY;
        }
    }

    private static Object fluidResource(FluidStack stack) {
        try {
            Class<?> type = Class.forName(FLUID_RESOURCE);
            try {
                Constructor<?> constructor = type.getConstructor(Fluid.class, DataComponentPatch.class);
                return constructor.newInstance(stack.getFluid(), stack.getComponentsPatch());
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = type.getConstructor(Fluid.class);
                return constructor.newInstance(stack.getFluid());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static ItemStack copyStackWithSize(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn("Refined Storage compat is disabled because the loaded RS API is not compatible.",
                    error);
        }
    }

    private record ResourceEntry(Object resource, long amount) {
    }

    private record ItemHandler(BlockEntity host) implements IItemHandler {
        @Override
        public int getSlots() {
            return itemEntries(storage(host)).size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<ResourceEntry> entries = itemEntries(storage(host));
            return slot >= 0 && slot < entries.size() ? itemStack(entries.get(slot)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object storage = storage(host);
            Object resource = itemResource(stack);
            if (storage == null || resource == null) {
                return stack;
            }
            try {
                int requested = stack.getCount();
                Object inserted = Reflect.invoke(storage, "insert", resource, (long) requested, action(simulate),
                        actor());
                long insertedCount = inserted instanceof Number number ? number.longValue() : 0L;
                if (insertedCount <= 0L) {
                    return stack;
                }
                int remaining = requested - (int) Math.min(insertedCount, requested);
                return remaining <= 0 ? ItemStack.EMPTY : copyStackWithSize(stack, remaining);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return stack;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            Object storage = storage(host);
            List<ResourceEntry> entries = itemEntries(storage);
            if (storage == null || slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            ResourceEntry entry = entries.get(slot);
            ItemStack display = itemStack(entry);
            if (display.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int requested = (int) Math.min(Math.min(entry.amount(), amount), display.getMaxStackSize());
            try {
                Object extracted = Reflect.invoke(storage, "extract", entry.resource(), (long) requested,
                        action(simulate), actor());
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? ItemStack.EMPTY
                        : toItemStack(entry.resource(), Math.min(extractedCount, requested));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return ItemStack.EMPTY;
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && insertItem(slot, stack, true).getCount() < stack.getCount();
        }
    }

    private record FluidHandler(BlockEntity host) implements IFluidHandler {
        @Override
        public int getTanks() {
            return fluidEntries(storage(host)).size() + 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            List<ResourceEntry> entries = fluidEntries(storage(host));
            return tank >= 0 && tank < entries.size() ? fluidStack(entries.get(tank)) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty() && fill(stack, FluidAction.SIMULATE) > 0;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            Object storage = storage(host);
            Object key = fluidResource(resource);
            if (storage == null || key == null) {
                return 0;
            }
            try {
                Object inserted = Reflect.invoke(storage, "insert", key, (long) resource.getAmount(),
                        action(action), actor());
                return inserted instanceof Number number
                        ? (int) Math.min(number.longValue(), resource.getAmount())
                        : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return 0;
            }
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            Object storage = storage(host);
            Object key = fluidResource(resource);
            if (storage == null || key == null) {
                return FluidStack.EMPTY;
            }
            try {
                Object extracted = Reflect.invoke(storage, "extract", key, (long) resource.getAmount(),
                        action(action), actor());
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? FluidStack.EMPTY
                        : fluidStack(new ResourceEntry(key, Math.min(extractedCount, resource.getAmount())));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return FluidStack.EMPTY;
            }
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            Object storage = storage(host);
            List<ResourceEntry> entries = fluidEntries(storage);
            if (storage == null || entries.isEmpty()) {
                return FluidStack.EMPTY;
            }
            ResourceEntry entry = entries.get(0);
            int requested = (int) Math.min(entry.amount(), maxDrain);
            try {
                Object extracted = Reflect.invoke(storage, "extract", entry.resource(), (long) requested,
                        action(action), actor());
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? FluidStack.EMPTY
                        : fluidStack(new ResourceEntry(entry.resource(), Math.min(extractedCount, requested)));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return FluidStack.EMPTY;
            }
        }
    }

    private static final class Reflect {
        private Reflect() {
        }

        static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
            Method method = method(target.getClass(), name, args);
            return method.invoke(target, args);
        }

        static Object tryInvoke(Object target, String name, Object... args) throws ReflectiveOperationException {
            Method method = findMethod(target.getClass(), name, args);
            return method == null ? null : method.invoke(target, args);
        }

        static Object invokeStatic(Class<?> owner, String name, Object... args) throws ReflectiveOperationException {
            Method method = method(owner, name, args);
            return method.invoke(null, args);
        }

        static Object enumConstant(String className, String constant) {
            try {
                Class<?> enumClass = Class.forName(className);
                if (!enumClass.isEnum()) {
                    return null;
                }
                for (Object value : enumClass.getEnumConstants()) {
                    if (constant.equals(((Enum<?>) value).name())) {
                        return value;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
            return null;
        }

        private static Method method(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
            Method method = findMethod(type, name, args);
            if (method == null) {
                throw new NoSuchMethodException(type.getName() + "#" + name + "/" + args.length);
            }
            return method;
        }

        private static Method findMethod(Class<?> type, String name, Object[] args) {
            Class<?> current = type;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (matches(method, name, args)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            for (Class<?> iface : type.getInterfaces()) {
                Method method = findMethod(iface, name, args);
                if (method != null) {
                    return method;
                }
            }
            return null;
        }

        private static boolean matches(Method method, String name, Object[] args) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                return false;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!isAssignable(parameterTypes[i], args[i])) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isAssignable(Class<?> parameterType, Object arg) {
            if (arg == null) {
                return !parameterType.isPrimitive();
            }
            Class<?> boxed = parameterType.isPrimitive() ? boxed(parameterType) : parameterType;
            return boxed.isInstance(arg);
        }

        private static Class<?> boxed(Class<?> primitive) {
            if (primitive == boolean.class) {
                return Boolean.class;
            }
            if (primitive == byte.class) {
                return Byte.class;
            }
            if (primitive == short.class) {
                return Short.class;
            }
            if (primitive == int.class) {
                return Integer.class;
            }
            if (primitive == long.class) {
                return Long.class;
            }
            if (primitive == float.class) {
                return Float.class;
            }
            if (primitive == double.class) {
                return Double.class;
            }
            if (primitive == char.class) {
                return Character.class;
            }
            return primitive;
        }
    }
}
