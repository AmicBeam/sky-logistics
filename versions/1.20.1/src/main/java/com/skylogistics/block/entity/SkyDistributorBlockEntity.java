package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModBlockEntities;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** A zero-buffer, budgeted internal routing view over a cached cluster of adjacent containers. */
public class SkyDistributorBlockEntity extends BlockEntity {
    private static final int MAX_CONFIGURABLE_TARGETS = 64;
    private static final long RESCAN_INTERVAL = 100L;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final DistributedItems[] items = new DistributedItems[DIRECTIONS.length];
    private final DistributedFluids[] fluids = new DistributedFluids[DIRECTIONS.length];
    private final DistributedEnergy[] energy = new DistributedEnergy[DIRECTIONS.length];
    private final TargetCache[] targetCaches = new TargetCache[DIRECTIONS.length];
    private final DiscoveryState[] targetDiscoveries = new DiscoveryState[DIRECTIONS.length];
    private List<TargetSnapshot> highlightSnapshot = List.of();
    private final boolean[] targetsDirty = new boolean[DIRECTIONS.length];
    private final long[] nextRescan = new long[DIRECTIONS.length];
    private Direction selectedSide = Direction.NORTH;
    private int itemInsertCursor;
    private int fluidInsertCursor;
    private int energyReceiveCursor;
    private int energyExtractCursor;
    private int energySnapshotCursor;
    private int energySnapshotScanned;
    private long energyStoredAccumulator;
    private long energyCapacityAccumulator;
    private boolean energyCanExtractAccumulator;
    private boolean energyCanReceiveAccumulator;
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
    private long scanBudgetTick = Long.MIN_VALUE;
    private int remainingScanOperations;
    private int discoveryCursor;
    private ItemInsertPlan itemInsertPlan;
    private boolean itemInsertPlanAwaitingExecution;
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
        Arrays.fill(targetCaches, TargetCache.EMPTY);
        Arrays.fill(targetsDirty, true);
        for (Direction direction : Direction.values()) {
            int index = direction.ordinal();
            items[index] = new DistributedItems(direction);
            fluids[index] = new DistributedFluids(direction);
            energy[index] = new DistributedEnergy(direction);
        }
    }

    public void invalidateTargets() {
        Arrays.fill(targetsDirty, true);
        Arrays.fill(targetDiscoveries, null);
        clearTransientCaches();
    }

    public void refreshTargets() {
        if (level == null) return;
        invalidateTargets();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof NetworkEndpointBlockEntity) {
                refreshTargets(direction);
            }
        }
    }

    private void refreshTargets(Direction side) {
        int index = side.ordinal();
        TargetCache cache = discoverTargets(side);
        if (cache == null) {
            nextRescan[index] = level.getGameTime() + 1L;
            return;
        }
        boolean resetScan = selectedSide != side || !cache.equals(targetCaches[index]);
        targetCaches[index] = cache;
        targetsDirty[index] = false;
        nextRescan[index] = level.getGameTime() + RESCAN_INTERVAL;
        selectedSide = side;
        highlightSnapshot = createTargetSnapshot(cache);
        if (resetScan) clearSelectedSideCaches();
    }

    private TargetCache targets(Direction side) {
        if (level == null) return TargetCache.EMPTY;
        selectSide(side);
        int index = side.ordinal();
        long now = level.getGameTime();
        if (targetsDirty[index] || now >= nextRescan[index]) {
            refreshTargets(side);
        }
        return targetCaches[index];
    }

    private TargetCache discoverTargets(Direction inheritedSide) {
        int maxTargets = SkyLogisticsConfig.distributorMaxTargets();
        int index = inheritedSide.ordinal();
        DiscoveryState scan = targetDiscoveries[index];
        if (scan == null || scan.maxTargets != maxTargets) {
            scan = new DiscoveryState(maxTargets);
            for (Direction direction : Direction.values()) scan.queue.add(worldPosition.relative(direction));
            targetDiscoveries[index] = scan;
        }
        while (!scan.queue.isEmpty() && scan.found < maxTargets) {
            if (!takeScanOperation()) return null;
            BlockPos pos = scan.queue.removeFirst();
            if (!scan.visited.add(pos) || scan.discovered.contains(pos) || !level.hasChunkAt(pos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || blockEntity instanceof SkyDistributorBlockEntity) continue;
            Target target = inspect(pos, inheritedSide);
            if (!target.usable()) continue;
            scan.discovered.add(pos);
            scan.found++;
            if (target.items) scan.itemTargets.add(target);
            if (target.fluids) scan.fluidTargets.add(target);
            if (target.energy) scan.energyTargets.add(target);
            for (Direction direction : Direction.values())
                scan.queue.addLast(pos.relative(direction));
        }
        targetDiscoveries[index] = null;
        return new TargetCache(List.copyOf(scan.itemTargets), List.copyOf(scan.fluidTargets),
                List.copyOf(scan.energyTargets));
    }

    private Target inspect(BlockPos pos, Direction accessSide) {
        Direction itemSide = SkyLogisticsConfig.enableDistributorItems()
                ? usableItems(capability(pos, accessSide, ForgeCapabilities.ITEM_HANDLER)) ? accessSide : null : null;
        Direction fluidSide = SkyLogisticsConfig.enableDistributorFluids()
                ? usableFluids(capability(pos, accessSide, ForgeCapabilities.FLUID_HANDLER)) ? accessSide : null : null;
        Direction energySide = SkyLogisticsConfig.enableDistributorEnergy()
                ? usableEnergy(capability(pos, accessSide, ForgeCapabilities.ENERGY)) ? accessSide : null : null;
        return new Target(pos.immutable(), accessSide,
                itemSide != null, fluidSide != null, energySide != null);
    }

    private <T> T capability(BlockPos pos, Direction side, Capability<T> capability) {
        BlockEntity target = level.getBlockEntity(pos);
        return target == null ? null : target.getCapability(capability, side).orElse(null);
    }

    private static boolean usableItems(IItemHandler handler) { return handler != null && handler.getSlots() > 0; }
    private static boolean usableFluids(IFluidHandler handler) { return handler != null && handler.getTanks() > 0; }
    private static boolean usableEnergy(IEnergyStorage storage) {
        return storage != null && (storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive());
    }

    private IItemHandler item(Target target) {
        if (!target.items) return null;
        return capability(target.pos, target.accessSide, ForgeCapabilities.ITEM_HANDLER);
    }

    private IFluidHandler fluid(Target target) {
        if (!target.fluids) return null;
        return capability(target.pos, target.accessSide, ForgeCapabilities.FLUID_HANDLER);
    }

    private IEnergyStorage energy(Target target) {
        if (!target.energy) return null;
        return capability(target.pos, target.accessSide, ForgeCapabilities.ENERGY);
    }

    public IItemHandler itemHandler(Direction side) { return SkyLogisticsConfig.enableDistributorItems() ? items[side.ordinal()] : null; }
    public IFluidHandler fluidHandler(Direction side) { return SkyLogisticsConfig.enableDistributorFluids() ? fluids[side.ordinal()] : null; }
    public IEnergyStorage energyHandler(Direction side) { return SkyLogisticsConfig.enableDistributorEnergy() ? energy[side.ordinal()] : null; }
    public boolean hasItemTargets(Direction side) {
        return SkyLogisticsConfig.enableDistributorItems()
                && (!targets(side).items.isEmpty() || targetDiscoveries[side.ordinal()] != null);
    }
    public boolean hasFluidTargets(Direction side) {
        return SkyLogisticsConfig.enableDistributorFluids()
                && (!targets(side).fluids.isEmpty() || targetDiscoveries[side.ordinal()] != null);
    }
    public boolean hasEnergyTargets(Direction side) {
        return SkyLogisticsConfig.enableDistributorEnergy()
                && (!targets(side).energy.isEmpty() || targetDiscoveries[side.ordinal()] != null);
    }
    public List<TargetSnapshot> targetSnapshot() {
        TargetCache cache = targets(selectedSide);
        highlightSnapshot = createTargetSnapshot(cache);
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

    private long gameTime() { return level == null ? Long.MIN_VALUE : level.getGameTime(); }

    private void selectSide(Direction side) {
        if (selectedSide == side) return;
        selectedSide = side;
        clearSelectedSideCaches();
    }

    private void clearSelectedSideCaches() {
        Arrays.fill(visibleItemSlots, -2);
        Arrays.fill(visibleFluidTanks, -2);
        Arrays.fill(visibleFluids, null);
        Arrays.fill(rejectedItems, null);
        Arrays.fill(rejectedItemUntil, 0L);
        Arrays.fill(rejectedFluids, null);
        Arrays.fill(rejectedFluidUntil, 0L);
        itemInsertPlan = null;
        itemInsertPlanAwaitingExecution = false;
        itemExtractPlan = null;
        fluidInsertPlan = null;
        fluidDrainPlan = null;
        energyReceivePlan = null;
        energyExtractPlan = null;
        energySnapshotTick = Long.MIN_VALUE;
        itemInsertCursor = 0;
        fluidInsertCursor = 0;
        energyReceiveCursor = 0;
        energyExtractCursor = 0;
        energySnapshotCursor = 0;
        energySnapshotScanned = 0;
        energyStoredAccumulator = 0L;
        energyCapacityAccumulator = 0L;
        energyCanExtractAccumulator = false;
        energyCanReceiveAccumulator = false;
    }

    private void prepareOperationBudget() {
        long now = gameTime();
        if (operationBudgetTick == now) return;
        operationBudgetTick = now;
        remainingOperations = SkyLogisticsConfig.distributorOpsPerTick();
        Arrays.fill(visibleItemSlots, -2);
        Arrays.fill(visibleFluidTanks, -2);
        Arrays.fill(visibleFluids, null);
        itemInsertPlan = null;
        itemInsertPlanAwaitingExecution = false;
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

    private boolean takeScanOperation() {
        long now = gameTime();
        if (scanBudgetTick != now) {
            scanBudgetTick = now;
            remainingScanOperations = SkyLogisticsConfig.distributorScanOpsPerTick();
        }
        if (remainingScanOperations <= 0) return false;
        remainingScanOperations--;
        return true;
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            SkyDistributorBlockEntity distributor) {
        if (level.isClientSide) return;
        distributor.continueDiscovery();
    }

    private void continueDiscovery() {
        for (int offset = 0; offset < DIRECTIONS.length; offset++) {
            int index = Math.floorMod(discoveryCursor + offset, DIRECTIONS.length);
            if (targetDiscoveries[index] == null) continue;
            discoveryCursor = (index + 1) % DIRECTIONS.length;
            refreshTargets(DIRECTIONS[index]);
            return;
        }
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
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameTags(first, second);
    }

    private static boolean sameItemType(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameTags(first, second);
    }

    private static boolean sameFluid(FluidStack first, FluidStack second) {
        return first.getAmount() == second.getAmount() && first.isFluidEqual(second);
    }

    private static boolean sameFluidType(FluidStack first, FluidStack second) { return first.isFluidEqual(second); }

    private record Target(BlockPos pos, Direction accessSide,
            boolean items, boolean fluids, boolean energy) {
        boolean usable() { return items || fluids || energy; }
    }

    private record TargetCache(List<Target> items, List<Target> fluids, List<Target> energy) {
        private static final TargetCache EMPTY = new TargetCache(List.of(), List.of(), List.of());
    }

    private static final class DiscoveryState {
        private final int maxTargets;
        private final List<Target> itemTargets;
        private final List<Target> fluidTargets;
        private final List<Target> energyTargets;
        private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        private final Set<BlockPos> visited = new HashSet<>();
        private final Set<BlockPos> discovered = new HashSet<>();
        private int found;

        private DiscoveryState(int maxTargets) {
            this.maxTargets = maxTargets;
            itemTargets = new ArrayList<>(maxTargets);
            fluidTargets = new ArrayList<>(maxTargets);
            energyTargets = new ArrayList<>(maxTargets);
        }
    }

    private record ItemMove(int target, int slot, int amount) {}
    private record ItemInsertPlan(long tick, ItemStack request, List<ItemMove> moves, int accepted) {}
    private record ItemExtractPlan(long tick, int target, int amount, int slot) {}
    private record FluidMove(int target, int amount) {}
    private record FluidInsertPlan(long tick, FluidStack request, List<FluidMove> moves, int accepted) {}
    private record FluidDrainPlan(long tick, FluidStack request, List<FluidMove> moves, int extracted) {}
    private record EnergyPlan(long tick, int request, List<FluidMove> moves, int accepted) {}

    private final class DistributedItems implements IItemHandler {
        private final Direction side;

        private DistributedItems(Direction side) { this.side = side; }

        @Override public int getSlots() {
            if (!SkyLogisticsConfig.enableDistributorItems()) return 0;
            return targets(side).items.size();
        }
        @Override public ItemStack getStackInSlot(int slot) {
            IItemHandler handler = handler(slot);
            if (handler == null || handler.getSlots() == 0) return ItemStack.EMPTY;
            int sourceSlot = visibleSlot(slot, handler);
            return sourceSlot < 0 ? ItemStack.EMPTY : handler.getStackInSlot(sourceSlot);
        }
        @Override public ItemStack insertItem(int ignored, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemInsertPlan plan = matchingItemInsertPlan(stack, simulate);
            if (plan == null) plan = buildItemInsertPlan(stack);
            ItemStack remaining = stack.copy();
            if (simulate) {
                itemInsertPlanAwaitingExecution = true;
                remaining.shrink(plan.accepted);
                return remaining;
            }
            itemInsertPlanAwaitingExecution = false;
            int inserted = executeItemInsertPlan(plan, stack);
            remaining.shrink(inserted);
            itemInsertPlan = null;
            return remaining;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler handler = handler(slot);
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
        private IItemHandler handler(int slot) {
            List<Target> all = targets(side).items;
            return slot < 0 || slot >= all.size() ? null : item(all.get(slot));
        }
        private int visibleSlot(int target, IItemHandler handler) {
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
                itemExtractCursors[target] = sourceSlot + 1;
            }
            visibleItemSlots[target] = -1;
            return -1;
        }

        private ItemInsertPlan matchingItemInsertPlan(ItemStack stack, boolean simulate) {
            prepareOperationBudget();
            if (itemInsertPlan == null || itemInsertPlan.tick != gameTime()
                    || !sameItemType(itemInsertPlan.request, stack)) return null;
            if (simulate) return sameItem(itemInsertPlan.request, stack) ? itemInsertPlan : null;
            return itemInsertPlanAwaitingExecution && stack.getCount() <= itemInsertPlan.accepted
                    ? itemInsertPlan : null;
        }

        private ItemInsertPlan buildItemInsertPlan(ItemStack stack) {
            List<Target> all = targets(side).items;
            List<ItemMove> moves = new ArrayList<>();
            if (!all.isEmpty()) {
                int start = Math.floorMod(itemInsertCursor, all.size());
                int candidateLimit = Math.min(stack.getCount(), all.size());
                int share = (int)(((long)stack.getCount() + all.size() - 1L) / all.size());
                for (int offset = 0; offset < all.size() && moves.size() < candidateLimit; offset++) {
                    int target = (start + offset) % all.size();
                    if (!takeOperation()) break;
                    itemInsertCursor = target + 1;
                    if (isRejected(target, stack)) continue;
                    IItemHandler handler = item(all.get(target));
                    if (handler == null || handler.getSlots() == 0) continue;
                    int slots = handler.getSlots();
                    int slotStart = Math.floorMod(itemInsertSlotCursors[target], slots);
                    boolean fullyScanned = true;
                    for (int checked = 0; checked < slots; checked++) {
                        if (!takeOperation()) {
                            itemInsertCursor = target;
                            fullyScanned = false;
                            break;
                        }
                        int slot = (slotStart + checked) % slots;
                        ItemStack offer = stack.copy(); offer.setCount(share);
                        ItemStack rejected = handler.insertItem(slot, offer, true);
                        int accepted = offer.getCount() - rejected.getCount();
                        if (accepted > 0) { moves.add(new ItemMove(target, slot, accepted)); break; }
                        itemInsertSlotCursors[target] = slot + 1;
                    }
                    if (fullyScanned && (moves.isEmpty() || moves.get(moves.size() - 1).target != target)) {
                        rejectedItems[target] = stack.copy();
                        rejectedItems[target].setCount(1);
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
                    && ItemStack.isSameItemSameTags(rejected, stack);
        }

        private int executeItemInsertPlan(ItemInsertPlan plan, ItemStack request) {
            List<Target> all = targets(side).items;
            int inserted = 0;
            for (ItemMove move : plan.moves) {
                if (move.target >= all.size() || inserted >= request.getCount()) break;
                IItemHandler handler = item(all.get(move.target));
                if (handler == null || move.slot >= handler.getSlots()) continue;
                ItemStack offer = request.copy();
                offer.setCount(Math.min(move.amount, request.getCount() - inserted));
                ItemStack rejected = handler.insertItem(move.slot, offer, false);
                int moved = offer.getCount() - rejected.getCount();
                if (moved > 0) {
                    inserted += moved;
                    itemInsertSlotCursors[move.target] = rejected.isEmpty() ? move.slot : move.slot + 1;
                    visibleItemSlots[move.target] = -2;
                    rejectedItems[move.target] = null;
                } else {
                    itemInsertSlotCursors[move.target] = move.slot + 1;
                }
            }
            return inserted;
        }
    }

    private final class DistributedFluids implements IFluidHandler {
        private final Direction side;

        private DistributedFluids(Direction side) { this.side = side; }

        @Override public int getTanks() {
            if (!SkyLogisticsConfig.enableDistributorFluids()) return 0;
            return targets(side).fluids.size();
        }
        @Override public FluidStack getFluidInTank(int tank) {
            IFluidHandler handler = handler(tank);
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
                fluidExtractCursors[tank] = sourceTank + 1;
            }
            visibleFluidTanks[tank] = -1;
            return FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { IFluidHandler h = handler(tank); return h == null ? 0 : Integer.MAX_VALUE; }
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
        private IFluidHandler handler(int tank) { List<Target> all = targets(side).fluids; return tank < 0 || tank >= all.size() ? null : fluid(all.get(tank)); }

        private FluidInsertPlan buildFluidInsertPlan(FluidStack resource) {
            List<Target> all = targets(side).fluids;
            List<FluidMove> moves = new ArrayList<>();
            if (!all.isEmpty()) {
                int start = Math.floorMod(fluidInsertCursor, all.size());
                int candidateLimit = Math.min(resource.getAmount(), all.size());
                int share = (int)(((long)resource.getAmount() + all.size() - 1L) / all.size());
                for (int offset = 0; offset < all.size() && moves.size() < candidateLimit; offset++) {
                    int target = (start + offset) % all.size();
                    if (!takeOperation()) break;
                    fluidInsertCursor = target + 1;
                    if (rejectedFluids[target] != null && gameTime() < rejectedFluidUntil[target]
                            && sameFluidType(rejectedFluids[target], resource)) continue;
                    IFluidHandler handler = fluid(all.get(target));
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
            List<Target> all = targets(side).fluids;
            int inserted = 0;
            for (FluidMove move : plan.moves) {
                if (move.target >= all.size() || inserted >= resource.getAmount()) break;
                IFluidHandler handler = fluid(all.get(move.target));
                if (handler == null) continue;
                FluidStack offer = resource.copy();
                offer.setAmount(Math.min(move.amount, resource.getAmount() - inserted));
                int moved = handler.fill(offer, FluidAction.EXECUTE);
                inserted += moved;
                if (moved > 0) rejectedFluids[move.target] = null;
            }
            return inserted;
        }

        private FluidDrainPlan buildFluidDrainPlan(FluidStack resource) {
            List<Target> all = targets(side).fluids;
            List<FluidMove> moves = new ArrayList<>();
            int remaining = resource.getAmount();
            int start = all.isEmpty() ? 0 : Math.floorMod(fluidInsertCursor, all.size());
            for (int offset = 0; offset < all.size() && remaining > 0; offset++) {
                if (!takeOperation()) break;
                int target = (start + offset) % all.size();
                fluidInsertCursor = target + 1;
                IFluidHandler handler = fluid(all.get(target));
                if (handler == null) continue;
                FluidStack request = resource.copy(); request.setAmount(remaining);
                FluidStack extracted = handler.drain(request, FluidAction.SIMULATE);
                if (!extracted.isEmpty()) { moves.add(new FluidMove(target, extracted.getAmount())); remaining -= extracted.getAmount(); }
            }
            fluidDrainPlan = new FluidDrainPlan(gameTime(), resource.copy(), List.copyOf(moves), resource.getAmount() - remaining);
            return fluidDrainPlan;
        }

        private FluidStack executeFluidDrainPlan(FluidDrainPlan plan, FluidStack resource) {
            List<Target> all = targets(side).fluids;
            FluidStack result = FluidStack.EMPTY;
            for (FluidMove move : plan.moves) {
                if (move.target >= all.size()) continue;
                IFluidHandler handler = fluid(all.get(move.target));
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
            return result;
        }
    }

    private final class DistributedEnergy implements IEnergyStorage {
        private final Direction side;

        private DistributedEnergy(Direction side) { this.side = side; }

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
            List<Target> all = targets(side).energy;
            List<FluidMove> moves = new ArrayList<>();
            if (!all.isEmpty() && amount > 0) {
                int cursor = receive ? energyReceiveCursor : energyExtractCursor;
                int start = Math.floorMod(cursor, all.size());
                int candidateLimit = Math.min(amount, all.size());
                int share = (int)(((long)amount + all.size() - 1L) / all.size());
                for (int offset = 0; offset < all.size() && moves.size() < candidateLimit; offset++) {
                    if (!takeOperation()) break;
                    int target = (start + offset) % all.size();
                    if (receive) energyReceiveCursor = target + 1;
                    else energyExtractCursor = target + 1;
                    IEnergyStorage handler = energy(all.get(target));
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
            List<Target> all = targets(side).energy;
            int moved = 0;
            for (FluidMove move : plan.moves) {
                if (move.target >= all.size() || moved >= amount) break;
                IEnergyStorage handler = energy(all.get(move.target));
                if (handler == null) continue;
                int offer = Math.min(move.amount, amount - moved);
                moved += receive ? handler.receiveEnergy(offer, false) : handler.extractEnergy(offer, false);
            }
            energySnapshotTick = Long.MIN_VALUE;
            return moved;
        }

        private void refreshEnergySnapshot() {
            long now = gameTime();
            if (energySnapshotTick == now) return;
            List<Target> all = targets(side).energy;
            while (energySnapshotScanned < all.size()) {
                if (!takeOperation()) break;
                Target target = all.get(Math.floorMod(energySnapshotCursor, all.size()));
                energySnapshotCursor++;
                energySnapshotScanned++;
                IEnergyStorage handler = energy(target);
                if (handler == null) continue;
                energyStoredAccumulator += handler.getEnergyStored();
                energyCapacityAccumulator += handler.getMaxEnergyStored();
                energyCanExtractAccumulator |= handler.canExtract();
                energyCanReceiveAccumulator |= handler.canReceive();
            }
            if (energySnapshotScanned >= all.size()) {
                energyStoredSnapshot = (int)Math.min(Integer.MAX_VALUE, energyStoredAccumulator);
                energyCapacitySnapshot = (int)Math.min(Integer.MAX_VALUE, energyCapacityAccumulator);
                energyCanExtractSnapshot = energyCanExtractAccumulator;
                energyCanReceiveSnapshot = energyCanReceiveAccumulator;
                resetEnergySnapshotScan();
            }
            energySnapshotTick = now;
        }

        private void resetEnergySnapshotScan() {
            energySnapshotCursor = 0;
            energySnapshotScanned = 0;
            energyStoredAccumulator = 0L;
            energyCapacityAccumulator = 0L;
            energyCanExtractAccumulator = false;
            energyCanReceiveAccumulator = false;
        }
    }
}
