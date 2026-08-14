package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** A zero-buffer, bounded capability fan-out over a cached cluster of adjacent containers. */
public class SkyDistributorBlockEntity extends BlockEntity {
    private static final int MAX_CONFIGURABLE_TARGETS = 64;
    private static final long RESCAN_INTERVAL = 100L;
    private final DistributedItems items = new DistributedItems();
    private final DistributedFluids fluids = new DistributedFluids();
    private final DistributedEnergy energy = new DistributedEnergy();
    private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> items);
    private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> fluids);
    private final LazyOptional<IEnergyStorage> energyCapability = LazyOptional.of(() -> energy);
    private TargetCache targetCache = TargetCache.EMPTY;
    private boolean targetsDirty = true;
    private long nextRescan;
    private int itemInsertCursor;
    private int fluidInsertCursor;
    private int energyInsertCursor;
    private final int[] itemExtractCursors = new int[MAX_CONFIGURABLE_TARGETS];
    private final int[] fluidExtractCursors = new int[MAX_CONFIGURABLE_TARGETS];

    public SkyDistributorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_DISTRIBUTOR.get(), pos, state);
    }

    public void invalidateTargets() {
        targetsDirty = true;
    }

    public void refreshTargets() {
        if (level == null) return;
        targetCache = discoverTargets();
        targetsDirty = false;
        nextRescan = level.getGameTime() + RESCAN_INTERVAL;
    }

    private TargetCache targets() {
        if (level == null) return TargetCache.EMPTY;
        long now = level.getGameTime();
        if (targetsDirty || now >= nextRescan) {
            refreshTargets();
        }
        return targetCache;
    }

    private TargetCache discoverTargets() {
        int maxTargets = SkyLogisticsConfig.distributorMaxTargets();
        List<Target> itemTargets = new ArrayList<>(maxTargets);
        List<Target> fluidTargets = new ArrayList<>(maxTargets);
        List<Target> energyTargets = new ArrayList<>(maxTargets);
        int found = 0;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        visited.add(worldPosition);
        for (Direction direction : Direction.values()) queue.add(worldPosition.relative(direction));
        while (!queue.isEmpty() && found < maxTargets) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos) || !level.hasChunkAt(pos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || blockEntity instanceof SkyDistributorBlockEntity) continue;
            Target target = inspect(pos, blockEntity);
            if (!target.usable()) continue;
            found++;
            if (target.items) itemTargets.add(target);
            if (target.fluids) fluidTargets.add(target);
            if (target.energy) energyTargets.add(target);
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction));
        }
        return new TargetCache(List.copyOf(itemTargets), List.copyOf(fluidTargets), List.copyOf(energyTargets));
    }

    private Target inspect(BlockPos pos, BlockEntity blockEntity) {
        Direction itemSide = firstItemSide(blockEntity);
        Direction fluidSide = firstFluidSide(blockEntity);
        Direction energySide = firstEnergySide(blockEntity);
        boolean unsidedItems = itemSide == null && blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                .map(handler -> handler.getSlots() > 0).orElse(false);
        boolean unsidedFluids = fluidSide == null && blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null)
                .map(handler -> handler.getTanks() > 0).orElse(false);
        boolean unsidedEnergy = energySide == null && blockEntity.getCapability(ForgeCapabilities.ENERGY, null)
                .map(SkyDistributorBlockEntity::usableEnergy).orElse(false);
        return new Target(pos.immutable(), itemSide, fluidSide, energySide,
                itemSide != null || unsidedItems, fluidSide != null || unsidedFluids, energySide != null || unsidedEnergy);
    }

    private static Direction firstItemSide(BlockEntity blockEntity) {
        for (Direction direction : Direction.values()) {
            if (blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction)
                    .map(handler -> handler.getSlots() > 0).orElse(false)) return direction;
        }
        return null;
    }
    private static Direction firstFluidSide(BlockEntity blockEntity) {
        for (Direction direction : Direction.values()) {
            if (blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction)
                    .map(handler -> handler.getTanks() > 0).orElse(false)) return direction;
        }
        return null;
    }
    private static Direction firstEnergySide(BlockEntity blockEntity) {
        for (Direction direction : Direction.values()) {
            if (blockEntity.getCapability(ForgeCapabilities.ENERGY, direction)
                    .map(SkyDistributorBlockEntity::usableEnergy).orElse(false)) return direction;
        }
        return null;
    }
    private static boolean usableEnergy(IEnergyStorage storage) {
        return storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive();
    }

    private IItemHandler item(Target target) {
        if (!target.items) return null;
        BlockEntity blockEntity = level.getBlockEntity(target.pos);
        return blockEntity == null ? null : blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, target.itemSide)
                .orElse(null);
    }

    private IFluidHandler fluid(Target target) {
        if (!target.fluids) return null;
        BlockEntity blockEntity = level.getBlockEntity(target.pos);
        return blockEntity == null ? null : blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, target.fluidSide)
                .orElse(null);
    }

    private IEnergyStorage energy(Target target) {
        if (!target.energy) return null;
        BlockEntity blockEntity = level.getBlockEntity(target.pos);
        return blockEntity == null ? null : blockEntity.getCapability(ForgeCapabilities.ENERGY, target.energySide)
                .orElse(null);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && SkyLogisticsConfig.enableDistributorItems())
            return itemCapability.cast();
        if (capability == ForgeCapabilities.FLUID_HANDLER && SkyLogisticsConfig.enableDistributorFluids())
            return fluidCapability.cast();
        if (capability == ForgeCapabilities.ENERGY && SkyLogisticsConfig.enableDistributorEnergy())
            return energyCapability.cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        fluidCapability.invalidate();
        energyCapability.invalidate();
    }

    private record Target(BlockPos pos, Direction itemSide, Direction fluidSide, Direction energySide,
            boolean items, boolean fluids, boolean energy) {
        boolean usable() { return items || fluids || energy; }
    }

    private record TargetCache(List<Target> items, List<Target> fluids, List<Target> energy) {
        private static final TargetCache EMPTY = new TargetCache(List.of(), List.of(), List.of());
    }

    private final class DistributedItems implements IItemHandler {
        @Override public int getSlots() {
            if (!SkyLogisticsConfig.enableDistributorItems()) return 0;
            return targets().items.size();
        }
        @Override public ItemStack getStackInSlot(int slot) {
            IItemHandler handler = handler(slot);
            if (handler == null || handler.getSlots() == 0) return ItemStack.EMPTY;
            int start = Math.floorMod(itemExtractCursors[slot], handler.getSlots());
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot((start + i) % handler.getSlots());
                if (!stack.isEmpty()) return stack;
            }
            return ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int ignored, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            List<Target> all = targets().items;
            ItemStack remaining = stack.copy();
            if (all.isEmpty()) return remaining;
            int start = Math.floorMod(itemInsertCursor, all.size());
            int viable = 0;
            for (Target target : all) if (canAccept(item(target), stack)) viable++;
            for (int i = 0, left = viable; i < all.size() && !remaining.isEmpty() && left > 0; i++) {
                IItemHandler handler = item(all.get((start + i) % all.size()));
                if (!canAccept(handler, remaining)) continue;
                int share = (remaining.getCount() + left - 1) / left--;
                ItemStack offer = remaining.copy(); offer.setCount(Math.min(share, remaining.getCount()));
                ItemStack rejected = insertStacked(handler, offer, simulate);
                remaining.shrink(offer.getCount() - rejected.getCount());
            }
            if (!simulate && remaining.getCount() != stack.getCount() && !all.isEmpty())
                itemInsertCursor = (start + 1) % all.size();
            return remaining;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler handler = handler(slot);
            if (handler == null || amount <= 0 || handler.getSlots() == 0) return ItemStack.EMPTY;
            int start = Math.floorMod(itemExtractCursors[slot], handler.getSlots());
            for (int i = 0; i < handler.getSlots(); i++) {
                int sourceSlot = (start + i) % handler.getSlots();
                ItemStack extracted = handler.extractItem(sourceSlot, amount, simulate);
                if (!extracted.isEmpty()) {
                    if (!simulate) itemExtractCursors[slot] = sourceSlot + 1;
                    return extracted;
                }
            }
            return ItemStack.EMPTY;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
        private IItemHandler handler(int slot) {
            List<Target> all = targets().items;
            return slot < 0 || slot >= all.size() ? null : item(all.get(slot));
        }
        private boolean canAccept(IItemHandler handler, ItemStack stack) {
            return handler != null && insertStacked(handler, stack.copy(), true).getCount() < stack.getCount();
        }
        private ItemStack insertStacked(IItemHandler handler, ItemStack stack, boolean simulate) {
            ItemStack remaining = stack;
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                    boolean empty = handler.getStackInSlot(slot).isEmpty();
                    if ((pass == 0) != !empty) continue;
                    remaining = handler.insertItem(slot, remaining, simulate);
                }
            }
            return remaining;
        }
    }

    private final class DistributedFluids implements IFluidHandler {
        @Override public int getTanks() {
            if (!SkyLogisticsConfig.enableDistributorFluids()) return 0;
            return targets().fluids.size();
        }
        @Override public FluidStack getFluidInTank(int tank) {
            IFluidHandler handler = handler(tank);
            if (handler == null || handler.getTanks() == 0) return FluidStack.EMPTY;
            int start = Math.floorMod(fluidExtractCursors[tank], handler.getTanks());
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack stack = handler.getFluidInTank((start + i) % handler.getTanks());
                if (!stack.isEmpty()) return stack;
            }
            return FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { IFluidHandler h = handler(tank); return h == null ? 0 : Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            List<Target> all = targets().fluids; int remaining = resource.getAmount();
            if (all.isEmpty()) return 0;
            int start = Math.floorMod(fluidInsertCursor, all.size()); int viable = 0;
            for (Target target : all) { IFluidHandler h = fluid(target); if (h != null && h.fill(resource, FluidAction.SIMULATE) > 0) viable++; }
            for (int i = 0, left = viable; i < all.size() && remaining > 0 && left > 0; i++) {
                IFluidHandler h = fluid(all.get((start + i) % all.size()));
                if (h == null || h.fill(resource, FluidAction.SIMULATE) <= 0) continue;
                int share = (remaining + left - 1) / left--; FluidStack offer = resource.copy(); offer.setAmount(Math.min(share, remaining));
                remaining -= h.fill(offer, action);
            }
            if (action.execute() && remaining != resource.getAmount() && !all.isEmpty()) fluidInsertCursor = (start + 1) % all.size();
            return resource.getAmount() - remaining;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            FluidStack result = FluidStack.EMPTY;
            for (int i = 0; i < getTanks() && result.getAmount() < resource.getAmount(); i++) {
                IFluidHandler h = handler(i); if (h == null) continue;
                FluidStack request = resource.copy(); request.setAmount(resource.getAmount() - result.getAmount());
                FluidStack part = h.drain(request, action);
                if (!part.isEmpty()) { if (result.isEmpty()) result = part.copy(); else result.grow(part.getAmount()); if (action.execute()) fluidExtractCursors[i]++; }
            }
            return result;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < getTanks(); i++) { FluidStack visible = getFluidInTank(i); if (!visible.isEmpty()) { FluidStack request = visible.copy(); request.setAmount(maxDrain); return drain(request, action); } }
            return FluidStack.EMPTY;
        }
        private IFluidHandler handler(int tank) { List<Target> all = targets().fluids; return tank < 0 || tank >= all.size() ? null : fluid(all.get(tank)); }
    }

    private final class DistributedEnergy implements IEnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            List<Target> all = targets().energy; if (all.isEmpty()) return 0; int remaining = maxReceive; int start = Math.floorMod(energyInsertCursor, all.size()); int viable = 0;
            for (Target target : all) { IEnergyStorage h = energy(target); if (h != null && h.receiveEnergy(maxReceive, true) > 0) viable++; }
            for (int i = 0, left = viable; i < all.size() && remaining > 0 && left > 0; i++) {
                IEnergyStorage h = energy(all.get((start + i) % all.size())); if (h == null || h.receiveEnergy(remaining, true) <= 0) continue;
                int share = (remaining + left - 1) / left--; remaining -= h.receiveEnergy(Math.min(share, remaining), simulate);
            }
            if (!simulate && remaining != maxReceive && !all.isEmpty()) energyInsertCursor = (start + 1) % all.size();
            return maxReceive - remaining;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            List<Target> all = targets().energy; if (all.isEmpty()) return 0; int remaining = maxExtract; int start = Math.floorMod(energyInsertCursor, all.size());
            for (int i = 0; i < all.size() && remaining > 0; i++) { IEnergyStorage h = energy(all.get((start + i) % all.size())); if (h != null) remaining -= h.extractEnergy(remaining, simulate); }
            if (!simulate && remaining != maxExtract && !all.isEmpty()) energyInsertCursor = (start + 1) % all.size();
            return maxExtract - remaining;
        }
        @Override public int getEnergyStored() { long sum = 0; for (Target t : targets().energy) { IEnergyStorage h = energy(t); if (h != null) sum += h.getEnergyStored(); } return (int)Math.min(Integer.MAX_VALUE, sum); }
        @Override public int getMaxEnergyStored() { long sum = 0; for (Target t : targets().energy) { IEnergyStorage h = energy(t); if (h != null) sum += h.getMaxEnergyStored(); } return (int)Math.min(Integer.MAX_VALUE, sum); }
        @Override public boolean canExtract() { for (Target t : targets().energy) { IEnergyStorage h = energy(t); if (h != null && h.canExtract()) return true; } return false; }
        @Override public boolean canReceive() { for (Target t : targets().energy) { IEnergyStorage h = energy(t); if (h != null && h.canReceive()) return true; } return false; }
    }
}
