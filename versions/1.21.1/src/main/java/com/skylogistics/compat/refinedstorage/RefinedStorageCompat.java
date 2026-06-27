package com.skylogistics.compat.refinedstorage;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class RefinedStorageCompat {
    private static final String REFINED_STORAGE = "refinedstorage";
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

    private static Object network(BlockEntity host) {
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        try {
            Class<?> proxyClass = Class.forName("com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy");
            BlockPos pos = host.getBlockPos();
            for (Direction direction : Direction.values()) {
                BlockPos targetPos = pos.relative(direction);
                if (!level.isLoaded(targetPos)) {
                    continue;
                }
                BlockEntity target = level.getBlockEntity(targetPos);
                if (!proxyClass.isInstance(target)) {
                    continue;
                }
                Object node = Reflect.invoke(target, "getNode");
                Object network = node == null ? null : Reflect.invoke(node, "getNetwork");
                if (network != null
                        && Boolean.TRUE.equals(Reflect.invoke(node, "isActive"))
                        && Boolean.TRUE.equals(Reflect.invoke(network, "canRun"))) {
                    return network;
                }
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
        }
        return null;
    }

    private static Object action(boolean simulate) {
        return Reflect.enumConstant("com.refinedmods.refinedstorage.api.util.Action",
                simulate ? "SIMULATE" : "PERFORM");
    }

    private static Object action(IFluidHandler.FluidAction action) {
        return action(action.simulate());
    }

    private static List<ItemStack> itemEntries(Object network) {
        if (network == null) {
            return List.of();
        }
        try {
            Object cache = Reflect.invoke(network, "getItemStorageCache");
            Object list = cache == null ? null : Reflect.invoke(cache, "getList");
            Object stacks = list == null ? null : Reflect.invoke(list, "getStacks");
            if (!(stacks instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<ItemStack> entries = new ArrayList<>();
            for (Object entry : iterable) {
                Object stack = Reflect.invoke(entry, "getStack");
                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty() && itemStack.getCount() > 0) {
                    entries.add(itemStack.copy());
                }
            }
            entries.sort(Comparator.comparing(ItemStack::toString));
            return entries;
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return List.of();
        }
    }

    private static List<FluidStack> fluidEntries(Object network) {
        if (network == null) {
            return List.of();
        }
        try {
            Object cache = Reflect.invoke(network, "getFluidStorageCache");
            Object list = cache == null ? null : Reflect.invoke(cache, "getList");
            Object stacks = list == null ? null : Reflect.invoke(list, "getStacks");
            if (!(stacks instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<FluidStack> entries = new ArrayList<>();
            for (Object entry : iterable) {
                Object stack = Reflect.invoke(entry, "getStack");
                if (stack instanceof FluidStack fluidStack && !fluidStack.isEmpty() && fluidStack.getAmount() > 0) {
                    entries.add(fluidStack.copy());
                }
            }
            entries.sort(Comparator.comparing(FluidStack::toString));
            return entries;
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return List.of();
        }
    }

    private static ItemStack displayItem(ItemStack stack) {
        return copyStackWithSize(stack, Math.min(stack.getCount(), stack.getMaxStackSize()));
    }

    private static FluidStack displayFluid(FluidStack stack) {
        FluidStack copy = stack.copy();
        copy.setAmount(Math.min(stack.getAmount(), Integer.MAX_VALUE));
        return copy;
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn("Refined Storage compat is disabled because the loaded RS API is not compatible.",
                    error);
        }
    }

    private static ItemStack copyStackWithSize(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    private record ItemHandler(BlockEntity host) implements IItemHandler {
        @Override
        public int getSlots() {
            return itemEntries(network(host)).size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<ItemStack> entries = itemEntries(network(host));
            return slot >= 0 && slot < entries.size() ? displayItem(entries.get(slot)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object network = network(host);
            if (network == null) {
                return stack;
            }
            try {
                Object result = Reflect.invoke(network, "insertItem", stack, stack.getCount(), action(simulate));
                return result instanceof ItemStack itemStack ? itemStack : stack;
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
                return stack;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            Object network = network(host);
            List<ItemStack> entries = itemEntries(network);
            if (network == null || slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack template = entries.get(slot);
            int requested = Math.min(amount, Math.min(template.getCount(), template.getMaxStackSize()));
            try {
                Object result = Reflect.invoke(network, "extractItem",
                        copyStackWithSize(template, 1), requested, action(simulate));
                return result instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
            } catch (ReflectiveOperationException | LinkageError error) {
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
            return fluidEntries(network(host)).size() + 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            List<FluidStack> entries = fluidEntries(network(host));
            return tank >= 0 && tank < entries.size() ? displayFluid(entries.get(tank)) : FluidStack.EMPTY;
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
            Object network = network(host);
            if (network == null) {
                return 0;
            }
            try {
                Object result = Reflect.invoke(network, "insertFluid", resource, resource.getAmount(), action(action));
                return result instanceof FluidStack remainder ? resource.getAmount() - remainder.getAmount() : 0;
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
                return 0;
            }
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            Object network = network(host);
            if (network == null) {
                return FluidStack.EMPTY;
            }
            try {
                Object result = Reflect.invoke(network, "extractFluid", resource.copy(), resource.getAmount(),
                        action(action));
                return result instanceof FluidStack fluidStack ? fluidStack : FluidStack.EMPTY;
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
                return FluidStack.EMPTY;
            }
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            Object network = network(host);
            List<FluidStack> entries = fluidEntries(network);
            if (network == null || entries.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidStack request = entries.get(0).copy();
            int requested = Math.min(maxDrain, request.getAmount());
            request.setAmount(1);
            try {
                Object result = Reflect.invoke(network, "extractFluid", request, requested, action(action));
                return result instanceof FluidStack fluidStack ? fluidStack : FluidStack.EMPTY;
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
                return FluidStack.EMPTY;
            }
        }
    }

    private static final class Reflect {
        private Reflect() {
        }

        static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
            Method method = method(target.getClass(), name, args.length);
            return method.invoke(target, args);
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

        private static Method method(Class<?> type, String name, int argCount) throws NoSuchMethodException {
            Class<?> current = type;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (method.getName().equals(name) && method.getParameterCount() == argCount) {
                        method.setAccessible(true);
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            for (Class<?> iface : type.getInterfaces()) {
                try {
                    return method(iface, name, argCount);
                } catch (NoSuchMethodException ignored) {
                }
            }
            throw new NoSuchMethodException(type.getName() + "#" + name + "/" + argCount);
        }
    }
}
