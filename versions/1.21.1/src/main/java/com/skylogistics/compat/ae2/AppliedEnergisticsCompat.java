package com.skylogistics.compat.ae2;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.SkyMEInterfaceBlockEntity;
import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.registry.ModBlocks;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class AppliedEnergisticsCompat {
    private static final String AE2 = "ae2";
    private static boolean warned;

    private AppliedEnergisticsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(AE2);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        return isLoaded() ? new ItemHandler(host) : EmptyExternalHandlers.Items.INSTANCE;
    }

    public static IFluidHandler createFluidHandler(BlockEntity host) {
        return isLoaded() ? new FluidHandler(host) : EmptyExternalHandlers.Fluids.INSTANCE;
    }

    public static GridNodeHandle createGridNodeHandle(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyGridNodeHandle.INSTANCE;
        }
        try {
            return new ManagedGridNodeHandle(host);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return EmptyGridNodeHandle.INSTANCE;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void registerCapabilities(RegisterCapabilitiesEvent event,
            BlockEntityType<SkyMEInterfaceBlockEntity> type) {
        if (!isLoaded()) {
            return;
        }
        try {
            Class<?> capabilities = Class.forName("appeng.capabilities.Capabilities");
            Object capability = capabilities.getField("IN_WORLD_GRID_NODE_HOST").get(null);
            event.registerBlockEntity((BlockCapability) capability, type,
                    (host, side) -> host.ae2GridNodeHost((Direction) side));
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
        }
    }

    private static Object storage(BlockEntity host) {
        Object grid = grid(host);
        if (grid == null) {
            return null;
        }
        try {
            Object storageService = Reflect.invoke(grid, "getStorageService");
            return storageService == null ? null : Reflect.invoke(storageService, "getInventory");
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static Object grid(BlockEntity host) {
        if (host instanceof GridNodeOwner owner) {
            Object grid = owner.ae2GridNodeHandle().grid();
            if (grid != null) {
                return grid;
            }
        }
        return adjacentGrid(host);
    }

    private static Object adjacentGrid(BlockEntity host) {
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        try {
            Class<?> gridHelper = Class.forName("appeng.api.networking.GridHelper");
            BlockPos pos = host.getBlockPos();
            for (Direction direction : Direction.values()) {
                BlockPos target = pos.relative(direction);
                if (!level.isLoaded(target)) {
                    continue;
                }
                Object node = Reflect.invokeStatic(gridHelper, "getExposedNode", level, target, direction.getOpposite());
                if (node != null && Boolean.TRUE.equals(Reflect.invoke(node, "isOnline"))) {
                    return Reflect.invoke(node, "getGrid");
                }
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
        }
        return null;
    }

    private static Object action(boolean simulate) {
        return Reflect.enumConstant("appeng.api.config.Actionable", simulate ? "SIMULATE" : "MODULATE");
    }

    private static Object action(IFluidHandler.FluidAction action) {
        return action(action.simulate());
    }

    private static Object actionSource() {
        try {
            return Reflect.invokeStatic(Class.forName("appeng.api.networking.security.IActionSource"), "empty");
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static List<Entry> entries(Object storage, String keyClassName) {
        if (storage == null) {
            return List.of();
        }
        try {
            Class<?> keyClass = Class.forName(keyClassName);
            Object stacks = Reflect.invoke(storage, "getAvailableStacks");
            if (!(stacks instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<Entry> entries = new ArrayList<>();
            for (Object entry : iterable) {
                Object key = Reflect.invoke(entry, "getKey");
                Object amount = Reflect.invoke(entry, "getLongValue");
                long count = amount instanceof Number number ? number.longValue() : 0L;
                if (keyClass.isInstance(key) && count > 0L) {
                    entries.add(new Entry(key, count));
                }
            }
            entries.sort(Comparator.comparing(entry -> entry.key().toString()));
            return entries;
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return List.of();
        }
    }

    private static List<Entry> itemEntries(Object storage) {
        return entries(storage, "appeng.api.stacks.AEItemKey");
    }

    private static List<Entry> fluidEntries(Object storage) {
        return entries(storage, "appeng.api.stacks.AEFluidKey");
    }

    private static ItemStack itemStack(Entry entry) {
        try {
            Object maxStackSize = Reflect.invoke(entry.key(), "getMaxStackSize");
            int amount = (int) Math.min(entry.amount(), maxStackSize instanceof Number number ? number.intValue() : 64);
            Object stack = Reflect.invoke(entry.key(), "toStack", amount);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return ItemStack.EMPTY;
        }
    }

    private static FluidStack fluidStack(Entry entry) {
        try {
            int amount = (int) Math.min(entry.amount(), Integer.MAX_VALUE);
            Object stack = Reflect.invoke(entry.key(), "toStack", amount);
            return stack instanceof FluidStack fluidStack ? fluidStack : FluidStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return FluidStack.EMPTY;
        }
    }

    private static ItemStack copyStackWithSize(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    private static Object itemKey(ItemStack stack) {
        try {
            return Reflect.invokeStatic(Class.forName("appeng.api.stacks.AEItemKey"), "of", stack);
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static Object fluidKey(FluidStack stack) {
        try {
            return Reflect.invokeStatic(Class.forName("appeng.api.stacks.AEFluidKey"), "of", stack);
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn("AE2 compat is disabled because the loaded AE2 API is not compatible.", error);
        }
    }

    public interface GridNodeOwner {
        GridNodeHandle ae2GridNodeHandle();
    }

    public interface GridNodeHandle {
        void load(CompoundTag tag, HolderLookup.Provider registries);

        void save(CompoundTag tag, HolderLookup.Provider registries);

        void onLoad(BlockEntity host);

        void onRemove();

        Object hostCapability(Direction side);

        Object grid();
    }

    private enum EmptyGridNodeHandle implements GridNodeHandle {
        INSTANCE;

        @Override
        public void load(CompoundTag tag, HolderLookup.Provider registries) {
        }

        @Override
        public void save(CompoundTag tag, HolderLookup.Provider registries) {
        }

        @Override
        public void onLoad(BlockEntity host) {
        }

        @Override
        public void onRemove() {
        }

        @Override
        public Object hostCapability(Direction side) {
            return null;
        }

        @Override
        public Object grid() {
            return null;
        }
    }

    private static final class ManagedGridNodeHandle implements GridNodeHandle {
        private final Object node;
        private final Object exposedHost;
        private boolean removed;

        private ManagedGridNodeHandle(BlockEntity host) throws ReflectiveOperationException {
            Class<?> gridHelper = Class.forName("appeng.api.networking.GridHelper");
            Class<?> listenerClass = Class.forName("appeng.api.networking.IGridNodeListener");
            Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass},
                    new NodeListener());
            Object managedNode = Reflect.invokeStatic(gridHelper, "createManagedNode", host, listener);
            Reflect.invoke(managedNode, "setVisualRepresentation", ModBlocks.SKY_ME_INTERFACE.get());
            Reflect.invoke(managedNode, "setInWorldNode", true);
            Reflect.invoke(managedNode, "setExposedOnSides", EnumSet.allOf(Direction.class));
            Object requireChannel = Reflect.enumConstant("appeng.api.networking.GridFlags", "REQUIRE_CHANNEL");
            if (requireChannel != null) {
                Reflect.invokeSingleVararg(managedNode, "setFlags", requireChannel);
            }
            Reflect.invoke(managedNode, "setTagName", "ae2_node");
            this.node = managedNode;
            this.exposedHost = createHostProxy(host);
        }

        @Override
        public void load(CompoundTag tag, HolderLookup.Provider registries) {
            try {
                if (!Reflect.tryInvoke(node, "loadFromNBT", tag, registries)) {
                    Reflect.tryInvoke(node, "loadFromNBT", tag);
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
            }
        }

        @Override
        public void save(CompoundTag tag, HolderLookup.Provider registries) {
            try {
                if (!Reflect.tryInvoke(node, "saveToNBT", tag, registries)) {
                    Reflect.tryInvoke(node, "saveToNBT", tag);
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
            }
        }

        @Override
        public void onLoad(BlockEntity host) {
            if (host.getLevel() == null || host.getLevel().isClientSide) {
                return;
            }
            try {
                Class<?> gridHelper = Class.forName("appeng.api.networking.GridHelper");
                Reflect.invokeStatic(gridHelper, "onFirstTick", host, (Consumer<BlockEntity>) loadedHost -> {
                    try {
                        if (!removed && !loadedHost.isRemoved()
                                && !Boolean.TRUE.equals(Reflect.invoke(node, "isReady"))) {
                            Reflect.invoke(node, "create", loadedHost.getLevel(), loadedHost.getBlockPos());
                        }
                    } catch (ReflectiveOperationException | LinkageError error) {
                        warn(error);
                    }
                });
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
            }
        }

        @Override
        public void onRemove() {
            if (removed) {
                return;
            }
            removed = true;
            try {
                Reflect.invoke(node, "destroy");
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
            }
        }

        @Override
        public Object hostCapability(Direction side) {
            return exposedHost;
        }

        @Override
        public Object grid() {
            try {
                Object gridNode = Reflect.invoke(node, "getNode");
                return gridNode != null && Boolean.TRUE.equals(Reflect.invoke(gridNode, "isOnline"))
                        ? Reflect.invoke(gridNode, "getGrid")
                        : null;
            } catch (ReflectiveOperationException | LinkageError error) {
                warn(error);
                return null;
            }
        }

        private Object createHostProxy(BlockEntity host) throws ReflectiveOperationException {
            Class<?> inWorldHost = Class.forName("appeng.api.networking.IInWorldGridNodeHost");
            Class<?> actionHost = Class.forName("appeng.api.networking.security.IActionHost");
            ClassLoader classLoader = inWorldHost.getClassLoader();
            return Proxy.newProxyInstance(classLoader, new Class<?>[]{inWorldHost, actionHost},
                    new HostProxy(host, this));
        }
    }

    private record HostProxy(BlockEntity host, ManagedGridNodeHandle handle) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "getGridNode", "getActionableNode" -> Reflect.invoke(handle.node, "getNode");
                case "getCableConnectionType" -> Reflect.enumConstant("appeng.api.util.AECableType", "SMART");
                case "getActionableBlockEntity" -> host;
                case "toString" -> "SkyMEInterfaceGridHost[" + host.getBlockPos() + "]";
                default -> Reflect.defaultValue(method.getReturnType());
            };
        }
    }

    private static final class NodeListener implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (args != null && args.length > 0 && args[0] instanceof BlockEntity blockEntity) {
                if ("onInWorldConnectionChanged".equals(method.getName())) {
                    Level level = blockEntity.getLevel();
                    if (level != null) {
                        BlockState state = blockEntity.getBlockState();
                        level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, 3);
                    }
                } else if ("onSaveChanges".equals(method.getName()) || "onStateChanged".equals(method.getName())) {
                    blockEntity.setChanged();
                }
            }
            return Reflect.defaultValue(method.getReturnType());
        }
    }

    private record Entry(Object key, long amount) {
    }

    private record ItemHandler(BlockEntity host) implements IItemHandler {
        @Override
        public int getSlots() {
            return itemEntries(storage(host)).size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<Entry> entries = itemEntries(storage(host));
            return slot >= 0 && slot < entries.size() ? itemStack(entries.get(slot)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object storage = storage(host);
            Object key = itemKey(stack);
            if (storage == null || key == null) {
                return stack;
            }
            try {
                int requested = stack.getCount();
                Object inserted = Reflect.invoke(storage, "insert", key, (long) requested, action(simulate), actionSource());
                long insertedCount = inserted instanceof Number number ? number.longValue() : 0L;
                if (insertedCount <= 0L) {
                    return stack;
                }
                int remaining = requested - (int) Math.min(insertedCount, requested);
                return remaining <= 0 ? ItemStack.EMPTY : copyStackWithSize(stack, remaining);
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
            Object storage = storage(host);
            List<Entry> entries = itemEntries(storage);
            if (storage == null || slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            Entry entry = entries.get(slot);
            try {
                Object maxStackSize = Reflect.invoke(entry.key(), "getMaxStackSize");
                int requested = (int) Math.min(Math.min(entry.amount(), amount),
                        maxStackSize instanceof Number number ? number.intValue() : 64);
                Object extracted = Reflect.invoke(storage, "extract", entry.key(), (long) requested,
                        action(simulate), actionSource());
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? ItemStack.EMPTY
                        : (ItemStack) Reflect.invoke(entry.key(), "toStack",
                                (int) Math.min(extractedCount, requested));
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
            return fluidEntries(storage(host)).size() + 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            List<Entry> entries = fluidEntries(storage(host));
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
            Object key = fluidKey(resource);
            if (storage == null || key == null) {
                return 0;
            }
            try {
                Object inserted = Reflect.invoke(storage, "insert", key, (long) resource.getAmount(),
                        action(action), actionSource());
                return inserted instanceof Number number
                        ? (int) Math.min(number.longValue(), resource.getAmount())
                        : 0;
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
            Object storage = storage(host);
            Object key = fluidKey(resource);
            if (storage == null || key == null) {
                return FluidStack.EMPTY;
            }
            try {
                Object extracted = Reflect.invoke(storage, "extract", key, (long) resource.getAmount(),
                        action(action), actionSource());
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? FluidStack.EMPTY
                        : (FluidStack) Reflect.invoke(key, "toStack",
                                (int) Math.min(extractedCount, resource.getAmount()));
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
            Object storage = storage(host);
            List<Entry> entries = fluidEntries(storage);
            if (storage == null || entries.isEmpty()) {
                return FluidStack.EMPTY;
            }
            Entry entry = entries.get(0);
            try {
                int requested = (int) Math.min(entry.amount(), maxDrain);
                Object extracted = Reflect.invoke(storage, "extract", entry.key(), (long) requested,
                        action(action), actionSource());
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? FluidStack.EMPTY
                        : (FluidStack) Reflect.invoke(entry.key(), "toStack",
                                (int) Math.min(extractedCount, requested));
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
            Method method = method(target.getClass(), name, args);
            return method.invoke(target, args);
        }

        static Object invokeSingleVararg(Object target, String name, Object arg) throws ReflectiveOperationException {
            Method method = method(target.getClass(), name, 1);
            Class<?> parameterType = method.getParameterTypes()[0];
            if (parameterType.isArray()) {
                Object array = Array.newInstance(parameterType.getComponentType(), 1);
                Array.set(array, 0, arg);
                return method.invoke(target, array);
            }
            return method.invoke(target, arg);
        }

        static boolean tryInvoke(Object target, String name, Object... args) throws ReflectiveOperationException {
            Method method = findMethod(target.getClass(), name, args);
            if (method == null) {
                return false;
            }
            method.invoke(target, args);
            return true;
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

        static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == void.class) {
                return null;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        }

        private static Method method(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
            Method method = findMethod(type, name, args);
            if (method == null) {
                throw new NoSuchMethodException(type.getName() + "#" + name + "/" + args.length);
            }
            return method;
        }

        private static Method method(Class<?> type, String name, int argCount) throws NoSuchMethodException {
            Method method = findMethod(type, name, argCount);
            if (method == null) {
                throw new NoSuchMethodException(type.getName() + "#" + name + "/" + argCount);
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

        private static Method findMethod(Class<?> type, String name, int argCount) {
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
                Method method = findMethod(iface, name, argCount);
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
