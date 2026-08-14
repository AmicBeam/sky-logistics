package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.TransferCompat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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

/** A zero-buffer, budgeted internal routing view over a cached cluster of adjacent containers. */
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
    private final int[] itemInsertSlotCursors = new int[MAX_CONFIGURABLE_TARGETS];
    private final int[] visibleItemSlots = new int[MAX_CONFIGURABLE_TARGETS];
    private final int[] visibleFluidTanks = new int[MAX_CONFIGURABLE_TARGETS];
    private final FluidStack[] visibleFluids = new FluidStack[MAX_CONFIGURABLE_TARGETS];
    private final ItemStack[] rejectedItems = new ItemStack[MAX_CONFIGURABLE_TARGETS];
    private final long[] rejectedItemUntil = new long[MAX_CONFIGURABLE_TARGETS];
    private final FluidStack[] rejectedFluids = new FluidStack[MAX_CONFIGURABLE_TARGETS];
    private final long[] rejectedFluidUntil = new long[MAX_CONFIGURABLE_TARGETS];
    private long operationBudgetTick = Long.MIN_VALUE;
    private int remainingOperations;
    private ItemInsertPlan itemInsertPlan;
    private ItemExtractPlan itemExtractPlan;
    private FluidInsertPlan fluidInsertPlan;
    private FluidDrainPlan fluidDrainPlan;
    private EnergyPlan energyReceivePlan;
    private EnergyPlan energyExtractPlan;
    private long energySnapshotTick = Long.MIN_VALUE;
    private int energyStoredSnapshot;
    private int energyCapacitySnapshot;
    private boolean energyCanExtractSnapshot;
    private boolean energyCanReceiveSnapshot;

    public SkyDistributorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_DISTRIBUTOR.get(), pos, state);
}
    public void invalidateTargets() {
        targetsDirty = true;
        clearTransientCaches();
    }

    public void refreshTargets() {
        if (level == null) return;
        targetCache = discoverTargets();
        highlightSnapshot = createTargetSnapshot(targetCache);
        targetsDirty = false;
        nextRescan = level.getGameTime() + RESCAN_INTERVAL;
        clearTransientCaches();
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
        boolean searchAllSides = SkyLogisticsConfig.distributorSearchAllSides();
        ArrayDeque<SearchNode> queue = new ArrayDeque<>();
        Set<SearchNode> visited = new HashSet<>();
        Set<BlockPos> discovered = new HashSet<>();
        for (Direction direction : Direction.values())
            queue.add(new SearchNode(worldPosition.relative(direction), direction.getOpposite()));
        while (!queue.isEmpty() && found < maxTargets) {
            SearchNode candidate = queue.removeFirst();
            BlockPos pos = candidate.pos;
            SearchNode visitKey = searchAllSides ? new SearchNode(pos, null) : candidate;
            if (!visited.add(visitKey) || discovered.contains(pos) || !level.hasChunkAt(pos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || blockEntity instanceof SkyDistributorBlockEntity) continue;
            Target target = inspect(pos, candidate.accessSide, searchAllSides);
            if (!target.usable()) continue;
            discovered.add(pos);
            found++;
            if (target.items) itemTargets.add(target);
            if (target.fluids) fluidTargets.add(target);
            if (target.energy) energyTargets.add(target);
            for (Direction direction : Direction.values())
                queue.addLast(new SearchNode(pos.relative(direction), direction.getOpposite()));
        }
        return new TargetCache(List.copyOf(itemTargets), List.copyOf(fluidTargets), List.copyOf(energyTargets));
    }

    private Target inspect(BlockPos pos, Direction accessSide, boolean searchAllSides) {
        Direction itemSide = SkyLogisticsConfig.enableDistributorItems()
                ? searchAllSides ? firstItemSide(pos) : usableItems(TransferCompat.itemHandler(level.getCapability(Capabilities.Item.BLOCK, pos, accessSide))) ? accessSide : null : null;
        Direction fluidSide = SkyLogisticsConfig.enableDistributorFluids()
                ? searchAllSides ? firstFluidSide(pos) : usableFluids(TransferCompat.fluidHandler(level.getCapability(Capabilities.Fluid.BLOCK, pos, accessSide))) ? accessSide : null : null;
        Direction energySide = SkyLogisticsConfig.enableDistributorEnergy()
                ? searchAllSides ? firstEnergySide(pos) : usableEnergy(TransferCompat.energyStorage(level.getCapability(Capabilities.Energy.BLOCK, pos, accessSide))) ? accessSide : null : null;
        return new Target(pos.immutable(), itemSide, fluidSide, energySide,
                itemSide != null, fluidSide != null, energySide != null);
    }

    private Direction firstItemSide(BlockPos pos) {
        for (Direction direction : Direction.values())
            if (usableItems(TransferCompat.itemHandler(level.getCapability(Capabilities.Item.BLOCK, pos, direction)))) return direction;
        return null;
    }
    private Direction firstFluidSide(BlockPos pos) {
        for (Direction direction : Direction.values())
            if (usableFluids(TransferCompat.fluidHandler(level.getCapability(Capabilities.Fluid.BLOCK, pos, direction)))) return direction;
        return null;
    }
    private Direction firstEnergySide(BlockPos pos) {
        for (Direction direction : Direction.values())
            if (usableEnergy(TransferCompat.energyStorage(level.getCapability(Capabilities.Energy.BLOCK, pos, direction)))) return direction;
        return null;
    }
    private static boolean usableItems(ItemHandler handler) { return handler != null && handler.getSlots() > 0; }
    private static boolean usableFluids(FluidHandler handler) { return handler != null && handler.getTanks() > 0; }
    private static boolean usableEnergy(EnergyStorage storage) {
        return storage != null && (storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive());
    }

    private ItemHandler item(Target target) {
        if (!target.items) return null;
        return TransferCompat.itemHandler(level.getCapability(Capabilities.Item.BLOCK, target.pos, target.itemSide));
    }

    private FluidHandler fluid(Target target) {
        if (!target.fluids) return null;
        return TransferCompat.fluidHandler(level.getCapability(Capabilities.Fluid.BLOCK, target.pos, target.fluidSide));
    }

    private EnergyStorage energy(Target target) {
        if (!target.energy) return null;
        return TransferCompat.energyStorage(level.getCapability(Capabilities.Energy.BLOCK, target.pos, target.energySide));
    }

    public ItemHandler itemHandler() { return SkyLogisticsConfig.enableDistributorItems() ? items : null; }
    public FluidHandler fluidHandler() { return SkyLogisticsConfig.enableDistributorFluids() ? fluids : null; }
    public EnergyStorage energyHandler() { return SkyLogisticsConfig.enableDistributorEnergy() ? energy : null; }
    public boolean hasItemTargets() { return SkyLogisticsConfig.enableDistributorItems() && !targets().items.isEmpty(); }
    public boolean hasFluidTargets() { return SkyLogisticsConfig.enableDistributorFluids() && !targets().fluids.isEmpty(); }
    public boolean hasEnergyTargets() { return SkyLogisticsConfig.enableDistributorEnergy() && !targets().energy.isEmpty(); }
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

    private record SearchNode(BlockPos pos, Direction accessSide) {}

    private long gameTime() { return level == null ? Long.MIN_VALUE : level.getGameTime(); }

    private void prepareOperationBudget() {
        long now = gameTime();
        if (operationBudgetTick == now) return;
        operationBudgetTick = now;
        remainingOperations = SkyLogisticsConfig.distributorOpsPerTick();
        Arrays.fill(visibleItemSlots, -2);
        Arrays.fill(visibleFluidTanks, -2);
        Arrays.fill(visibleFluids, null);
        itemInsertPlan = null;
        itemExtractPlan = null;
        fluidInsertPlan = null;
        fluidDrainPlan = null;
        energyReceivePlan = null;
        energyExtractPlan = null;
    }

    private boolean takeOperation() {
        prepareOperationBudget();
        if (remainingOperations <= 0) return false;
        remainingOperations--;
        return true;
    }

    private void clearTransientCaches() {
        operationBudgetTick = Long.MIN_VALUE;
        energySnapshotTick = Long.MIN_VALUE;
        Arrays.fill(rejectedItems, null);
        Arrays.fill(rejectedItemUntil, 0L);
        Arrays.fill(rejectedFluids, null);
        Arrays.fill(rejectedFluidUntil, 0L);
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        return first.getAmount() == second.getAmount() && FluidStack.isSameFluidSameComponents(first, second);
    }

    private static boolean sameFluidType(FluidStack first, FluidStack second) { return FluidStack.isSameFluidSameComponents(first, second); }

    private record Target(BlockPos pos, Direction itemSide, Direction fluidSide, Direction energySide,
            boolean items, boolean fluids, boolean energy) {
        boolean usable() { return items || fluids || energy; }
    }

    private record TargetCache(List<Target> items, List<Target> fluids, List<Target> energy) {
        private static final TargetCache EMPTY = new TargetCache(List.of(), List.of(), List.of());
    }

    private record ItemMove(int target, int slot, int amount) {}
    private record ItemInsertPlan(long tick, ItemStack request, List<ItemMove> moves, int accepted) {}
    private record ItemExtractPlan(long tick, int target, int amount, int slot) {}
    private record FluidMove(int target, int amount) {}
    private record FluidInsertPlan(long tick, FluidStack request, List<FluidMove> moves, int accepted) {}
    private record FluidDrainPlan(long tick, FluidStack request, List<FluidMove> moves, int extracted) {}
    private record EnergyPlan(long tick, int request, List<FluidMove> moves, int accepted) {}

    private final class DistributedItems implements ItemHandler {
        @Override public int getSlots() {
            if (!SkyLogisticsConfig.enableDistributorItems()) return 0;
            return targets().items.size();
        }
        @Override public ItemStack getStackInSlot(int slot) {
            ItemHandler handler = handler(slot);
            if (handler == null || handler.getSlots() == 0) return ItemStack.EMPTY;
            int sourceSlot = visibleSlot(slot, handler);
            return sourceSlot < 0 ? ItemStack.EMPTY : handler.getStackInSlot(sourceSlot);
        }
        @Override public ItemStack insertItem(int ignored, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemInsertPlan plan = matchingItemInsertPlan(stack);
            if (plan == null) plan = buildItemInsertPlan(stack);
            ItemStack remaining = stack.copy();
            if (simulate) {
                remaining.shrink(plan.accepted);
                return remaining;
            }
            int inserted = executeItemInsertPlan(plan, stack);
            remaining.shrink(inserted);
            itemInsertPlan = null;
            return remaining;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemHandler handler = handler(slot);
            if (handler == null || amount <= 0 || handler.getSlots() == 0) return ItemStack.EMPTY;
            ItemExtractPlan plan = itemExtractPlan;
            if (plan == null || plan.tick != gameTime() || plan.target != slot || plan.amount != amount) {
                int sourceSlot = visibleSlot(slot, handler);
                if (sourceSlot < 0 || !takeOperation()) return ItemStack.EMPTY;
                plan = new ItemExtractPlan(gameTime(), slot, amount, sourceSlot);
                itemExtractPlan = plan;
            }
            ItemStack extracted = handler.extractItem(plan.slot, amount, simulate);
            if (!simulate) {
                if (!extracted.isEmpty()) itemExtractCursors[slot] = plan.slot + 1;
                visibleItemSlots[slot] = -2;
                itemExtractPlan = null;
            }
            return extracted;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
        private ItemHandler handler(int slot) {
            List<Target> all = targets().items;
            return slot < 0 || slot >= all.size() ? null : item(all.get(slot));
        }
        private int visibleSlot(int target, ItemHandler handler) {
            prepareOperationBudget();
            int cached = visibleItemSlots[target];
            if (cached >= -1) return cached;
            int slots = handler.getSlots();
            int start = Math.floorMod(itemExtractCursors[target], slots);
            for (int i = 0; i < slots; i++) {
                if (!takeOperation()) return -1;
                int sourceSlot = (start + i) % slots;
                if (!handler.getStackInSlot(sourceSlot).isEmpty()) {
                    visibleItemSlots[target] = sourceSlot;
                    return sourceSlot;
                }
            }
            visibleItemSlots[target] = -1;
            return -1;
        }

        private ItemInsertPlan matchingItemInsertPlan(ItemStack stack) {
            prepareOperationBudget();
            return itemInsertPlan != null && itemInsertPlan.tick == gameTime()
                    && sameItem(itemInsertPlan.request, stack) ? itemInsertPlan : null;
        }

        private ItemInsertPlan buildItemInsertPlan(ItemStack stack) {
            List<Target> all = targets().items;
            List<ItemMove> moves = new ArrayList<>();
            if (!all.isEmpty()) {
                int start = Math.floorMod(itemInsertCursor, all.size());
                int candidateLimit = Math.min(stack.getCount(), all.size());
                int share = (int)(((long)stack.getCount() + all.size() - 1L) / all.size());
                for (int offset = 0; offset < all.size() && moves.size() < candidateLimit; offset++) {
                    int target = (start + offset) % all.size();
                    if (isRejected(target, stack)) continue;
                    if (!takeOperation()) break;
                    ItemHandler handler = item(all.get(target));
                    if (handler == null || handler.getSlots() == 0) continue;
                    int slots = handler.getSlots();
                    int slotStart = Math.floorMod(itemInsertSlotCursors[target], slots);
                    boolean fullyScanned = true;
                    for (int checked = 0; checked < slots; checked++) {
                        if (!takeOperation()) { fullyScanned = false; break; }
                        int slot = (slotStart + checked) % slots;
                        ItemStack offer = stack.copy(); offer.setCount(share);
                        ItemStack rejected = handler.insertItem(slot, offer, true);
                        int accepted = offer.getCount() - rejected.getCount();
                        if (accepted > 0) { moves.add(new ItemMove(target, slot, accepted)); break; }
                    }
                    if (fullyScanned && (moves.isEmpty() || moves.get(moves.size() - 1).target != target)) {
                        rejectedItems[target] = stack.copyWithCount(1);
                        rejectedItemUntil[target] = gameTime() + 5L;
                    }
                }
            }
            int accepted = (int)Math.min(stack.getCount(), moves.stream().mapToLong(ItemMove::amount).sum());
            itemInsertPlan = new ItemInsertPlan(gameTime(), stack.copy(), List.copyOf(moves), accepted);
            return itemInsertPlan;
        }

        private boolean isRejected(int target, ItemStack stack) {
            ItemStack rejected = rejectedItems[target];
            return rejected != null && gameTime() < rejectedItemUntil[target]
                    && ItemStack.isSameItemSameComponents(rejected, stack);
        }

        private int executeItemInsertPlan(ItemInsertPlan plan, ItemStack request) {
            List<Target> all = targets().items;
            int inserted = 0;
            for (ItemMove move : plan.moves) {
                if (move.target >= all.size() || inserted >= request.getCount()) break;
                ItemHandler handler = item(all.get(move.target));
                if (handler == null || move.slot >= handler.getSlots()) continue;
                ItemStack offer = request.copy();
                offer.setCount(Math.min(move.amount, request.getCount() - inserted));
                ItemStack rejected = handler.insertItem(move.slot, offer, false);
                int moved = offer.getCount() - rejected.getCount();
                if (moved > 0) {
                    inserted += moved;
                    itemInsertSlotCursors[move.target] = move.slot + 1;
                    visibleItemSlots[move.target] = -2;
                    rejectedItems[move.target] = null;
                }
            }
            if (inserted > 0 && !all.isEmpty()) itemInsertCursor = (itemInsertCursor + 1) % all.size();
            return inserted;
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
            prepareOperationBudget();
            if (visibleFluids[tank] != null) return visibleFluids[tank].copy();
            int cached = visibleFluidTanks[tank];
            if (cached == -1) return FluidStack.EMPTY;
            int start = cached >= 0 ? cached : Math.floorMod(fluidExtractCursors[tank], handler.getTanks());
            int checks = cached >= 0 ? 1 : handler.getTanks();
            for (int i = 0; i < checks; i++) {
                if (!takeOperation()) return FluidStack.EMPTY;
                int sourceTank = (start + i) % handler.getTanks();
                FluidStack stack = handler.getFluidInTank(sourceTank);
                if (!stack.isEmpty()) {
                    visibleFluidTanks[tank] = sourceTank;
                    visibleFluids[tank] = stack.copy();
                    return stack;
                }
            }
            visibleFluidTanks[tank] = -1;
            return FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { FluidHandler h = handler(tank); return h == null ? 0 : Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        @Override public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            FluidInsertPlan plan = fluidInsertPlan;
            if (plan == null || plan.tick != gameTime() || !sameFluid(plan.request, resource)) {
                plan = buildFluidInsertPlan(resource);
            }
            if (!action.execute()) return plan.accepted;
            int inserted = executeFluidInsertPlan(plan, resource);
            fluidInsertPlan = null;
            return inserted;
        }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return FluidStack.EMPTY;
            FluidDrainPlan plan = fluidDrainPlan;
            if (plan == null || plan.tick != gameTime() || !sameFluid(plan.request, resource)) {
                plan = buildFluidDrainPlan(resource);
            }
            if (!action.execute()) {
                FluidStack result = resource.copy(); result.setAmount(plan.extracted); return result;
            }
            FluidStack result = executeFluidDrainPlan(plan, resource);
            fluidDrainPlan = null;
            return result;
        }
        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < getTanks(); i++) { FluidStack visible = getFluidInTank(i); if (!visible.isEmpty()) { FluidStack request = visible.copy(); request.setAmount(maxDrain); return drain(request, action); } }
            return FluidStack.EMPTY;
        }
        private FluidHandler handler(int tank) { List<Target> all = targets().fluids; return tank < 0 || tank >= all.size() ? null : fluid(all.get(tank)); }

        private FluidInsertPlan buildFluidInsertPlan(FluidStack resource) {
            List<Target> all = targets().fluids;
            List<FluidMove> moves = new ArrayList<>();
            if (!all.isEmpty()) {
                int start = Math.floorMod(fluidInsertCursor, all.size());
                int candidateLimit = Math.min(resource.getAmount(), all.size());
                int share = (int)(((long)resource.getAmount() + all.size() - 1L) / all.size());
                for (int offset = 0; offset < all.size() && moves.size() < candidateLimit; offset++) {
                    int target = (start + offset) % all.size();
                    if (rejectedFluids[target] != null && gameTime() < rejectedFluidUntil[target]
                            && sameFluidType(rejectedFluids[target], resource)) continue;
                    if (!takeOperation()) break;
                    FluidHandler handler = fluid(all.get(target));
                    if (handler == null) continue;
                    FluidStack offer = resource.copy(); offer.setAmount(share);
                    int accepted = handler.fill(offer, FluidAction.SIMULATE);
                    if (accepted > 0) moves.add(new FluidMove(target, accepted));
                    else {
                        rejectedFluids[target] = resource.copy(); rejectedFluids[target].setAmount(1);
                        rejectedFluidUntil[target] = gameTime() + 5L;
                    }
                }
            }
            int accepted = (int)Math.min(resource.getAmount(), moves.stream().mapToLong(FluidMove::amount).sum());
            fluidInsertPlan = new FluidInsertPlan(gameTime(), resource.copy(), List.copyOf(moves), accepted);
            return fluidInsertPlan;
        }

        private int executeFluidInsertPlan(FluidInsertPlan plan, FluidStack resource) {
            List<Target> all = targets().fluids;
            int inserted = 0;
            for (FluidMove move : plan.moves) {
                if (move.target >= all.size() || inserted >= resource.getAmount()) break;
                FluidHandler handler = fluid(all.get(move.target));
                if (handler == null) continue;
                FluidStack offer = resource.copy();
                offer.setAmount(Math.min(move.amount, resource.getAmount() - inserted));
                int moved = handler.fill(offer, FluidAction.EXECUTE);
                inserted += moved;
                if (moved > 0) rejectedFluids[move.target] = null;
            }
            if (inserted > 0 && !all.isEmpty()) fluidInsertCursor = (fluidInsertCursor + 1) % all.size();
            return inserted;
        }

        private FluidDrainPlan buildFluidDrainPlan(FluidStack resource) {
            List<Target> all = targets().fluids;
            List<FluidMove> moves = new ArrayList<>();
            int remaining = resource.getAmount();
            for (int offset = 0; offset < all.size() && remaining > 0; offset++) {
                if (!takeOperation()) break;
                int target = Math.floorMod(fluidInsertCursor + offset, all.size());
                FluidHandler handler = fluid(all.get(target));
                if (handler == null) continue;
                FluidStack request = resource.copy(); request.setAmount(remaining);
                FluidStack extracted = handler.drain(request, FluidAction.SIMULATE);
                if (!extracted.isEmpty()) { moves.add(new FluidMove(target, extracted.getAmount())); remaining -= extracted.getAmount(); }
            }
            fluidDrainPlan = new FluidDrainPlan(gameTime(), resource.copy(), List.copyOf(moves), resource.getAmount() - remaining);
            return fluidDrainPlan;
        }

        private FluidStack executeFluidDrainPlan(FluidDrainPlan plan, FluidStack resource) {
            List<Target> all = targets().fluids;
            FluidStack result = FluidStack.EMPTY;
            for (FluidMove move : plan.moves) {
                if (move.target >= all.size()) continue;
                FluidHandler handler = fluid(all.get(move.target));
                if (handler == null) continue;
                FluidStack request = resource.copy(); request.setAmount(move.amount);
                FluidStack extracted = handler.drain(request, FluidAction.EXECUTE);
                if (!extracted.isEmpty()) {
                    if (result.isEmpty()) result = extracted.copy(); else result.grow(extracted.getAmount());
                    fluidExtractCursors[move.target]++;
                    visibleFluidTanks[move.target] = -2;
                    visibleFluids[move.target] = null;
                }
            }
            if (!result.isEmpty() && !all.isEmpty()) fluidInsertCursor = (fluidInsertCursor + 1) % all.size();
            return result;
        }
    }

    private final class DistributedEnergy implements EnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
            EnergyPlan plan = energyReceivePlan;
            if (plan == null || plan.tick != gameTime() || plan.request != maxReceive) plan = buildEnergyPlan(maxReceive, true);
            if (simulate) return plan.accepted;
            int received = executeEnergyPlan(plan, maxReceive, true);
            energyReceivePlan = null;
            return received;
        }
        @Override public int extractEnergy(int maxExtract, boolean simulate) {
            EnergyPlan plan = energyExtractPlan;
            if (plan == null || plan.tick != gameTime() || plan.request != maxExtract) plan = buildEnergyPlan(maxExtract, false);
            if (simulate) return plan.accepted;
            int extracted = executeEnergyPlan(plan, maxExtract, false);
            energyExtractPlan = null;
            return extracted;
        }
        @Override public int getEnergyStored() { refreshEnergySnapshot(); return energyStoredSnapshot; }
        @Override public int getMaxEnergyStored() { refreshEnergySnapshot(); return energyCapacitySnapshot; }
        @Override public boolean canExtract() { refreshEnergySnapshot(); return energyCanExtractSnapshot; }
        @Override public boolean canReceive() { refreshEnergySnapshot(); return energyCanReceiveSnapshot; }

        private EnergyPlan buildEnergyPlan(int amount, boolean receive) {
            List<Target> all = targets().energy;
            List<FluidMove> moves = new ArrayList<>();
            if (!all.isEmpty() && amount > 0) {
                int start = Math.floorMod(energyInsertCursor, all.size());
                int candidateLimit = Math.min(amount, all.size());
                int share = (int)(((long)amount + all.size() - 1L) / all.size());
                for (int offset = 0; offset < all.size() && moves.size() < candidateLimit; offset++) {
                    if (!takeOperation()) break;
                    int target = (start + offset) % all.size();
                    EnergyStorage handler = energy(all.get(target));
                    if (handler == null) continue;
                    int accepted = receive ? handler.receiveEnergy(share, true) : handler.extractEnergy(share, true);
                    if (accepted > 0) moves.add(new FluidMove(target, accepted));
                }
            }
            int accepted = (int)Math.min(amount, moves.stream().mapToLong(FluidMove::amount).sum());
            EnergyPlan plan = new EnergyPlan(gameTime(), amount, List.copyOf(moves), accepted);
            if (receive) energyReceivePlan = plan; else energyExtractPlan = plan;
            return plan;
        }

        private int executeEnergyPlan(EnergyPlan plan, int amount, boolean receive) {
            List<Target> all = targets().energy;
            int moved = 0;
            for (FluidMove move : plan.moves) {
                if (move.target >= all.size() || moved >= amount) break;
                EnergyStorage handler = energy(all.get(move.target));
                if (handler == null) continue;
                int offer = Math.min(move.amount, amount - moved);
                moved += receive ? handler.receiveEnergy(offer, false) : handler.extractEnergy(offer, false);
            }
            if (moved > 0 && !all.isEmpty()) energyInsertCursor = (energyInsertCursor + 1) % all.size();
            energySnapshotTick = Long.MIN_VALUE;
            return moved;
        }

        private void refreshEnergySnapshot() {
            long now = gameTime();
            if (energySnapshotTick == now) return;
            long stored = 0L, capacity = 0L;
            boolean canExtract = false, canReceive = false;
            for (Target target : targets().energy) {
                if (!takeOperation()) break;
                EnergyStorage handler = energy(target);
                if (handler == null) continue;
                stored += handler.getEnergyStored();
                capacity += handler.getMaxEnergyStored();
                canExtract |= handler.canExtract();
                canReceive |= handler.canReceive();
            }
            energyStoredSnapshot = (int)Math.min(Integer.MAX_VALUE, stored);
            energyCapacitySnapshot = (int)Math.min(Integer.MAX_VALUE, capacity);
            energyCanExtractSnapshot = canExtract;
            energyCanReceiveSnapshot = canReceive;
            energySnapshotTick = now;
        }
    }
}
