package com.skylogistics.compat.ae2;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.SkyMEInterfaceBlockEntity;
import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.config.SkyLogisticsConfig;
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
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class AppliedEnergisticsCompat {
    private static final String AE2 = "ae2";
    private static final String APPFLUX = "appflux";
    private static final String APPMEK = "appmek";
    private static final String APPBOT = "appbot";
    private static final String ARSENG = "arseng";
    private static final String FLUX_KEY_CLASS = "com.glodblock.github.appflux.common.me.key.FluxKey";
    private static final String FLUX_ENERGY_TYPE_CLASS =
            "com.glodblock.github.appflux.common.me.key.type.EnergyType";
    private static final String MEKANISM_KEY_CLASS = "me.ramidzkh.mekae2.ae2.MekanismKey";
    private static final String MANA_KEY_CLASS = "appbot.ae2.ManaKey";
    private static final String SOURCE_KEY_CLASS = "gripe._90.arseng.me.key.SourceKey";
    private static boolean warned;

    private AppliedEnergisticsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(AE2);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        return isLoaded() && SkyLogisticsConfig.allowAe2ItemTransfer()
                ? new ItemHandler(host) : EmptyExternalHandlers.Items.INSTANCE;
    }

    public static IFluidHandler createFluidHandler(BlockEntity host) {
        return isLoaded() && SkyLogisticsConfig.allowAe2FluidTransfer()
                ? new FluidHandler(host) : EmptyExternalHandlers.Fluids.INSTANCE;
    }

    public static IEnergyStorage createEnergyHandler(BlockEntity host) {
        if (!canUseAppFlux()) {
            return EmptyExternalHandlers.Energy.INSTANCE;
        }
        try {
            return new EnergyHandler(host, appFluxEnergyKey());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Energy.INSTANCE;
        }
    }

    public static ChemicalHandlerBridge createChemicalHandler(BlockEntity host) {
        if (!canUseAppliedMekanistics()) {
            return null;
        }
        try {
            return new ChemicalHandler(host, Class.forName(MEKANISM_KEY_CLASS));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static ManaHandlerBridge createManaHandler(BlockEntity host) {
        if (!canUseAppliedBotanics()) {
            return null;
        }
        try {
            return new ManaHandler(host, singletonKey(MANA_KEY_CLASS));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static SourceHandlerBridge createSourceHandler(BlockEntity host) {
        if (!canUseArsEnergistique()) {
            return null;
        }
        try {
            return new SourceHandler(host, singletonKey(SOURCE_KEY_CLASS));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static boolean supportsEnergyEndpoint() {
        return canUseAppFlux() || canUseAppliedBotanics() || canUseArsEnergistique();
    }

    public static boolean supportsChemicalEndpoint() {
        return canUseAppliedMekanistics();
    }

    public static boolean supportsManaEndpoint() {
        return canUseAppliedBotanics();
    }

    public static boolean supportsSourceEndpoint() {
        return canUseArsEnergistique();
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
            Object capability = ae2Capability("IN_WORLD_GRID_NODE_HOST");
            event.registerBlockEntity((BlockCapability) capability, type,
                    (host, side) -> host.ae2GridNodeHost((Direction) side));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static Object actionSource(BlockEntity host) {
        if (host instanceof GridNodeOwner owner
                && owner.ae2GridNodeHandle() instanceof ManagedGridNodeHandle managedHandle) {
            Object actionSource = managedHandle.actionSource();
            if (actionSource != null) {
                return actionSource;
            }
        }
        return actionSource();
    }

    private static boolean canUseAppFlux() {
        return isLoaded() && SkyLogisticsConfig.allowAe2AppFluxEnergyTransfer()
                && ModList.get().isLoaded(APPFLUX);
    }

    private static boolean canUseAppliedMekanistics() {
        return isLoaded() && SkyLogisticsConfig.allowAe2AppliedMekanisticsChemicalTransfer()
                && SkyLogisticsConfig.allowFluidChemicalTransfer()
                && ModList.get().isLoaded(APPMEK);
    }

    private static boolean canUseAppliedBotanics() {
        return isLoaded() && SkyLogisticsConfig.allowAe2AppliedBotanicsManaTransfer()
                && SkyLogisticsConfig.allowEnergyManaTransfer()
                && ModList.get().isLoaded(APPBOT);
    }

    private static boolean canUseArsEnergistique() {
        return isLoaded() && SkyLogisticsConfig.allowAe2ArsEnergistiqueSourceTransfer()
                && SkyLogisticsConfig.allowEnergySourceTransfer()
                && ModList.get().isLoaded(ARSENG);
    }

    private static Object appFluxEnergyKey() throws ReflectiveOperationException {
        Object energyType = Reflect.staticField(FLUX_ENERGY_TYPE_CLASS, "FE");
        return Reflect.invokeStatic(Class.forName(FLUX_KEY_CLASS), "of", energyType);
    }

    private static Object singletonKey(String className) throws ReflectiveOperationException {
        return Reflect.staticField(className, "KEY");
    }

    private static long amountStored(Object storage, Object key) {
        if (storage == null || key == null) {
            return 0L;
        }
        try {
            Object stacks = Reflect.invoke(storage, "getAvailableStacks");
            if (!(stacks instanceof Iterable<?> iterable)) {
                return 0L;
            }
            for (Object entry : iterable) {
                Object entryKey = Reflect.invoke(entry, "getKey");
                if (!key.equals(entryKey)) {
                    continue;
                }
                Object amount = Reflect.invoke(entry, "getLongValue");
                return amount instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
        }
        return 0L;
    }

    private static long insertKey(BlockEntity host, Object storage, Object key, long amount, boolean simulate) {
        if (storage == null || key == null || amount <= 0L) {
            return 0L;
        }
        try {
            Object inserted = Reflect.invoke(storage, "insert", key, amount, action(simulate), actionSource(host));
            return inserted instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    private static long extractKey(BlockEntity host, Object storage, Object key, long amount, boolean simulate) {
        if (storage == null || key == null || amount <= 0L) {
            return 0L;
        }
        try {
            Object extracted = Reflect.invoke(storage, "extract", key, amount, action(simulate), actionSource(host));
            return extracted instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    private static int clampInt(long amount) {
        return amount <= 0L ? 0 : (int) Math.min(amount, Integer.MAX_VALUE);
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return List.of();
        }
    }

    private static List<Entry> entries(Object storage, Class<?> keyClass) {
        if (storage == null) {
            return List.of();
        }
        try {
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return ItemStack.EMPTY;
        }
    }

    private static FluidStack fluidStack(Entry entry) {
        try {
            int amount = (int) Math.min(entry.amount(), Integer.MAX_VALUE);
            Object stack = Reflect.invoke(entry.key(), "toStack", amount);
            return stack instanceof FluidStack fluidStack ? fluidStack : FluidStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return FluidStack.EMPTY;
        }
    }

    private static ChemicalStackView chemicalStack(Entry entry) {
        return entry.amount() <= 0L ? EmptyChemicalStackView.INSTANCE
                : new AeChemicalStackView(entry.key(), entry.amount());
    }

    private static Object chemicalKey(ChemicalStackView stack, Class<?> keyClass) {
        if (stack instanceof AeChemicalStackView view && keyClass.isInstance(view.key)) {
            return view.key;
        }
        return chemicalKey(stack.rawStack(), keyClass);
    }

    private static Object chemicalKey(Object rawStack, Class<?> keyClass) {
        if (rawStack == null) {
            return null;
        }
        try {
            return Reflect.invokeStatic(keyClass, "of", rawStack);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static Object chemicalStackWithAmount(Object key, long amount) {
        try {
            return Reflect.invoke(key, "withAmount", amount);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            try {
                Object stack = Reflect.invoke(key, "getStack");
                Object copy = Reflect.invoke(stack, "copy");
                if (!Reflect.tryInvoke(copy, "setAmount", amount)) {
                    return null;
                }
                return copy;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return null;
            }
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static Object fluidKey(FluidStack stack) {
        try {
            return Reflect.invokeStatic(Class.forName("appeng.api.stacks.AEFluidKey"), "of", stack);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static Object ae2Capability(String fieldName) throws ReflectiveOperationException {
        ReflectiveOperationException lastError = null;
        for (String className : List.of("appeng.api.AECapabilities", "appeng.capabilities.Capabilities")) {
            try {
                return Class.forName(className).getField(fieldName).get(null);
            } catch (ClassNotFoundException | NoSuchFieldException error) {
                lastError = error;
            }
        }
        throw lastError == null ? new ClassNotFoundException(fieldName) : lastError;
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
        private final Object actionSource;
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
            this.actionSource = createActionSource(exposedHost);
        }

        @Override
        public void load(CompoundTag tag, HolderLookup.Provider registries) {
            try {
                if (!Reflect.tryInvoke(node, "loadFromNBT", tag, registries)) {
                    Reflect.tryInvoke(node, "loadFromNBT", tag);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
            }
        }

        @Override
        public void save(CompoundTag tag, HolderLookup.Provider registries) {
            try {
                if (!Reflect.tryInvoke(node, "saveToNBT", tag, registries)) {
                    Reflect.tryInvoke(node, "saveToNBT", tag);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                        warn(error);
                    }
                });
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
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

        private Object createActionSource(Object host) {
            try {
                return Reflect.invokeStatic(Class.forName("appeng.api.networking.security.IActionSource"),
                        "ofMachine", host);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return AppliedEnergisticsCompat.actionSource();
            }
        }

        private Object actionSource() {
            return actionSource;
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
                Object inserted = Reflect.invoke(storage, "insert", key, (long) requested, action(simulate),
                        actionSource(host));
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
                        action(simulate), actionSource(host));
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? ItemStack.EMPTY
                        : (ItemStack) Reflect.invoke(entry.key(), "toStack",
                                (int) Math.min(extractedCount, requested));
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
                        action(action), actionSource(host));
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
            Object key = fluidKey(resource);
            if (storage == null || key == null) {
                return FluidStack.EMPTY;
            }
            try {
                Object extracted = Reflect.invoke(storage, "extract", key, (long) resource.getAmount(),
                        action(action), actionSource(host));
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? FluidStack.EMPTY
                        : (FluidStack) Reflect.invoke(key, "toStack",
                                (int) Math.min(extractedCount, resource.getAmount()));
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
            List<Entry> entries = fluidEntries(storage);
            if (storage == null || entries.isEmpty()) {
                return FluidStack.EMPTY;
            }
            Entry entry = entries.get(0);
            try {
                int requested = (int) Math.min(entry.amount(), maxDrain);
                Object extracted = Reflect.invoke(storage, "extract", entry.key(), (long) requested,
                        action(action), actionSource(host));
                long extractedCount = extracted instanceof Number number ? number.longValue() : 0L;
                return extractedCount <= 0L ? FluidStack.EMPTY
                        : (FluidStack) Reflect.invoke(entry.key(), "toStack",
                                (int) Math.min(extractedCount, requested));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return FluidStack.EMPTY;
            }
        }
    }

    private record EnergyHandler(BlockEntity host, Object key) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return clampInt(insertKey(host, storage(host), key, maxReceive, simulate));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return clampInt(extractKey(host, storage(host), key, maxExtract, simulate));
        }

        @Override
        public int getEnergyStored() {
            return clampInt(amountStored(storage(host), key));
        }

        @Override
        public int getMaxEnergyStored() {
            return canReceive() || getEnergyStored() > 0 ? Integer.MAX_VALUE : 0;
        }

        @Override
        public boolean canExtract() {
            Object storage = storage(host);
            return amountStored(storage, key) > 0L || extractKey(host, storage, key, 1L, true) > 0L;
        }

        @Override
        public boolean canReceive() {
            return insertKey(host, storage(host), key, 1L, true) > 0L;
        }
    }

    private record ChemicalHandler(BlockEntity host, Class<?> keyClass) implements ChemicalHandlerBridge {
        @Override
        public int getTanks() {
            return entries(storage(host), keyClass).size() + 1;
        }

        @Override
        public ChemicalStackView getChemicalInTank(int tank) {
            List<Entry> entries = entries(storage(host), keyClass);
            return tank >= 0 && tank < entries.size()
                    ? chemicalStack(entries.get(tank))
                    : EmptyChemicalStackView.INSTANCE;
        }

        @Override
        public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
            if (amount <= 0L) {
                return EmptyChemicalStackView.INSTANCE;
            }
            Object storage = storage(host);
            List<Entry> entries = entries(storage, keyClass);
            if (tank < 0 || tank >= entries.size()) {
                return EmptyChemicalStackView.INSTANCE;
            }
            Entry entry = entries.get(tank);
            long requested = Math.min(entry.amount(), amount);
            long extracted = extractKey(host, storage, entry.key(), requested, simulate);
            return extracted <= 0L ? EmptyChemicalStackView.INSTANCE
                    : new AeChemicalStackView(entry.key(), extracted);
        }

        @Override
        public long insertChemical(ChemicalStackView stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return 0L;
            }
            Object storage = storage(host);
            Object key = chemicalKey(stack, keyClass);
            long requested = stack.getAmount();
            long inserted = insertKey(host, storage, key, requested, simulate);
            return Math.min(inserted, requested);
        }
    }

    private record ManaHandler(BlockEntity host, Object key) implements ManaHandlerBridge {
        @Override
        public int getCurrentMana() {
            return clampInt(amountStored(storage(host), key));
        }

        @Override
        public int getMaxMana() {
            return canReceive() || getCurrentMana() > 0 ? Integer.MAX_VALUE : 0;
        }

        @Override
        public boolean canExtract() {
            Object storage = storage(host);
            return amountStored(storage, key) > 0L || extractKey(host, storage, key, 1L, true) > 0L;
        }

        @Override
        public boolean canReceive() {
            return insertKey(host, storage(host), key, 1L, true) > 0L;
        }

        @Override
        public int extractMana(int amount, boolean simulate) {
            return clampInt(extractKey(host, storage(host), key, amount, simulate));
        }

        @Override
        public int insertMana(int amount, boolean simulate) {
            return clampInt(insertKey(host, storage(host), key, amount, simulate));
        }
    }

    private record SourceHandler(BlockEntity host, Object key) implements SourceHandlerBridge {
        @Override
        public int getCurrentSource() {
            return clampInt(amountStored(storage(host), key));
        }

        @Override
        public int getMaxSource() {
            return canReceive() || getCurrentSource() > 0 ? Integer.MAX_VALUE : 0;
        }

        @Override
        public boolean canExtract() {
            Object storage = storage(host);
            return amountStored(storage, key) > 0L || extractKey(host, storage, key, 1L, true) > 0L;
        }

        @Override
        public boolean canReceive() {
            return insertKey(host, storage(host), key, 1L, true) > 0L;
        }

        @Override
        public int extractSource(int amount, boolean simulate) {
            return clampInt(extractKey(host, storage(host), key, amount, simulate));
        }

        @Override
        public int insertSource(int amount, boolean simulate) {
            return clampInt(insertKey(host, storage(host), key, amount, simulate));
        }
    }

    private record AeChemicalStackView(Object key, long amount) implements ChemicalStackView {
        @Override
        public boolean isEmpty() {
            return key == null || amount <= 0L;
        }

        @Override
        public long getAmount() {
            return Math.max(0L, amount);
        }

        @Override
        public ChemicalStackView copyWithAmount(long amount) {
            return amount <= 0L ? EmptyChemicalStackView.INSTANCE : new AeChemicalStackView(key, amount);
        }

        @Override
        public boolean isSameChemical(ChemicalStackView other) {
            if (other == null) {
                return false;
            }
            if (other instanceof AeChemicalStackView view) {
                return key.equals(view.key);
            }
            Object otherKey = chemicalKey(other.rawStack(), key.getClass());
            return otherKey != null && key.equals(otherKey);
        }

        @Override
        public Object rawStack() {
            return chemicalStackWithAmount(key, amount);
        }

        @Override
        public String toString() {
            return key + " x " + amount;
        }
    }

    private enum EmptyChemicalStackView implements ChemicalStackView {
        INSTANCE;

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public long getAmount() {
            return 0L;
        }

        @Override
        public ChemicalStackView copyWithAmount(long amount) {
            return this;
        }

        @Override
        public boolean isSameChemical(ChemicalStackView other) {
            return other != null && other.isEmpty();
        }
    }

    private static final class Reflect {
        private Reflect() {
        }

        static Object staticField(String className, String name) throws ReflectiveOperationException {
            return Class.forName(className).getField(name).get(null);
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
                for (Class<?> iface : current.getInterfaces()) {
                    Method method = findMethod(iface, name, args);
                    if (method != null) {
                        return method;
                    }
                }
                current = current.getSuperclass();
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
                for (Class<?> iface : current.getInterfaces()) {
                    Method method = findMethod(iface, name, argCount);
                    if (method != null) {
                        return method;
                    }
                }
                current = current.getSuperclass();
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
