package com.skylogistics.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridNodeListener.State;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.capabilities.Capabilities;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.registry.ModBlocks;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

final class AppliedEnergisticsApiBridge {
    private static final String FLUX_KEY_CLASS = "com.glodblock.github.appflux.common.me.key.FluxKey";
    private static final String FLUX_ENERGY_TYPE_CLASS =
            "com.glodblock.github.appflux.common.me.key.type.EnergyType";
    private static final String MEKANISM_KEY_CLASS = "me.ramidzkh.mekae2.ae2.MekanismKey";
    private static final String MANA_KEY_CLASS = "appbot.ae2.ManaKey";
    private static final String SOURCE_KEY_CLASS = "gripe._90.arseng.me.key.SourceKey";
    private static final IActionSource EMPTY_ACTION_SOURCE = IActionSource.empty();
    private static final IGridNodeListener<BlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(BlockEntity nodeOwner, IGridNode node) {
            nodeOwner.setChanged();
        }

        @Override
        public void onInWorldConnectionChanged(BlockEntity nodeOwner, IGridNode node) {
            Level level = nodeOwner.getLevel();
            if (level != null) {
                BlockState state = nodeOwner.getBlockState();
                level.sendBlockUpdated(nodeOwner.getBlockPos(), state, state, 3);
            }
        }

        @Override
        public void onStateChanged(BlockEntity nodeOwner, IGridNode node, State state) {
            nodeOwner.setChanged();
        }
    };

    private AppliedEnergisticsApiBridge() {
    }

    static AppliedEnergisticsCompat.GridNodeHandle createGridNodeHandle(BlockEntity host) {
        return new ManagedGridNodeHandle(host);
    }

    static IItemHandler createItemHandler(BlockEntity host) {
        return new ItemHandler(host);
    }

    static IFluidHandler createFluidHandler(BlockEntity host) {
        return new FluidHandler(host);
    }

    static IEnergyStorage createEnergyHandler(BlockEntity host) {
        return new EnergyHandler(host, appFluxEnergyKey());
    }

    static ChemicalHandlerBridge createChemicalHandler(BlockEntity host) {
        return new ChemicalHandler(host, keyClass(MEKANISM_KEY_CLASS));
    }

    static ManaHandlerBridge createManaHandler(BlockEntity host) {
        return new ManaHandler(host, singletonKey(MANA_KEY_CLASS));
    }

    static SourceHandlerBridge createSourceHandler(BlockEntity host) {
        return new SourceHandler(host, singletonKey(SOURCE_KEY_CLASS));
    }

    private static MEStorage storage(BlockEntity host) {
        IGrid grid = grid(host);
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    private static IGrid grid(BlockEntity host) {
        if (host instanceof AppliedEnergisticsCompat.GridNodeOwner owner) {
            return grid(owner.ae2GridNodeHandle());
        }
        return adjacentGrid(host);
    }

    private static IGrid grid(AppliedEnergisticsCompat.GridNodeHandle handle) {
        if (handle instanceof ManagedGridNodeHandle managedHandle) {
            return managedHandle.grid();
        }
        return null;
    }

    private static IGrid adjacentGrid(BlockEntity host) {
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        BlockPos pos = host.getBlockPos();
        for (Direction direction : Direction.values()) {
            BlockPos target = pos.relative(direction);
            if (!level.isLoaded(target)) {
                continue;
            }
            IGridNode node = GridHelper.getExposedNode(level, target, direction.getOpposite());
            if (node != null && node.isOnline()) {
                return node.getGrid();
            }
        }
        return null;
    }

    private static IActionSource actionSource(BlockEntity host) {
        if (host instanceof AppliedEnergisticsCompat.GridNodeOwner owner
                && owner.ae2GridNodeHandle() instanceof ManagedGridNodeHandle managedHandle) {
            return managedHandle.actionSource();
        }
        return EMPTY_ACTION_SOURCE;
    }

    private static final class ManagedGridNodeHandle
            implements AppliedEnergisticsCompat.GridNodeHandle, IInWorldGridNodeHost, IActionHost {
        private final IManagedGridNode node;
        private final IActionSource actionSource;
        private LazyOptional<IInWorldGridNodeHost> exposedHost = LazyOptional.of(() -> this);
        private boolean removed;

        private ManagedGridNodeHandle(BlockEntity host) {
            this.node = GridHelper.createManagedNode(host, NODE_LISTENER)
                    .setVisualRepresentation(ModBlocks.SKY_ME_INTERFACE.get())
                    .setInWorldNode(true)
                    .setExposedOnSides(EnumSet.allOf(Direction.class))
                    .setFlags(GridFlags.REQUIRE_CHANNEL)
                    .setTagName("ae2_node");
            this.actionSource = IActionSource.ofMachine(this);
        }

        @Override
        public void load(CompoundTag tag) {
            node.loadFromNBT(tag);
        }

        @Override
        public void save(CompoundTag tag) {
            node.saveToNBT(tag);
        }

        @Override
        public void onLoad(BlockEntity host) {
            if (host.getLevel() == null || host.getLevel().isClientSide) {
                return;
            }
            GridHelper.onFirstTick(host, loadedHost -> {
                if (!removed && !loadedHost.isRemoved() && !node.isReady()) {
                    node.create(loadedHost.getLevel(), loadedHost.getBlockPos());
                }
            });
        }

        @Override
        public void onRemove() {
            if (!removed) {
                removed = true;
                node.destroy();
            }
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            if (side == null && capability == Capabilities.IN_WORLD_GRID_NODE_HOST) {
                return exposedHost.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public void invalidateCaps() {
            exposedHost.invalidate();
        }

        @Override
        public IGridNode getGridNode(Direction dir) {
            return node.getNode();
        }

        @Override
        public AECableType getCableConnectionType(Direction dir) {
            return AECableType.SMART;
        }

        @Override
        public IGridNode getActionableNode() {
            return node.getNode();
        }

        private IActionSource actionSource() {
            return actionSource;
        }

        private IGrid grid() {
            IGridNode gridNode = node.getNode();
            return gridNode != null && gridNode.isOnline() ? gridNode.getGrid() : null;
        }
    }

    private static Actionable action(boolean simulate) {
        return simulate ? Actionable.SIMULATE : Actionable.MODULATE;
    }

    private static Actionable action(IFluidHandler.FluidAction action) {
        return action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE;
    }

    private static AEKey appFluxEnergyKey() {
        try {
            Object energyType = Reflect.staticField(FLUX_ENERGY_TYPE_CLASS, "FE");
            return aeKey(Reflect.invokeStatic(Class.forName(FLUX_KEY_CLASS), "of", energyType));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            throw new IllegalStateException("Unable to resolve AppFlux FE key", error);
        }
    }

    private static AEKey singletonKey(String className) {
        try {
            return aeKey(Reflect.staticField(className, "KEY"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            throw new IllegalStateException("Unable to resolve AE2 addon key " + className, error);
        }
    }

    private static Class<?> keyClass(String className) {
        try {
            return Class.forName(className);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            throw new IllegalStateException("Unable to resolve AE2 addon key class " + className, error);
        }
    }

    private static AEKey aeKey(Object key) {
        if (key instanceof AEKey aeKey) {
            return aeKey;
        }
        throw new IllegalStateException("Resolved object is not an AE key: " + key);
    }

    private static long amountStored(MEStorage storage, AEKey key) {
        if (storage == null || key == null) {
            return 0L;
        }
        for (Object2LongMap.Entry<AEKey> entry : storage.getAvailableStacks()) {
            if (key.equals(entry.getKey())) {
                return Math.max(0L, entry.getLongValue());
            }
        }
        return 0L;
    }

    private static long insertKey(BlockEntity host, MEStorage storage, AEKey key, long amount, boolean simulate) {
        if (storage == null || key == null || amount <= 0L) {
            return 0L;
        }
        return storage.insert(key, amount, action(simulate), actionSource(host));
    }

    private static long extractKey(BlockEntity host, MEStorage storage, AEKey key, long amount, boolean simulate) {
        if (storage == null || key == null || amount <= 0L) {
            return 0L;
        }
        return storage.extract(key, amount, action(simulate), actionSource(host));
    }

    private static int clampInt(long amount) {
        return amount <= 0L ? 0 : (int) Math.min(amount, Integer.MAX_VALUE);
    }

    private static List<ItemEntry> itemEntries(MEStorage storage) {
        if (storage == null) {
            return List.of();
        }
        List<ItemEntry> entries = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey key && entry.getLongValue() > 0L) {
                entries.add(new ItemEntry(key, entry.getLongValue()));
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.key().toString()));
        return entries;
    }

    private static List<FluidEntry> fluidEntries(MEStorage storage) {
        if (storage == null) {
            return List.of();
        }
        List<FluidEntry> entries = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : storage.getAvailableStacks()) {
            if (entry.getKey() instanceof AEFluidKey key && entry.getLongValue() > 0L) {
                entries.add(new FluidEntry(key, entry.getLongValue()));
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.key().toString()));
        return entries;
    }

    private static List<ChemicalEntry> chemicalEntries(MEStorage storage, Class<?> keyClass) {
        if (storage == null) {
            return List.of();
        }
        List<ChemicalEntry> entries = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : storage.getAvailableStacks()) {
            AEKey key = entry.getKey();
            if (keyClass.isInstance(key) && entry.getLongValue() > 0L) {
                entries.add(new ChemicalEntry(key, entry.getLongValue()));
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.key().toString()));
        return entries;
    }

    private static ItemStack itemStack(ItemEntry entry) {
        int amount = (int) Math.min(entry.amount(), entry.key().getMaxStackSize());
        return entry.key().toStack(amount);
    }

    private static FluidStack fluidStack(FluidEntry entry) {
        int amount = (int) Math.min(entry.amount(), Integer.MAX_VALUE);
        return entry.key().toStack(amount);
    }

    private static ChemicalStackView chemicalStack(ChemicalEntry entry) {
        return entry.amount() <= 0L ? EmptyChemicalStackView.INSTANCE
                : new AeChemicalStackView(entry.key(), entry.amount());
    }

    private static AEKey chemicalKey(ChemicalStackView stack, Class<?> keyClass) {
        if (stack instanceof AeChemicalStackView view && keyClass.isInstance(view.key)) {
            return view.key;
        }
        return chemicalKey(stack.rawStack(), keyClass);
    }

    private static AEKey chemicalKey(Object rawStack, Class<?> keyClass) {
        if (rawStack == null) {
            return null;
        }
        try {
            Object key = Reflect.invokeStatic(keyClass, "of", rawStack);
            return key instanceof AEKey aeKey ? aeKey : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static Object chemicalStackWithAmount(AEKey key, long amount) {
        try {
            return Reflect.invoke(key, "withAmount", amount);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            try {
                Object stack = Reflect.invoke(key, "getStack");
                Object copy = Reflect.invoke(stack, "copy");
                Reflect.invoke(copy, "setAmount", amount);
                return copy;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                return null;
            }
        }
    }

    private record ItemEntry(AEItemKey key, long amount) {
    }

    private record FluidEntry(AEFluidKey key, long amount) {
    }

    private record ChemicalEntry(AEKey key, long amount) {
    }

    private record ItemHandler(BlockEntity host) implements IItemHandler {
        @Override
        public int getSlots() {
            return itemEntries(storage(host)).size() + 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            List<ItemEntry> entries = itemEntries(storage(host));
            return slot >= 0 && slot < entries.size() ? itemStack(entries.get(slot)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            MEStorage storage = storage(host);
            AEItemKey key = AEItemKey.of(stack);
            if (storage == null || key == null) {
                return stack;
            }
            int requested = stack.getCount();
            long inserted = storage.insert(key, requested, action(simulate), actionSource(host));
            if (inserted <= 0L) {
                return stack;
            }
            int remaining = requested - (int) Math.min(inserted, requested);
            return remaining <= 0 ? ItemStack.EMPTY : ItemHandlerHelper.copyStackWithSize(stack, remaining);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            MEStorage storage = storage(host);
            List<ItemEntry> entries = itemEntries(storage);
            if (slot < 0 || slot >= entries.size()) {
                return ItemStack.EMPTY;
            }
            ItemEntry entry = entries.get(slot);
            int requested = (int) Math.min(Math.min(entry.amount(), amount), entry.key().getMaxStackSize());
            long extracted = storage.extract(entry.key(), requested, action(simulate), actionSource(host));
            return extracted <= 0L ? ItemStack.EMPTY : entry.key().toStack((int) Math.min(extracted, requested));
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
            List<FluidEntry> entries = fluidEntries(storage(host));
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
            MEStorage storage = storage(host);
            AEFluidKey key = AEFluidKey.of(resource);
            if (storage == null || key == null) {
                return 0;
            }
            long inserted = storage.insert(key, resource.getAmount(), action(action), actionSource(host));
            return (int) Math.min(inserted, resource.getAmount());
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            MEStorage storage = storage(host);
            AEFluidKey key = AEFluidKey.of(resource);
            if (storage == null || key == null) {
                return FluidStack.EMPTY;
            }
            long extracted = storage.extract(key, resource.getAmount(), action(action), actionSource(host));
            return extracted <= 0L ? FluidStack.EMPTY : key.toStack((int) Math.min(extracted, resource.getAmount()));
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            MEStorage storage = storage(host);
            List<FluidEntry> entries = fluidEntries(storage);
            if (entries.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidEntry entry = entries.get(0);
            int requested = (int) Math.min(entry.amount(), maxDrain);
            long extracted = storage.extract(entry.key(), requested, action(action), actionSource(host));
            return extracted <= 0L ? FluidStack.EMPTY : entry.key().toStack((int) Math.min(extracted, requested));
        }
    }

    private record EnergyHandler(BlockEntity host, AEKey key) implements IEnergyStorage {
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
            MEStorage storage = storage(host);
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
            return chemicalEntries(storage(host), keyClass).size() + 1;
        }

        @Override
        public ChemicalStackView getChemicalInTank(int tank) {
            List<ChemicalEntry> entries = chemicalEntries(storage(host), keyClass);
            return tank >= 0 && tank < entries.size()
                    ? chemicalStack(entries.get(tank))
                    : EmptyChemicalStackView.INSTANCE;
        }

        @Override
        public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
            if (amount <= 0L) {
                return EmptyChemicalStackView.INSTANCE;
            }
            MEStorage storage = storage(host);
            List<ChemicalEntry> entries = chemicalEntries(storage, keyClass);
            if (tank < 0 || tank >= entries.size()) {
                return EmptyChemicalStackView.INSTANCE;
            }
            ChemicalEntry entry = entries.get(tank);
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
            MEStorage storage = storage(host);
            AEKey key = chemicalKey(stack, keyClass);
            long requested = stack.getAmount();
            long inserted = insertKey(host, storage, key, requested, simulate);
            return Math.min(inserted, requested);
        }
    }

    private record ManaHandler(BlockEntity host, AEKey key) implements ManaHandlerBridge {
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
            MEStorage storage = storage(host);
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

    private record SourceHandler(BlockEntity host, AEKey key) implements SourceHandlerBridge {
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
            MEStorage storage = storage(host);
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

    private record AeChemicalStackView(AEKey key, long amount) implements ChemicalStackView {
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
            AEKey otherKey = chemicalKey(other.rawStack(), key.getClass());
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

        static Object invokeStatic(Class<?> owner, String name, Object... args) throws ReflectiveOperationException {
            Method method = method(owner, name, args);
            return method.invoke(null, args);
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
