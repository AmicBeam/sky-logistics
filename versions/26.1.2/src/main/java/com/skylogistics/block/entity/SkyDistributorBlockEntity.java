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
import net.neoforged.neoforge.capabilities.Capabilities;
import com.skylogistics.util.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import com.skylogistics.util.FluidHandler;
import com.skylogistics.util.ItemHandler;

/** A zero-buffer, bounded capability fan-out over a cached cluster of adjacent containers. */
public class SkyDistributorBlockEntity extends BlockEntity {
    private static final int MAX_CONFIGURABLE_TARGETS = 64;
    private static final long RESCAN_INTERVAL = 100L;
    private final DistributedItems items = new DistributedItems();
    private final DistributedFluids fluids = new DistributedFluids();
    private final DistributedEnergy energy = new DistributedEnergy();
    private TargetCache targetCache = TargetCache.EMPTY;
    private List<TargetSnapshot> highlightSnapshot = List.of();
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
        highlightSnapshot = createTargetSnapshot(targetCache);
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
        Direction itemSide = firstItemSide(pos);
        Direction fluidSide = firstFluidSide(pos);
        Direction energySide = firstEnergySide(pos);
        return new Target(pos.immutable(), itemSide, fluidSide, energySide,
                itemSide != null, fluidSide != null, energySide != null);
    }

    private Direction firstItemSide(BlockPos pos) {
        for (Direction direction : Direction.values())
            if (usableItems(com.skylogistics.util.TransferCompat.itemHandler(level.getCapability(Capabilities.Item.BLOCK, pos, direction)))) return direction;
        return null;
    }
    private Direction firstFluidSide(BlockPos pos) {
        for (Direction direction : Direction.values())
            if (usableFluids(com.skylogistics.util.TransferCompat.fluidHandler(level.getCapability(Capabilities.Fluid.BLOCK, pos, direction)))) return direction;
        return null;
    }
    private Direction firstEnergySide(BlockPos pos) {
        for (Direction direction : Direction.values())
            if (usableEnergy(com.skylogistics.util.TransferCompat.energyStorage(level.getCapability(Capabilities.Energy.BLOCK, pos, direction)))) return direction;
        return null;
    }
    private static boolean usableItems(ItemHandler handler) { return handler != null && handler.getSlots() > 0; }
    private static boolean usableFluids(FluidHandler handler) { return handler != null && handler.getTanks() > 0; }
    private static boolean usableEnergy(EnergyStorage storage) {
        return storage != null && (storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive());
    }

    private ItemHandler item(Target target) {
        if (!target.items) return null;
        return com.skylogistics.util.TransferCompat.itemHandler(level.getCapability(Capabilities.Item.BLOCK, target.pos, target.itemSide));
    }

    private FluidHandler fluid(Target target) {
        if (!target.fluids) return null;
        return com.skylogistics.util.TransferCompat.fluidHandler(level.getCapability(Capabilities.Fluid.BLOCK, target.pos, target.fluidSide));
    }

    private EnergyStorage energy(Target target) {
        if (!target.energy) return null;
        return com.skylogistics.util.TransferCompat.energyStorage(level.getCapability(Capabilities.Energy.BLOCK, target.pos, target.energySide));
    }

    public ItemHandler itemHandler() { return SkyLogisticsConfig.enableDistributorItems() ? items : null; }
    public FluidHandler fluidHandler() { return SkyLogisticsConfig.enableDistributorFluids() ? fluids : null; }
    public EnergyStorage energyHandler() { return SkyLogisticsConfig.enableDistributorEnergy() ? energy : null; }

    public List<TargetSnapshot> targetSnapshot() {
        targets();
        return highlightSnapshot;
    }

    private static List<TargetSnapshot> createTargetSnapshot(TargetCache cache) {
        java.util.LinkedHashMap<BlockPos, Integer> masks = new java.util.LinkedHashMap<>();
        cache.items.forEach(target -> masks.merge(target.pos, 1, (left, right) -> left | right));
        cache.fluids.forEach(target -> masks.merge(target.pos, 2, (left, right) -> left | right));
        cache.energy.forEach(target -> masks.merge(target.pos, 4, (left, right) -> left | right));
        return masks.entrySet().stream().map(entry -> new TargetSnapshot(entry.getKey(), entry.getValue())).toList();
    }

    public record TargetSnapshot(BlockPos pos, int resourceMask) {}

    private record Target(BlockPos pos, Direction itemSide, Direction fluidSide, Direction energySide,
            boolean items, boolean fluids, boolean energy) {
        boolean usable() { return items || fluids || energy; }
    }

    private record TargetCache(List<Target> items, List<Target> fluids, List<Target> energy) {
        private static final TargetCache EMPTY = new TargetCache(List.of(), List.of(), List.of());
    }

    private final class DistributedItems implements ItemHandler {
        @Override public int getSlots() {
            if (!SkyLogisticsConfig.enableDistributorItems()) return 0;
            return targets().items.size();
        }
        @Override public ItemStack getStackInSlot(int slot) {
            ItemHandler handler = handler(slot);
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
                ItemHandler handler = item(all.get((start + i) % all.size()));
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
            ItemHandler handler = handler(slot);
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
        private ItemHandler handler(int slot) {
            List<Target> all = targets().items;
            return slot < 0 || slot >= all.size() ? null : item(all.get(slot));
        }
        private boolean canAccept(ItemHandler handler, ItemStack stack) {
            return handler != null && insertStacked(handler, stack.copy(), true).getCount() < stack.getCount();
        }
        private ItemStack insertStacked(ItemHandler handler, ItemStack stack, boolean simulate) {
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

    private final class DistributedFluids implements FluidHandler {
        @Override public int getTanks() {
            if (!SkyLogisticsConfig.enableDistributorFluids()) return 0;
            return targets().fluids.size();
        }
        @Override public FluidStack getFluidInTank(int tank) {
            FluidHandler handler = handler(tank);
            if (handler == null || handler.getTanks() == 0) return FluidStack.EMPTY;
            int start = Math.floorMod(fluidExtractCursors[tank], handler.getTanks());
            for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack stack = handler.getFluidInTank((start + i) % handler.getTanks());
                if (!stack.isEmpty()) return stack;
            }
            return FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { FluidHandler h = handler(tank); return h == null ? 0 : Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        @Override public int fill(FluidStack resource, FluidHandler.FluidAction action) {
            if (resource.isEmpty()) return 0;
            List<Target> all = targets().fluids; int remaining = resource.getAmount();
            if (all.isEmpty()) return 0;
            int start = Math.floorMod(fluidInsertCursor, all.size()); int viable = 0;
            for (Target target : all) { FluidHandler h = fluid(target); if (h != null && h.fill(resource, FluidHandler.FluidAction.SIMULATE) > 0) viable++; }
            for (int i = 0, left = viable; i < all.size() && remaining > 0 && left > 0; i++) {
                FluidHandler h = fluid(all.get((start + i) % all.size()));
                if (h == null || h.fill(resource, FluidHandler.FluidAction.SIMULATE) <= 0) continue;
                int share = (remaining + left - 1) / left--; FluidStack offer = resource.copy(); offer.setAmount(Math.min(share, remaining));
                remaining -= h.fill(offer, action);
            }
            if (action.execute() && remaining != resource.getAmount() && !all.isEmpty()) fluidInsertCursor = (start + 1) % all.size();
            return resource.getAmount() - remaining;
        }
        @Override public FluidStack drain(FluidStack resource, FluidHandler.FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            FluidStack result = FluidStack.EMPTY;
            for (int i = 0; i < getTanks() && result.getAmount() < resource.getAmount(); i++) {
                FluidHandler h = handler(i); if (h == null) continue;
                FluidStack request = resource.copy(); request.setAmount(resource.getAmount() - result.getAmount());
                FluidStack part = h.drain(request, action);
                if (!part.isEmpty()) { if (result.isEmpty()) result = part.copy(); else result.grow(part.getAmount()); if (action.execute()) fluidExtractCursors[i]++; }
            }
            return result;
        }
        @Override public FluidStack drain(int maxDrain, FluidHandler.FluidAction action) {
            for (int i = 0; i < getTanks(); i++) { FluidStack visible = getFluidInTank(i); if (!visible.isEmpty()) { FluidStack request = visible.copy(); request.setAmount(maxDrain); return drain(request, action); } }
            return FluidStack.EMPTY;
        }
        private FluidHandler handler(int tank) { List<Target> all = targets().fluids; return tank < 0 || tank >= all.size() ? null : fluid(all.get(tank)); }
    }

    private final class DistributedEnergy implements EnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            List<Target> all = targets().energy; if (all.isEmpty()) return 0; int remaining = maxReceive; int start = Math.floorMod(energyInsertCursor, all.size()); int viable = 0;
            for (Target target : all) { EnergyStorage h = energy(target); if (h != null && h.receiveEnergy(maxReceive, true) > 0) viable++; }
            for (int i = 0, left = viable; i < all.size() && remaining > 0 && left > 0; i++) {
                EnergyStorage h = energy(all.get((start + i) % all.size())); if (h == null || h.receiveEnergy(remaining, true) <= 0) continue;
                int share = (remaining + left - 1) / left--; remaining -= h.receiveEnergy(Math.min(share, remaining), simulate);
            }
            if (!simulate && remaining != maxReceive && !all.isEmpty()) energyInsertCursor = (start + 1) % all.size();
            return maxReceive - remaining;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            List<Target> all = targets().energy; if (all.isEmpty()) return 0; int remaining = maxExtract; int start = Math.floorMod(energyInsertCursor, all.size());
            for (int i = 0; i < all.size() && remaining > 0; i++) { EnergyStorage h = energy(all.get((start + i) % all.size())); if (h != null) remaining -= h.extractEnergy(remaining, simulate); }
            if (!simulate && remaining != maxExtract && !all.isEmpty()) energyInsertCursor = (start + 1) % all.size();
            return maxExtract - remaining;
        }
        @Override public int getEnergyStored() { long sum = 0; for (Target t : targets().energy) { EnergyStorage h = energy(t); if (h != null) sum += h.getEnergyStored(); } return (int)Math.min(Integer.MAX_VALUE, sum); }
        @Override public int getMaxEnergyStored() { long sum = 0; for (Target t : targets().energy) { EnergyStorage h = energy(t); if (h != null) sum += h.getMaxEnergyStored(); } return (int)Math.min(Integer.MAX_VALUE, sum); }
        @Override public boolean canExtract() { for (Target t : targets().energy) { EnergyStorage h = energy(t); if (h != null && h.canExtract()) return true; } return false; }
        @Override public boolean canReceive() { for (Target t : targets().energy) { EnergyStorage h = energy(t); if (h != null && h.canReceive()) return true; } return false; }
    }
}
