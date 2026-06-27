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
import com.skylogistics.registry.ModBlocks;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

final class AppliedEnergisticsApiBridge {
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

    private static ItemStack itemStack(ItemEntry entry) {
        int amount = (int) Math.min(entry.amount(), entry.key().getMaxStackSize());
        return entry.key().toStack(amount);
    }

    private static FluidStack fluidStack(FluidEntry entry) {
        int amount = (int) Math.min(entry.amount(), Integer.MAX_VALUE);
        return entry.key().toStack(amount);
    }

    private record ItemEntry(AEItemKey key, long amount) {
    }

    private record FluidEntry(AEFluidKey key, long amount) {
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
}
