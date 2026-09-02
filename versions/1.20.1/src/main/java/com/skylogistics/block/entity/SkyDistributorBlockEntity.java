package com.skylogistics.block.entity;

import com.skylogistics.block.SkyDistributorBlock;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.distributor.DistributedChemicalHandler;
import com.skylogistics.compat.distributor.AdaptiveRoutingConfig;
import com.skylogistics.compat.distributor.AdaptiveTargetProbeScheduler;
import com.skylogistics.compat.distributor.DistributedHandlerLookup;
import com.skylogistics.compat.distributor.DistributorInsertMode;
import com.skylogistics.compat.distributor.DistributorIndexPolicy;
import com.skylogistics.compat.distributor.DistributedManaHandler;
import com.skylogistics.compat.distributor.DistributedSourceHandler;
import com.skylogistics.compat.distributor.BudgetedDistributorHandler;
import com.skylogistics.compat.distributor.ConstrainedDistributorItemHandler;
import com.skylogistics.compat.distributor.ConstrainedDistributorEnergyHandler;
import com.skylogistics.compat.distributor.ConstrainedDistributorFluidHandler;
import com.skylogistics.compat.distributor.DistributorEnergyMaintenancePolicy;
import com.skylogistics.compat.distributor.DistributorResourceMaintenancePolicy;
import com.skylogistics.compat.distributor.DistributorItemInsertContext;
import com.skylogistics.compat.distributor.DistributorMaintenancePolicy;
import com.skylogistics.compat.distributor.DistributorOperationBudget;
import com.skylogistics.compat.distributor.DistributorRescanPolicy;
import com.skylogistics.compat.distributor.DistributedSlotMap;
import com.skylogistics.compat.distributor.DistributedTargetProbeScheduler;
import com.skylogistics.compat.distributor.HierarchicalTargetRouteCache;
import com.skylogistics.compat.distributor.MaintainedStorageView;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.storage.ItemStackKey;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.util.DistributorPushDirection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
    private final DistributedChemicalHandler[] chemicals = new DistributedChemicalHandler[DIRECTIONS.length];
    private final DistributedManaHandler[] mana = new DistributedManaHandler[DIRECTIONS.length];
    private final DistributedSourceHandler[] source = new DistributedSourceHandler[DIRECTIONS.length];
    private final TargetCache[] targetCaches = new TargetCache[DIRECTIONS.length];
    private final boolean[] completeTargetIndexes = new boolean[DIRECTIONS.length];
    private final DiscoveryState[] targetDiscoveries = new DiscoveryState[DIRECTIONS.length];
    private final boolean[] activeTargetSides = new boolean[DIRECTIONS.length];
    private List<TargetSnapshot> highlightSnapshot = List.of();
    private final boolean[] targetsDirty = new boolean[DIRECTIONS.length];
    private final long[] nextRescan = new long[DIRECTIONS.length];
    private Direction selectedSide = Direction.NORTH;
    private final int[] itemInsertCursors = new int[DIRECTIONS.length];
    private final int[] fluidInsertCursors = new int[DIRECTIONS.length];
    private final int[] fluidDrainCursors = new int[DIRECTIONS.length];
    private final int[] energyReceiveCursors = new int[DIRECTIONS.length];
    private final int[] energyExtractCursors = new int[DIRECTIONS.length];
    private int energySnapshotCursor;
    private int energySnapshotScanned;
    private long energyStoredAccumulator;
    private long energyCapacityAccumulator;
    private int energyOccupiedAccumulator;
    private long energyExistingRefillAccumulator;
    private boolean energyCanExtractAccumulator;
    private boolean energyCanReceiveAccumulator;
    private final int[] fluidExtractCursors = new int[MAX_CONFIGURABLE_TARGETS];
    private final int[] itemInsertSlotCursors = new int[MAX_CONFIGURABLE_TARGETS];
    private final int[] visibleFluidTanks = new int[MAX_CONFIGURABLE_TARGETS];
    private final FluidStack[] visibleFluids = new FluidStack[MAX_CONFIGURABLE_TARGETS];
    private final ItemStack[] rejectedItems = new ItemStack[MAX_CONFIGURABLE_TARGETS];
    private final long[] rejectedItemUntil = new long[MAX_CONFIGURABLE_TARGETS];
    private final FluidStack[] rejectedFluids = new FluidStack[MAX_CONFIGURABLE_TARGETS];
    private final long[] rejectedFluidUntil = new long[MAX_CONFIGURABLE_TARGETS];
    private long operationStateTick = Long.MIN_VALUE;
    private long scanBudgetTick = Long.MIN_VALUE;
    private int remainingScanOperations;
    private int discoveryCursor;
    private ItemInsertPlan itemInsertPlan;
    private boolean itemInsertPlanAwaitingExecution;
    private ItemExtractPlan itemExtractPlan;
    private FluidInsertPlan fluidInsertPlan;
    private boolean fluidInsertPlanAwaitingExecution;
    private FluidDrainPlan fluidDrainPlan;
    private EnergyPlan energyReceivePlan;
    private EnergyPlan energyExtractPlan;
    private long energySnapshotTick = Long.MIN_VALUE;
    private int energyStoredSnapshot;
    private int energyOccupiedSnapshot;
    private long energyExistingRefillSnapshot;
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
            chemicals[index] = new DistributedChemicalHandler(new ChemicalLookup(direction));
            mana[index] = new DistributedManaHandler(new ManaLookup(direction));
            source[index] = new DistributedSourceHandler(new SourceLookup(direction));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) return;
        invalidateTargets();
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = worldPosition.relative(direction);
            if (serverLevel.getBlockEntity(neighbor) instanceof NetworkEndpointBlockEntity) {
                activeTargetSides[direction.ordinal()] = true;
                SkyNetworkRegistry.register(serverLevel, neighbor);
            }
        }
    }

    public void invalidateTargets() {
        Arrays.fill(targetsDirty, true);
        Arrays.fill(targetDiscoveries, null);
        clearTransientCaches();
    }

    public void abandonTargets() {
        Arrays.fill(targetCaches, TargetCache.EMPTY);
        Arrays.fill(completeTargetIndexes, false);
        highlightSnapshot = List.of();
        invalidateTargets();
        for (Direction direction : DIRECTIONS) {
            if (activeTargetSides[direction.ordinal()]) wakeAdjacentEndpoint(direction);
        }
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
        activeTargetSides[index] = true;
        TargetCache cache = discoverTargets(side);
        if (cache == null) {
            nextRescan[index] = level.getGameTime() + 1L;
            return;
        }
        TargetCache previous = targetCaches[index];
        boolean topologyChanged = !cache.equals(previous);
        boolean resetScan = topologyChanged && selectedSide == side;
        if (topologyChanged) {
            items[index].remapAdaptiveState(previous.itemSlots, cache.itemSlots,
                    targetRemap(previous.items, cache.items));
            fluids[index].remapAdaptiveState(targetRemap(previous.fluids, cache.fluids));
            chemicals[index].remapAdaptiveState(targetRemap(previous.chemicals, cache.chemicals));
        }
        targetCaches[index] = cache;
        completeTargetIndexes[index] = true;
        targetsDirty[index] = false;
        nextRescan[index] = level.getGameTime() + RESCAN_INTERVAL;
        highlightSnapshot = createTargetSnapshot(cache);
        wakeAdjacentEndpoint(side);
        if (resetScan) {
            clearSelectedSideCaches();
        }
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

    private boolean targetIndexUnavailable(Direction side) {
        int index = side.ordinal();
        return DistributorIndexPolicy.transferBlocked(completeTargetIndexes[index], targetsDirty[index],
                targetDiscoveries[index] != null);
    }

    private void wakeAdjacentEndpoint(Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos neighbor = worldPosition.relative(side);
        if (serverLevel.getBlockEntity(neighbor) instanceof NetworkEndpointBlockEntity) {
            SkyNetworkRegistry.markRuntimeDirty(serverLevel, neighbor);
        }
    }

    private TargetCache discoverTargets(Direction inheritedSide) {
        int maxTargets = SkyLogisticsConfig.distributorMaxTargets();
        int index = inheritedSide.ordinal();
        DistributorPushDirection pushDirection = pushDirection();
        DiscoveryState scan = targetDiscoveries[index];
        if (scan == null || scan.maxTargets != maxTargets || scan.pushDirection != pushDirection) {
            scan = new DiscoveryState(maxTargets, pushDirection);
            scan.visited.add(worldPosition);
            for (Direction direction : pushDirection.scanDirections()) {
                scan.queue.add(worldPosition.relative(direction));
            }
            targetDiscoveries[index] = scan;
        }
        while (!scan.queue.isEmpty() && scan.found < maxTargets) {
            if (!takeScanOperation()) return null;
            BlockPos pos = scan.queue.removeFirst();
            if (!scan.visited.add(pos) || scan.discovered.contains(pos) || !level.hasChunkAt(pos)) continue;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) continue;
            if (blockEntity instanceof SkyDistributorBlockEntity) {
                if (!scan.pushDirection.directional()) continue;
                scan.discovered.add(pos);
                scan.found++;
                for (Direction direction : scan.pushDirection.scanDirections()) {
                    scan.queue.addLast(pos.relative(direction));
                }
                continue;
            }
            Target target = inspect(pos, inheritedSide);
            if (!target.usable()) continue;
            scan.discovered.add(pos);
            scan.found++;
            if (target.items) scan.itemTargets.add(target);
            if (target.fluids) scan.fluidTargets.add(target);
            if (target.chemical) scan.chemicalTargets.add(target);
            if (target.energy) scan.energyTargets.add(target);
            if (target.mana) scan.manaTargets.add(target);
            if (target.source) scan.sourceTargets.add(target);
            for (Direction direction : scan.pushDirection.scanDirections())
                scan.queue.addLast(pos.relative(direction));
        }
        targetDiscoveries[index] = null;
        List<Target> itemTargets = List.copyOf(scan.itemTargets);
        return new TargetCache(itemTargets, DistributedSlotMap.create(itemTargets, Target::itemSlots),
                List.copyOf(scan.fluidTargets),
                List.copyOf(scan.chemicalTargets), List.copyOf(scan.energyTargets),
                List.copyOf(scan.manaTargets), List.copyOf(scan.sourceTargets), scan.found);
    }

    private DistributorPushDirection pushDirection() {
        BlockState state = getBlockState();
        return state.hasProperty(SkyDistributorBlock.PUSH_DIRECTION)
                ? state.getValue(SkyDistributorBlock.PUSH_DIRECTION)
                : DistributorPushDirection.ALL;
    }

    private Target inspect(BlockPos pos, Direction accessSide) {
        IItemHandler itemHandler = SkyLogisticsConfig.enableDistributorItems()
                ? capability(pos, accessSide, ForgeCapabilities.ITEM_HANDLER) : null;
        int itemSlots = itemHandler == null ? 0 : Math.max(0, itemHandler.getSlots());
        Direction itemSide = itemSlots > 0 ? accessSide : null;
        Direction fluidSide = SkyLogisticsConfig.enableDistributorFluids()
                ? usableFluids(capability(pos, accessSide, ForgeCapabilities.FLUID_HANDLER)) ? accessSide : null : null;
        Direction energySide = SkyLogisticsConfig.enableDistributorEnergy()
                ? usableEnergy(capability(pos, accessSide, ForgeCapabilities.ENERGY)) ? accessSide : null : null;
        boolean chemical = SkyLogisticsConfig.enableDistributorFluids()
                && SkyLogisticsConfig.allowFluidChemicalTransfer()
                && usableChemical(MekanismCompat.chemicalHandler(level, pos, accessSide));
        boolean mana = SkyLogisticsConfig.enableDistributorEnergy()
                && SkyLogisticsConfig.allowEnergyManaTransfer()
                && usableMana(BotaniaCompat.manaHandler(level, pos, accessSide));
        boolean source = SkyLogisticsConfig.enableDistributorEnergy()
                && SkyLogisticsConfig.allowEnergySourceTransfer()
                && usableSource(ArsNouveauCompat.sourceHandler(level, pos, accessSide));
        return new Target(pos.immutable(), accessSide,
                itemSide != null, itemSlots, fluidSide != null, chemical, energySide != null, mana, source);
    }

    private <T> T capability(BlockPos pos, Direction side, Capability<T> capability) {
        BlockEntity target = level.getBlockEntity(pos);
        return target == null ? null : target.getCapability(capability, side).orElse(null);
    }

    private static boolean usableFluids(IFluidHandler handler) { return handler != null && handler.getTanks() > 0; }
    private static boolean usableEnergy(IEnergyStorage storage) {
        return storage != null && (storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive());
    }
    private static boolean usableChemical(ChemicalHandlerBridge handler) {
        return handler != null && handler.getTanks() > 0;
    }
    private static boolean usableMana(ManaHandlerBridge handler) {
        return handler != null && (handler.getMaxMana() > 0 || handler.canExtract() || handler.canReceive());
    }
    private static boolean usableSource(SourceHandlerBridge handler) {
        return handler != null && (handler.getMaxSource() > 0 || handler.canExtract() || handler.canReceive());
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

    private ChemicalHandlerBridge chemical(Target target) {
        return target.chemical ? MekanismCompat.chemicalHandler(level, target.pos, target.accessSide) : null;
    }

    private ManaHandlerBridge mana(Target target) {
        return target.mana ? BotaniaCompat.manaHandler(level, target.pos, target.accessSide) : null;
    }

    private SourceHandlerBridge source(Target target) {
        return target.source ? ArsNouveauCompat.sourceHandler(level, target.pos, target.accessSide) : null;
    }

    public IItemHandler itemHandler(Direction side) { return SkyLogisticsConfig.enableDistributorItems() ? items[side.ordinal()] : null; }
    public IFluidHandler fluidHandler(Direction side) { return SkyLogisticsConfig.enableDistributorFluids() ? fluids[side.ordinal()] : null; }
    public IEnergyStorage energyHandler(Direction side) { return SkyLogisticsConfig.enableDistributorEnergy() ? energy[side.ordinal()] : null; }
    public ChemicalHandlerBridge chemicalHandler(Direction side) {
        return SkyLogisticsConfig.enableDistributorFluids() && SkyLogisticsConfig.allowFluidChemicalTransfer()
                ? chemicals[side.ordinal()] : null;
    }
    public ManaHandlerBridge manaHandler(Direction side) {
        return SkyLogisticsConfig.enableDistributorEnergy() && SkyLogisticsConfig.allowEnergyManaTransfer()
                ? mana[side.ordinal()] : null;
    }
    public SourceHandlerBridge sourceHandler(Direction side) {
        return SkyLogisticsConfig.enableDistributorEnergy() && SkyLogisticsConfig.allowEnergySourceTransfer()
                ? source[side.ordinal()] : null;
    }
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
    public boolean hasChemicalTargets(Direction side) {
        return SkyLogisticsConfig.enableDistributorFluids() && SkyLogisticsConfig.allowFluidChemicalTransfer()
                && (!targets(side).chemicals.isEmpty() || targetDiscoveries[side.ordinal()] != null);
    }
    public boolean hasManaTargets(Direction side) {
        return SkyLogisticsConfig.enableDistributorEnergy() && SkyLogisticsConfig.allowEnergyManaTransfer()
                && (!targets(side).mana.isEmpty() || targetDiscoveries[side.ordinal()] != null);
    }
    public boolean hasSourceTargets(Direction side) {
        return SkyLogisticsConfig.enableDistributorEnergy() && SkyLogisticsConfig.allowEnergySourceTransfer()
                && (!targets(side).source.isEmpty() || targetDiscoveries[side.ordinal()] != null);
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
        cache.chemicals.forEach(target -> masks.merge(target.pos, 2, (left, right) -> left | right));
        cache.energy.forEach(target -> masks.merge(target.pos, 4, (left, right) -> left | right));
        cache.mana.forEach(target -> masks.merge(target.pos, 4, (left, right) -> left | right));
        cache.source.forEach(target -> masks.merge(target.pos, 4, (left, right) -> left | right));
        return masks.entrySet().stream().map(entry -> new TargetSnapshot(entry.getKey(), entry.getValue())).toList();
    }

    public record TargetSnapshot(BlockPos pos, int resourceMask) {}

    public IndexStatus indexStatus() {
        boolean indexing = false;
        int boundDevices = 0;
        for (int index = 0; index < DIRECTIONS.length; index++) {
            if (!activeTargetSides[index]) continue;
            DiscoveryState discovery = targetDiscoveries[index];
            indexing |= targetsDirty[index] || discovery != null;
            boundDevices = Math.max(boundDevices,
                    discovery == null ? targetCaches[index].deviceCount : discovery.found);
        }
        return new IndexStatus(indexing, boundDevices);
    }

    public record IndexStatus(boolean indexing, int boundDevices) {}

    public boolean sequentialInsertion() {
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    private long gameTime() { return level == null ? Long.MIN_VALUE : level.getGameTime(); }

    private AdaptiveRoutingConfig fluidRoutingConfig() {
        return routingConfig(SkyLogisticsConfig.enableDistributorAdaptiveFluidTargetProbes());
    }

    private AdaptiveRoutingConfig chemicalRoutingConfig() {
        return routingConfig(SkyLogisticsConfig.enableDistributorAdaptiveChemicalTargetProbes());
    }

    private AdaptiveRoutingConfig routingConfig(boolean enabled) {
        return new AdaptiveRoutingConfig(enabled, SkyLogisticsConfig.distributorItemRouteCacheSize(),
                SkyLogisticsConfig.distributorItemTargetHotProbeTicks(),
                SkyLogisticsConfig.distributorItemTargetWarmProbeTicks(),
                SkyLogisticsConfig.distributorItemTargetCoolProbeTicks(),
                SkyLogisticsConfig.distributorItemTargetFallbackProbeTicks(),
                SkyLogisticsConfig.distributorItemTargetMissesPerDemotion());
    }

    private void selectSide(Direction side) {
        if (selectedSide == side) return;
        selectedSide = side;
        clearSelectedSideCaches();
    }

    private void clearSelectedSideCaches() {
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
        energySnapshotCursor = 0;
        energySnapshotScanned = 0;
        energyStoredAccumulator = 0L;
        energyCapacityAccumulator = 0L;
        energyOccupiedAccumulator = 0;
        energyExistingRefillAccumulator = 0L;
        energyCanExtractAccumulator = false;
        energyCanReceiveAccumulator = false;
    }

    private void prepareOperationState() {
        long now = gameTime();
        if (operationStateTick == now) return;
        operationStateTick = now;
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
        prepareOperationState();
        return DistributorOperationBudget.takeOperation();
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
        long now = gameTime();
        for (int offset = 0; offset < DIRECTIONS.length; offset++) {
            int index = Math.floorMod(discoveryCursor + offset, DIRECTIONS.length);
            if (!DistributorRescanPolicy.shouldScan(activeTargetSides[index], targetsDirty[index],
                    targetDiscoveries[index] != null, now, nextRescan[index])) continue;
            discoveryCursor = (index + 1) % DIRECTIONS.length;
            refreshTargets(DIRECTIONS[index]);
            return;
        }
    }

    private void clearTransientCaches() {
        operationStateTick = Long.MIN_VALUE;
        energySnapshotTick = Long.MIN_VALUE;
        itemInsertPlan = null;
        itemInsertPlanAwaitingExecution = false;
        itemExtractPlan = null;
        fluidInsertPlan = null;
        fluidDrainPlan = null;
        energyReceivePlan = null;
        energyExtractPlan = null;
        Arrays.fill(rejectedItems, null);
        Arrays.fill(rejectedItemUntil, 0L);
        Arrays.fill(rejectedFluids, null);
        Arrays.fill(rejectedFluidUntil, 0L);
        for (DistributedItems handler : items) if (handler != null) handler.constrainedItemScans.clear();
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
            boolean items, int itemSlots, boolean fluids, boolean chemical, boolean energy, boolean mana, boolean source) {
        boolean usable() { return items || fluids || chemical || energy || mana || source; }
    }

    private static int[] targetRemap(List<Target> previous, List<Target> current) {
        int[] oldIndexForNew = new int[current.size()];
        Arrays.fill(oldIndexForNew, -1);
        for (int target = 0; target < current.size(); target++) {
            Target candidate = current.get(target);
            for (int oldTarget = 0; oldTarget < previous.size(); oldTarget++) {
                Target old = previous.get(oldTarget);
                if (old.pos.equals(candidate.pos) && old.accessSide == candidate.accessSide) {
                    oldIndexForNew[target] = oldTarget;
                    break;
                }
            }
        }
        return oldIndexForNew;
    }

    private static int remappedTarget(int oldTarget, int[] oldIndexForNew) {
        for (int target = 0; target < oldIndexForNew.length; target++) {
            if (oldIndexForNew[target] == oldTarget) return target;
        }
        return -1;
    }

    private record TargetCache(List<Target> items, DistributedSlotMap<Target> itemSlots,
            List<Target> fluids, List<Target> chemicals,
            List<Target> energy, List<Target> mana, List<Target> source, int deviceCount) {
        private static final TargetCache EMPTY = new TargetCache(
                List.of(), DistributedSlotMap.create(List.of(), target -> 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    private static final class DiscoveryState {
        private final int maxTargets;
        private final DistributorPushDirection pushDirection;
        private final List<Target> itemTargets;
        private final List<Target> fluidTargets;
        private final List<Target> chemicalTargets;
        private final List<Target> energyTargets;
        private final List<Target> manaTargets;
        private final List<Target> sourceTargets;
        private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        private final Set<BlockPos> visited = new HashSet<>();
        private final Set<BlockPos> discovered = new HashSet<>();
        private int found;

        private DiscoveryState(int maxTargets, DistributorPushDirection pushDirection) {
            this.maxTargets = maxTargets;
            this.pushDirection = pushDirection;
            itemTargets = new ArrayList<>(maxTargets);
            fluidTargets = new ArrayList<>(maxTargets);
            chemicalTargets = new ArrayList<>(maxTargets);
            energyTargets = new ArrayList<>(maxTargets);
            manaTargets = new ArrayList<>(maxTargets);
            sourceTargets = new ArrayList<>(maxTargets);
        }
    }

    private record ItemMove(int target, int slot, int amount, boolean opensMaintainedSlot) {
        private ItemMove(int target, int slot, int amount) {
            this(target, slot, amount, false);
        }
    }

    private record ItemTargetPlan(int target, List<ItemMove> moves, int matchingSlots, int matchingItems) {}
    private static final class ConstrainedItemScan {
        private final List<ItemTargetPlan> plans = new ArrayList<>();
        private int nextTargetOffset;
        private int currentTarget = -1;
        private int currentSlots;
        private int nextSlot;
        private int fixedSlot;
        private int matchingSlots;
        private int matchingItems;
        private final List<ItemMove> matchingMoves = new ArrayList<>();
        private final List<ItemMove> otherMoves = new ArrayList<>();

        private void beginTarget(int target, int slots, int fixedSlot) {
            currentTarget = target;
            currentSlots = slots;
            nextSlot = 0;
            this.fixedSlot = fixedSlot;
            matchingSlots = 0;
            matchingItems = 0;
            matchingMoves.clear();
            otherMoves.clear();
        }

        private void finishTarget() {
            matchingMoves.addAll(otherMoves);
            plans.add(new ItemTargetPlan(currentTarget, List.copyOf(matchingMoves), matchingSlots, matchingItems));
            currentTarget = -1;
            nextTargetOffset++;
        }
    }
    private record ConstrainedItemScanKey(ItemStackKey item, int count,
            DistributorItemInsertContext context, int targetCount, int start) {}
    private record ItemInsertPlan(long tick, ItemStack request, List<ItemMove> moves, int accepted) {}
    private record ItemExtractPlan(long tick, int virtualSlot, int amount, int physicalSlot) {}
    private record FluidMove(int target, int amount) {}
    private record FluidInsertPlan(long tick, FluidStack request, List<FluidMove> moves, int accepted) {}
    private record FluidDrainPlan(long tick, FluidStack request, List<FluidMove> moves, int extracted) {}
    private record EnergyPlan(long tick, int request, List<FluidMove> moves, int accepted) {}

    private final class ChemicalLookup implements DistributedHandlerLookup<ChemicalHandlerBridge> {
        private final Direction side;
        private ChemicalLookup(Direction side) { this.side = side; }
        @Override public int size() { return targets(side).chemicals.size(); }
        @Override public ChemicalHandlerBridge handler(int index) {
            List<Target> all = targets(side).chemicals;
            return index < 0 || index >= all.size() ? null : chemical(all.get(index));
        }
        @Override public boolean takeOperation() { return SkyDistributorBlockEntity.this.takeOperation(); }
        @Override public boolean sequentialInsertion() { return SkyDistributorBlockEntity.this.sequentialInsertion(); }
        @Override public boolean budgetExhausted() { return DistributorOperationBudget.exhausted(); }
        @Override public boolean scanPending() {
            return targetIndexUnavailable(side);
        }
        @Override public long gameTime() { return SkyDistributorBlockEntity.this.gameTime(); }
        @Override public AdaptiveRoutingConfig adaptiveRoutingConfig() {
            return chemicalRoutingConfig();
        }
    }

    private final class ManaLookup implements DistributedHandlerLookup<ManaHandlerBridge> {
        private final Direction side;
        private ManaLookup(Direction side) { this.side = side; }
        @Override public int size() { return targets(side).mana.size(); }
        @Override public ManaHandlerBridge handler(int index) {
            List<Target> all = targets(side).mana;
            return index < 0 || index >= all.size() ? null : mana(all.get(index));
        }
        @Override public boolean takeOperation() { return SkyDistributorBlockEntity.this.takeOperation(); }
        @Override public boolean sequentialInsertion() { return SkyDistributorBlockEntity.this.sequentialInsertion(); }
        @Override public boolean budgetExhausted() { return DistributorOperationBudget.exhausted(); }
        @Override public boolean scanPending() { return targetIndexUnavailable(side); }
        @Override public long gameTime() { return SkyDistributorBlockEntity.this.gameTime(); }
    }

    private final class SourceLookup implements DistributedHandlerLookup<SourceHandlerBridge> {
        private final Direction side;
        private SourceLookup(Direction side) { this.side = side; }
        @Override public int size() { return targets(side).source.size(); }
        @Override public SourceHandlerBridge handler(int index) {
            List<Target> all = targets(side).source;
            return index < 0 || index >= all.size() ? null : source(all.get(index));
        }
        @Override public boolean takeOperation() { return SkyDistributorBlockEntity.this.takeOperation(); }
        @Override public boolean sequentialInsertion() { return SkyDistributorBlockEntity.this.sequentialInsertion(); }
        @Override public boolean budgetExhausted() { return DistributorOperationBudget.exhausted(); }
        @Override public boolean scanPending() { return targetIndexUnavailable(side); }
        @Override public long gameTime() { return SkyDistributorBlockEntity.this.gameTime(); }
    }

    private final class DistributedItems implements IItemHandler, ConstrainedDistributorItemHandler {
        private final Direction side;
        private final DistributedTargetProbeScheduler<Target> extractionProbes =
                new DistributedTargetProbeScheduler<>();
        private final HierarchicalTargetRouteCache<ItemStackKey> insertionRoutes =
                new HierarchicalTargetRouteCache<>();
        private final int[] insertionRouteCandidates = new int[MAX_CONFIGURABLE_TARGETS];
        private final Map<ConstrainedItemScanKey, ConstrainedItemScan> constrainedItemScans = new LinkedHashMap<>();

        private DistributedItems(Direction side) { this.side = side; }

        @Override public boolean distributorBudgetExhausted() {
            return DistributorOperationBudget.exhausted();
        }

        @Override public boolean distributorScanPending() {
            return targetIndexUnavailable(side);
        }

        @Override public int nextFairExtractionSlot(long gameTime) {
            if (!usesIndependentExtractionProbes()) return -1;
            configureExtractionProbes();
            return extractionProbes.nextDueSlot(targets(side).itemSlots, gameTime);
        }

        @Override public int fairExtractionProbesDue(long gameTime) {
            if (!usesIndependentExtractionProbes()) return 0;
            configureExtractionProbes();
            return extractionProbes.dueProbeCount(targets(side).itemSlots, gameTime);
        }

        @Override public void setMaintainedExtractionPollTicks(int pollTicks) { extractionProbes.setMaximumInterval(pollTicks, gameTime()); insertionRoutes.setMaximumInterval(pollTicks); }

        @Override public boolean usesIndependentExtractionProbes() {
            return SkyLogisticsConfig.enableDistributorAdaptiveItemTargetProbes();
        }

        @Override public int getSlots() {
            if (!SkyLogisticsConfig.enableDistributorItems()) return 0;
            return targets(side).itemSlots.size();
        }
        @Override public ItemStack getStackInSlot(int slot) {
            DistributedSlotMap.Slot<Target> mapped = mappedSlot(slot);
            IItemHandler handler = handler(mapped);
            if (handler == null || !takeOperation()) return ItemStack.EMPTY;
            ItemStack stack = handler.getStackInSlot(mapped.localSlot());
            recordExtractionProbe(slot, stack, true);
            return stack;
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

        @Override public int planItemInsertion(ItemStack stack, DistributorItemInsertContext context,
                Predicate<ItemStack> maintainedMatcher) {
            if (stack.isEmpty()) return 0;
            ItemInsertPlan plan = buildConstrainedItemInsertPlan(stack,
                    context == null ? DistributorItemInsertContext.unrestricted() : context,
                    maintainedMatcher == null ? ignored -> true : maintainedMatcher);
            itemInsertPlanAwaitingExecution = true;
            return plan.accepted;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            DistributedSlotMap.Slot<Target> mapped = mappedSlot(slot);
            IItemHandler handler = handler(mapped);
            if (handler == null || amount <= 0) return ItemStack.EMPTY;
            ItemExtractPlan plan = itemExtractPlan;
            if (plan == null || plan.tick != gameTime() || plan.virtualSlot != slot || plan.amount != amount) {
                if (!takeOperation()) return ItemStack.EMPTY;
                plan = new ItemExtractPlan(gameTime(), slot, amount, mapped.localSlot());
                itemExtractPlan = plan;
            }
            ItemStack extracted = handler.extractItem(plan.physicalSlot, amount, simulate);
            recordExtractionProbe(slot, extracted, simulate);
            if (!simulate) {
                itemExtractPlan = null;
            }
            return extracted;
        }
        @Override public int getSlotLimit(int slot) {
            DistributedSlotMap.Slot<Target> mapped = mappedSlot(slot);
            IItemHandler handler = handler(mapped);
            return handler == null ? 0 : handler.getSlotLimit(mapped.localSlot());
        }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
        private DistributedSlotMap.Slot<Target> mappedSlot(int slot) {
            return targets(side).itemSlots.resolve(slot);
        }
        private IItemHandler handler(DistributedSlotMap.Slot<Target> mapped) {
            if (mapped == null) return null;
            IItemHandler handler = item(mapped.target());
            if (handler == null || mapped.localSlot() >= handler.getSlots()) {
                targetsDirty[side.ordinal()] = true;
                return null;
            }
            return handler;
        }

        private void recordExtractionProbe(int slot, ItemStack stack, boolean simulated) {
            if (!SkyLogisticsConfig.enableDistributorAdaptiveItemTargetProbes()) return;
            configureExtractionProbes();
            DistributedSlotMap<Target> current = targets(side).itemSlots;
            if (simulated) {
                extractionProbes.recordSimulatedProbe(current, slot, gameTime(), !stack.isEmpty());
            } else {
                extractionProbes.recordProbe(current, slot, gameTime(), !stack.isEmpty());
            }
        }

        private void configureExtractionProbes() {
            extractionProbes.configure(SkyLogisticsConfig.distributorItemTargetHotProbeTicks(),
                    SkyLogisticsConfig.distributorItemTargetWarmProbeTicks(),
                    SkyLogisticsConfig.distributorItemTargetCoolProbeTicks(),
                    SkyLogisticsConfig.distributorItemTargetFallbackProbeTicks(),
                    SkyLogisticsConfig.distributorItemTargetMissesPerDemotion());
        }

        private ItemInsertPlan matchingItemInsertPlan(ItemStack stack, boolean simulate) {
            prepareOperationState();
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
                boolean adaptiveRoutes = SkyLogisticsConfig.enableDistributorAdaptiveItemTargetProbes();
                ItemStackKey routeKey = adaptiveRoutes ? ItemStackKey.of(stack) : null;
                int candidateCount;
                if (adaptiveRoutes) {
                    configureInsertionRoutes();
                    candidateCount = insertionRoutes.orderCandidates(
                            routeKey, all.size(), gameTime(), insertionRouteCandidates);
                } else {
                    int start = Math.floorMod(itemInsertCursors[side.ordinal()], all.size());
                    candidateCount = all.size();
                    for (int offset = 0; offset < candidateCount; offset++) {
                        insertionRouteCandidates[offset] = (start + offset) % all.size();
                    }
                }
                boolean sequential = sequentialInsertion();
                int successfulTargets = adaptiveRoutes
                        ? insertionRoutes.successfulTargetCount(routeKey, all.size()) : all.size();
                int shareTargets = successfulTargets > 0 ? successfulTargets : all.size();
                int share = DistributorInsertMode.offer(stack.getCount(), shareTargets, sequential);
                int planned = 0;
                int successfulTargetRank = 0;
                boolean discoveryProbeCompleted = false;
                for (int offset = 0; offset < candidateCount; offset++) {
                    if (!adaptiveRoutes && planned >= stack.getCount()) break;
                    int target = insertionRouteCandidates[offset];
                    boolean knownRoute = adaptiveRoutes
                            && insertionRoutes.isSuccessful(routeKey, target, all.size());
                    int targetShare = !sequential && knownRoute && successfulTargets > 0
                            ? DistributorInsertMode.balancedOffer(
                                    stack.getCount(), successfulTargets, successfulTargetRank++)
                            : share;
                    boolean discoveryOnly = adaptiveRoutes && planned >= stack.getCount();
                    if (discoveryOnly && (knownRoute || discoveryProbeCompleted)) continue;
                    if (!discoveryOnly && targetShare <= 0) continue;
                    if (!takeOperation()) break;
                    if (!adaptiveRoutes) {
                        itemInsertCursors[side.ordinal()] = target + 1;
                        if (isRejected(target, stack)) continue;
                    }
                    IItemHandler handler = item(all.get(target));
                    if (handler == null || handler.getSlots() == 0) {
                        if (adaptiveRoutes) insertionRoutes.recordMiss(routeKey, target, all.size(), gameTime());
                        continue;
                    }
                    int slots = handler.getSlots();
                    int slotStart = Math.floorMod(itemInsertSlotCursors[target], slots);
                    boolean fullyScanned = true;
                    boolean targetAccepted = false;
                    for (int checked = 0; checked < slots; checked++) {
                        if (checked > 0 && !takeOperation()) {
                            if (!adaptiveRoutes) itemInsertCursors[side.ordinal()] = target;
                            fullyScanned = false;
                            break;
                        }
                        int slot = (slotStart + checked) % slots;
                        ItemStack offer = stack.copy();
                        offer.setCount(discoveryOnly ? 1 : Math.min(targetShare, stack.getCount() - planned));
                        ItemStack rejected = handler.insertItem(slot, offer, true);
                        int accepted = offer.getCount() - rejected.getCount();
                        if (accepted > 0) {
                            targetAccepted = true;
                            if (adaptiveRoutes) insertionRoutes.recordSuccess(routeKey, target, all.size());
                            if (!discoveryOnly) {
                                moves.add(new ItemMove(target, slot, accepted));
                                planned += accepted;
                            }
                            if (discoveryOnly || !sequential || planned >= stack.getCount()) break;
                        }
                        itemInsertSlotCursors[target] = slot + 1;
                    }
                    if (fullyScanned && !targetAccepted) {
                        if (adaptiveRoutes) {
                            insertionRoutes.recordMiss(routeKey, target, all.size(), gameTime());
                        } else {
                            rejectedItems[target] = stack.copy();
                            rejectedItems[target].setCount(1);
                            rejectedItemUntil[target] = gameTime() + 5L;
                        }
                    }
                    if (discoveryOnly) discoveryProbeCompleted = true;
                }
            }
            int accepted = (int)Math.min(stack.getCount(), moves.stream().mapToLong(ItemMove::amount).sum());
            itemInsertPlan = new ItemInsertPlan(gameTime(), stack.copy(), List.copyOf(moves), accepted);
            return itemInsertPlan;
        }

        private ItemInsertPlan buildConstrainedItemInsertPlan(ItemStack stack,
                DistributorItemInsertContext context, Predicate<ItemStack> maintainedMatcher) {
            if (!context.maintained() && !context.orderedMatching()) return buildItemInsertPlan(stack);
            List<Target> all = targets(side).items;
            int start = all.isEmpty() ? 0 : Math.floorMod(itemInsertCursors[side.ordinal()], all.size());
            int orderedDevice = sequentialInsertion() && context.orderedMatching()
                    ? context.orderedPosition(all.size()) : -1;
            if (sequentialInsertion() && context.orderedMatching() && orderedDevice < 0) {
                return rememberItemInsertPlan(stack, List.of());
            }
            ConstrainedItemScanKey scanKey = new ConstrainedItemScanKey(
                    ItemStackKey.of(stack), stack.getCount(), context, all.size(), start);
            ConstrainedItemScan scan = constrainedItemScans.get(scanKey);
            if (scan == null) {
                scan = new ConstrainedItemScan();
                constrainedItemScans.put(scanKey, scan);
                trimConstrainedItemScans();
            }
            while (scan.nextTargetOffset < all.size()) {
                int target = (start + scan.nextTargetOffset) % all.size();
                IItemHandler handler = item(all.get(target));
                if (handler == null || handler.getSlots() <= 0) {
                    scan.nextTargetOffset++;
                    scan.currentTarget = -1;
                    continue;
                }
                int fixedSlot = !sequentialInsertion() && context.orderedMatching()
                        ? context.orderedPosition(handler.getSlots()) : -1;
                boolean insertionEnabled = (!sequentialInsertion() || !context.orderedMatching()
                        || target == orderedDevice)
                        && (!context.orderedMatching() || sequentialInsertion() || fixedSlot >= 0);
                int plannedSlot = insertionEnabled ? fixedSlot : Integer.MIN_VALUE;
                if (scan.currentTarget != target) {
                    scan.beginTarget(target, handler.getSlots(), plannedSlot);
                } else if (scan.currentSlots != handler.getSlots() || scan.fixedSlot != plannedSlot) {
                    constrainedItemScans.remove(scanKey);
                    return rememberItemInsertPlan(stack, List.of());
                }
                while (scan.nextSlot < handler.getSlots()) {
                    if (!takeOperation()) return rememberItemInsertPlan(stack, List.of());
                    int slot = scan.nextSlot++;
                    ItemStack existing = handler.getStackInSlot(slot);
                    boolean maintained = !existing.isEmpty() && maintainedMatcher.test(existing);
                    if (maintained) {
                        scan.matchingSlots++;
                        scan.matchingItems = (int)Math.min(Integer.MAX_VALUE,
                                (long)scan.matchingItems + existing.getCount());
                    }
                    if (scan.fixedSlot == Integer.MIN_VALUE
                            || scan.fixedSlot >= 0 && slot != scan.fixedSlot) continue;
                    ItemStack rejected = handler.insertItem(slot, stack.copy(), true);
                    int accepted = stack.getCount() - rejected.getCount();
                    if (accepted <= 0) continue;
                    boolean opensSlot = existing.isEmpty() && maintainedMatcher.test(stack);
                    ItemMove move = new ItemMove(target, slot, accepted, opensSlot);
                    if (!existing.isEmpty() && sameItemType(existing, stack)) scan.matchingMoves.add(move);
                    else scan.otherMoves.add(move);
                }
                scan.finishTarget();
            }
            List<ItemMove> moves = sequentialInsertion()
                    ? sequentialConstrainedMoves(scan.plans, stack.getCount(), context)
                    : balancedConstrainedMoves(scan.plans, stack.getCount(), context);
            constrainedItemScans.remove(scanKey);
            return rememberItemInsertPlan(stack, moves);
        }

        private void trimConstrainedItemScans() {
            int maximum = Math.max(1, SkyLogisticsConfig.distributorItemRouteCacheSize());
            while (constrainedItemScans.size() > maximum) {
                ConstrainedItemScanKey eldest = constrainedItemScans.keySet().iterator().next();
                constrainedItemScans.remove(eldest);
            }
        }

        private List<ItemMove> balancedConstrainedMoves(List<ItemTargetPlan> targets, int requested,
                DistributorItemInsertContext context) {
            List<List<ItemMove>> available = new ArrayList<>();
            for (ItemTargetPlan target : targets) {
                List<ItemMove> limited = limitedTargetMoves(target, context);
                if (!limited.isEmpty()) available.add(limited);
            }
            int[] capacities = available.stream()
                    .mapToInt(moves -> moves.stream().mapToInt(ItemMove::amount).sum()).toArray();
            int[] assigned = DistributorInsertMode.balancedAssignments(requested, capacities);
            List<ItemMove> result = new ArrayList<>();
            for (int index = 0; index < available.size(); index++)
                appendMoves(result, available.get(index), assigned[index]);
            return result;
        }

        private List<ItemMove> sequentialConstrainedMoves(List<ItemTargetPlan> targets, int requested,
                DistributorItemInsertContext context) {
            int totalSlots = targets.stream().mapToInt(ItemTargetPlan::matchingSlots).sum();
            long totalItems = targets.stream().mapToLong(ItemTargetPlan::matchingItems).sum();
            int remainingItems = context.maintainUnit() == DistributorItemInsertContext.MaintainUnit.ITEMS
                    ? DistributorMaintenancePolicy.remainingItems(totalItems, context.maintainAmount())
                    : requested;
            int newSlotBudget = context.maintainUnit() == DistributorItemInsertContext.MaintainUnit.SLOTS
                    ? DistributorMaintenancePolicy.remainingSlots(totalSlots, context.maintainAmount())
                    : Integer.MAX_VALUE;
            boolean slotsBlocked = context.maintainUnit() == DistributorItemInsertContext.MaintainUnit.SLOTS
                    && DistributorMaintenancePolicy.blocksSlotInsertion(totalSlots,
                            context.maintainAmount(), context.fillMaintainedSlots());
            if (context.maintainUnit() == DistributorItemInsertContext.MaintainUnit.ITEMS && remainingItems <= 0
                    || slotsBlocked) return List.of();
            List<ItemMove> result = new ArrayList<>();
            int remaining = Math.min(requested, remainingItems);
            for (ItemTargetPlan target : targets) {
                for (ItemMove move : target.moves()) {
                    if (remaining <= 0) break;
                    if (move.opensMaintainedSlot() && newSlotBudget <= 0) continue;
                    int amount = Math.min(move.amount(), remaining);
                    if (amount <= 0) continue;
                    result.add(new ItemMove(move.target(), move.slot(), amount, move.opensMaintainedSlot()));
                    remaining -= amount;
                    if (move.opensMaintainedSlot()) newSlotBudget--;
                }
            }
            return result;
        }

        private List<ItemMove> limitedTargetMoves(ItemTargetPlan target,
                DistributorItemInsertContext context) {
            if (!context.maintained()) return target.moves();
            if (context.maintainUnit() == DistributorItemInsertContext.MaintainUnit.ITEMS) {
                int remaining = DistributorMaintenancePolicy.remainingItems(
                        target.matchingItems(), context.maintainAmount());
                if (remaining <= 0) return List.of();
                List<ItemMove> result = new ArrayList<>();
                appendMoves(result, target.moves(), remaining);
                return result;
            }
            if (DistributorMaintenancePolicy.blocksSlotInsertion(target.matchingSlots(),
                    context.maintainAmount(), context.fillMaintainedSlots())) {
                return List.of();
            }
            int newSlots = DistributorMaintenancePolicy.remainingSlots(
                    target.matchingSlots(), context.maintainAmount());
            List<ItemMove> result = new ArrayList<>();
            for (ItemMove move : target.moves()) {
                if (move.opensMaintainedSlot() && newSlots <= 0) continue;
                result.add(move);
                if (move.opensMaintainedSlot()) newSlots--;
            }
            return result;
        }

        private int appendMoves(List<ItemMove> result, List<ItemMove> source, int maximum) {
            int appended = 0;
            for (ItemMove move : source) {
                if (appended >= maximum) break;
                int amount = Math.min(move.amount(), maximum - appended);
                if (amount <= 0) continue;
                result.add(new ItemMove(move.target(), move.slot(), amount, move.opensMaintainedSlot()));
                appended += amount;
            }
            return appended;
        }

        private ItemInsertPlan rememberItemInsertPlan(ItemStack stack, List<ItemMove> moves) {
            int accepted = (int)Math.min(stack.getCount(), moves.stream().mapToLong(ItemMove::amount).sum());
            if (!moves.isEmpty()) itemInsertCursors[side.ordinal()] = moves.get(moves.size() - 1).target() + 1;
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
            boolean adaptiveRoutes = SkyLogisticsConfig.enableDistributorAdaptiveItemTargetProbes();
            ItemStackKey routeKey = adaptiveRoutes ? ItemStackKey.of(request) : null;
            int inserted = 0;
            int lastSuccessfulTarget = -1;
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
                    lastSuccessfulTarget = move.target;
                    itemInsertSlotCursors[move.target] = rejected.isEmpty() ? move.slot : move.slot + 1;
                    if (adaptiveRoutes) insertionRoutes.recordSuccess(routeKey, move.target, all.size());
                    else rejectedItems[move.target] = null;
                } else {
                    itemInsertSlotCursors[move.target] = move.slot + 1;
                    if (adaptiveRoutes) {
                        insertionRoutes.recordMiss(routeKey, move.target, all.size(), gameTime());
                    }
                }
            }
            if (adaptiveRoutes && lastSuccessfulTarget >= 0) {
                insertionRoutes.advanceHotCursorAfter(routeKey, lastSuccessfulTarget, all.size());
            }
            return inserted;
        }

        private void configureInsertionRoutes() {
            insertionRoutes.configure(SkyLogisticsConfig.distributorItemRouteCacheSize(),
                    SkyLogisticsConfig.distributorItemTargetHotProbeTicks(),
                    SkyLogisticsConfig.distributorItemTargetWarmProbeTicks(),
                    SkyLogisticsConfig.distributorItemTargetCoolProbeTicks(),
                    SkyLogisticsConfig.distributorItemTargetFallbackProbeTicks(),
                    SkyLogisticsConfig.distributorItemTargetMissesPerDemotion());
        }

        private void clearInsertionRoutes() {
            insertionRoutes.clear();
        }

        private void remapAdaptiveState(DistributedSlotMap<Target> previous,
                DistributedSlotMap<Target> current, int[] oldIndexForNew) {
            insertionRoutes.remapTargets(oldIndexForNew);
            extractionProbes.remapTargets(current, oldIndexForNew, gameTime());
            itemInsertPlan = null;
            itemInsertPlanAwaitingExecution = false;
            itemExtractPlan = null;
        }
    }

    private final class DistributedFluids implements IFluidHandler, BudgetedDistributorHandler,
            ConstrainedDistributorFluidHandler<FluidStack> {
        private final Direction side;
        private final AdaptiveTargetProbeScheduler extractionProbes = new AdaptiveTargetProbeScheduler();
        private final HierarchicalTargetRouteCache<FluidStackKey> insertionRoutes = new HierarchicalTargetRouteCache<>();
        private final int[] insertionRouteCandidates = new int[MAX_CONFIGURABLE_TARGETS];
        private int observedDrainTarget = -1;
        private FluidStackKey observedDrainKey;
        private long observedDrainTick = Long.MIN_VALUE;

        private DistributedFluids(Direction side) { this.side = side; }

        @Override public boolean distributorBudgetExhausted() { return DistributorOperationBudget.exhausted(); }
        @Override public boolean distributorScanPending() {
            return targetIndexUnavailable(side);
        }
        @Override public boolean usesIndependentExtractionProbes() { return fluidRoutingConfig().enabled(); }
        @Override public int nextFairExtractionSlot(long time) { configureExtractionProbes(); return extractionProbes.nextDueTarget(targets(side).fluids.size(), time); }
        @Override public int fairExtractionProbesDue(long time) { configureExtractionProbes(); return extractionProbes.dueProbeCount(targets(side).fluids.size(), time); }
        @Override public void setMaintainedExtractionPollTicks(int pollTicks) { extractionProbes.setMaximumInterval(pollTicks, gameTime()); insertionRoutes.setMaximumInterval(pollTicks); }

        @Override public int getTanks() {
            if (!SkyLogisticsConfig.enableDistributorFluids()) return 0;
            return targets(side).fluids.size();
        }
        @Override public FluidStack getFluidInTank(int tank) {
            IFluidHandler handler = handler(tank);
            if (handler == null || handler.getTanks() == 0) { recordExtractionProbe(tank, false); return FluidStack.EMPTY; }
            prepareOperationState();
            if (visibleFluids[tank] != null) { observeDrainTarget(tank, visibleFluids[tank]); recordExtractionProbe(tank, true); return visibleFluids[tank].copy(); }
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
                    observeDrainTarget(tank, stack);
                    recordExtractionProbe(tank, true);
                    return stack;
                }
                fluidExtractCursors[tank] = sourceTank + 1;
            }
            visibleFluidTanks[tank] = -1;
            recordExtractionProbe(tank, false);
            return FluidStack.EMPTY;
        }
        @Override public int getTankCapacity(int tank) { IFluidHandler h = handler(tank); return h == null ? 0 : Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        @Override
        public int planMaintainedFluidInsertion(FluidStack resource, boolean maintainByAmount,
                long maintainTarget, boolean fillMaintainedUnits) {
            fluidInsertPlan = buildMaintainedFluidInsertPlan(resource, maintainByAmount, maintainTarget,
                    fillMaintainedUnits);
            fluidInsertPlanAwaitingExecution = true;
            return fluidInsertPlan.accepted;
        }

        private FluidInsertPlan buildMaintainedFluidInsertPlan(FluidStack resource, boolean maintainByAmount,
                long maintainTarget, boolean fillMaintainedUnits) {
            List<Target> all = targets(side).fluids;
            if (resource.isEmpty() || all.isEmpty()) return new FluidInsertPlan(gameTime(), resource.copy(), List.of(), 0);
            int cursorIndex = side.ordinal();
            int start = Math.floorMod(fluidInsertCursors[cursorIndex], all.size());
            int[] targetIndices = new int[all.size()];
            long[] stored = new long[all.size()], capacities = new long[all.size()], refill = new long[all.size()];
            int[] occupied = new int[all.size()];
            for (int offset = 0; offset < all.size(); offset++) {
                if (!takeOperation()) return new FluidInsertPlan(gameTime(), resource.copy(), List.of(), 0);
                int target = (start + offset) % all.size();
                targetIndices[offset] = target;
                IFluidHandler handler = fluid(all.get(target));
                if (handler == null) continue;
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                    FluidStack existing = handler.getFluidInTank(tank);
                    if (existing.isEmpty() || !sameFluidType(existing, resource)) continue;
                    stored[offset] += existing.getAmount();
                    occupied[offset]++;
                    refill[offset] += Math.max(0, handler.getTankCapacity(tank) - existing.getAmount());
                }
                FluidStack offer = resource.copy();
                capacities[offset] = Math.max(0, handler.fill(offer, FluidAction.SIMULATE));
            }
            long[] assignments = DistributorResourceMaintenancePolicy.assignments(resource.getAmount(), stored,
                    capacities, occupied, refill, maintainByAmount, maintainTarget, fillMaintainedUnits,
                    sequentialInsertion());
            List<FluidMove> moves = new ArrayList<>();
            int accepted = 0;
            int last = -1;
            for (int offset = 0; offset < assignments.length; offset++) {
                int assigned = (int)Math.min(Integer.MAX_VALUE, assignments[offset]);
                if (assigned <= 0) continue;
                moves.add(new FluidMove(targetIndices[offset], assigned));
                accepted += assigned;
                last = targetIndices[offset];
            }
            fluidInsertCursors[cursorIndex] = last >= 0 ? last + 1 : start + 1;
            return new FluidInsertPlan(gameTime(), resource.copy(), List.copyOf(moves),
                    Math.min(resource.getAmount(), accepted));
        }

        @Override public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            FluidInsertPlan plan = fluidInsertPlan;
            boolean execute = action.execute();
            if (plan == null || plan.tick != gameTime()
                    || (execute ? !sameFluidType(plan.request, resource)
                            || !fluidInsertPlanAwaitingExecution || resource.getAmount() > plan.accepted
                            : !sameFluid(plan.request, resource))) {
                plan = buildFluidInsertPlan(resource);
            }
            if (!execute) {
                fluidInsertPlanAwaitingExecution = true;
                return plan.accepted;
            }
            fluidInsertPlanAwaitingExecution = false;
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
                AdaptiveRoutingConfig config = fluidRoutingConfig();
                FluidStackKey key = FluidStackKey.of(resource);
                int candidateCount;
                if (config.enabled()) {
                    configureInsertionRoutes(config);
                    candidateCount = insertionRoutes.orderCandidates(key, all.size(), gameTime(), insertionRouteCandidates);
                } else {
                    int start = Math.floorMod(fluidInsertCursors[side.ordinal()], all.size());
                    candidateCount = all.size();
                    for (int offset = 0; offset < candidateCount; offset++) insertionRouteCandidates[offset] = (start + offset) % all.size();
                }
                boolean sequential = sequentialInsertion();
                int successfulTargets = config.enabled() ? insertionRoutes.successfulTargetCount(key, all.size()) : all.size();
                int shareTargets = successfulTargets > 0 ? successfulTargets : all.size();
                int share = DistributorInsertMode.offer(resource.getAmount(), shareTargets, sequential);
                int planned = 0;
                int successfulTargetRank = 0;
                boolean discoveryProbeCompleted = false;
                for (int offset = 0; offset < candidateCount; offset++) {
                    if (!config.enabled() && planned >= resource.getAmount()) break;
                    int target = insertionRouteCandidates[offset];
                    boolean knownRoute = config.enabled() && insertionRoutes.isSuccessful(key, target, all.size());
                    int targetShare = !sequential && knownRoute && successfulTargets > 0
                            ? DistributorInsertMode.balancedOffer(resource.getAmount(), successfulTargets, successfulTargetRank++) : share;
                    boolean discoveryOnly = config.enabled() && planned >= resource.getAmount();
                    if (discoveryOnly && (knownRoute || discoveryProbeCompleted)) continue;
                    if (!discoveryOnly && targetShare <= 0) continue;
                    if (!takeOperation()) break;
                    if (!config.enabled()) fluidInsertCursors[side.ordinal()] = target + 1;
                    if (!config.enabled() && rejectedFluids[target] != null && gameTime() < rejectedFluidUntil[target]
                            && sameFluidType(rejectedFluids[target], resource)) continue;
                    IFluidHandler handler = fluid(all.get(target));
                    if (handler == null) { if (config.enabled()) insertionRoutes.recordMiss(key, target, all.size(), gameTime()); continue; }
                    FluidStack offer = resource.copy(); offer.setAmount(discoveryOnly ? 1 : Math.min(targetShare, resource.getAmount() - planned));
                    int accepted = handler.fill(offer, FluidAction.SIMULATE);
                    if (accepted > 0) {
                        if (config.enabled()) insertionRoutes.recordSuccess(key, target, all.size());
                        if (!discoveryOnly) { moves.add(new FluidMove(target, Math.min(offer.getAmount(), accepted))); planned += Math.min(offer.getAmount(), accepted); }
                    } else if (config.enabled()) insertionRoutes.recordMiss(key, target, all.size(), gameTime());
                    else {
                        rejectedFluids[target] = resource.copy(); rejectedFluids[target].setAmount(1);
                        rejectedFluidUntil[target] = gameTime() + 5L;
                    }
                    if (discoveryOnly) discoveryProbeCompleted = true;
                }
            }
            int accepted = (int)Math.min(resource.getAmount(), moves.stream().mapToLong(FluidMove::amount).sum());
            fluidInsertPlan = new FluidInsertPlan(gameTime(), resource.copy(), List.copyOf(moves), accepted);
            return fluidInsertPlan;
        }

        private int executeFluidInsertPlan(FluidInsertPlan plan, FluidStack resource) {
            List<Target> all = targets(side).fluids;
            AdaptiveRoutingConfig config = fluidRoutingConfig();
            FluidStackKey key = FluidStackKey.of(resource);
            int inserted = 0;
            int lastSuccessfulTarget = -1;
            for (FluidMove move : plan.moves) {
                if (move.target >= all.size() || inserted >= resource.getAmount()) break;
                IFluidHandler handler = fluid(all.get(move.target));
                if (handler == null) continue;
                FluidStack offer = resource.copy();
                offer.setAmount(Math.min(move.amount, resource.getAmount() - inserted));
                int moved = handler.fill(offer, FluidAction.EXECUTE);
                inserted += moved;
                if (moved > 0) { lastSuccessfulTarget = move.target; if (config.enabled()) insertionRoutes.recordSuccess(key, move.target, all.size()); else rejectedFluids[move.target] = null; }
                else if (config.enabled()) insertionRoutes.recordMiss(key, move.target, all.size(), gameTime());
            }
            if (config.enabled() && lastSuccessfulTarget >= 0) insertionRoutes.advanceHotCursorAfter(key, lastSuccessfulTarget, all.size());
            return inserted;
        }

        private FluidDrainPlan buildFluidDrainPlan(FluidStack resource) {
            List<Target> all = targets(side).fluids;
            List<FluidMove> moves = new ArrayList<>();
            int remaining = resource.getAmount();
            int cursorIndex = side.ordinal();
            FluidStackKey drainKey = FluidStackKey.of(resource);
            int start = all.isEmpty() ? 0 : observedDrainTick == gameTime() && drainKey.equals(observedDrainKey)
                    && observedDrainTarget >= 0 && observedDrainTarget < all.size()
                    ? observedDrainTarget : Math.floorMod(fluidDrainCursors[cursorIndex], all.size());
            for (int offset = 0; offset < all.size() && remaining > 0; offset++) {
                if (!takeOperation()) break;
                int target = (start + offset) % all.size();
                fluidDrainCursors[cursorIndex] = target + 1;
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
                    recordExtractionProbe(move.target, true);
                    fluidExtractCursors[move.target]++;
                    visibleFluidTanks[move.target] = -2;
                    visibleFluids[move.target] = null;
                }
                else recordExtractionProbe(move.target, false);
            }
            observedDrainTarget = -1; observedDrainKey = null; observedDrainTick = Long.MIN_VALUE;
            return result;
        }

        private void observeDrainTarget(int target, FluidStack stack) { observedDrainTarget = target; observedDrainKey = FluidStackKey.of(stack); observedDrainTick = gameTime(); }
        private void recordExtractionProbe(int target, boolean available) { AdaptiveRoutingConfig config = fluidRoutingConfig(); if (!config.enabled() || DistributorOperationBudget.exhausted()) return; configureExtractionProbes(); extractionProbes.recordProbe(targets(side).fluids.size(), target, gameTime(), available); }
        private void configureExtractionProbes() { AdaptiveRoutingConfig config = fluidRoutingConfig(); extractionProbes.configure(config.hotTicks(), config.warmTicks(), config.coolTicks(), config.fallbackTicks(), config.missesPerDemotion()); }
        private void configureInsertionRoutes(AdaptiveRoutingConfig config) { insertionRoutes.configure(config.routeCacheSize(), config.hotTicks(), config.warmTicks(), config.coolTicks(), config.fallbackTicks(), config.missesPerDemotion()); }
        private void clearAdaptiveState() { insertionRoutes.clear(); extractionProbes.clear(); observedDrainTarget = -1; observedDrainKey = null; observedDrainTick = Long.MIN_VALUE; }
        private void remapAdaptiveState(int[] oldIndexForNew) { insertionRoutes.remapTargets(oldIndexForNew); extractionProbes.remapTargets(oldIndexForNew, gameTime()); observedDrainTarget = remappedTarget(observedDrainTarget, oldIndexForNew); fluidInsertPlan = null; fluidDrainPlan = null; }
    }

    private final class DistributedEnergy implements IEnergyStorage, BudgetedDistributorHandler, MaintainedStorageView,
            ConstrainedDistributorEnergyHandler {
        private final Direction side;

        private DistributedEnergy(Direction side) { this.side = side; }

        @Override public boolean distributorBudgetExhausted() { return DistributorOperationBudget.exhausted(); }
        @Override public boolean distributorScanPending() { return targetIndexUnavailable(side); }

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
        @Override public long maintainedStoredAmount() { return getEnergyStored(); }
        @Override public int maintainedOccupiedStorageUnits() { refreshEnergySnapshot(); return energyOccupiedSnapshot; }
        @Override public long maintainedExistingUnitRefillCapacity() { refreshEnergySnapshot(); return energyExistingRefillSnapshot; }

        @Override
        public int planMaintainedEnergyInsertion(int amount, boolean maintainByAmount, long maintainTarget,
                boolean fillMaintainedUnits) {
            EnergyPlan plan = buildMaintainedEnergyPlan(amount, maintainByAmount, maintainTarget,
                    fillMaintainedUnits);
            energyReceivePlan = plan;
            return plan.accepted;
        }

        private EnergyPlan buildMaintainedEnergyPlan(int amount, boolean maintainByAmount, long maintainTarget,
                boolean fillMaintainedUnits) {
            List<Target> all = targets(side).energy;
            if (all.isEmpty() || amount <= 0) return new EnergyPlan(gameTime(), amount, List.of(), 0);
            int cursorIndex = side.ordinal();
            int start = Math.floorMod(energyReceiveCursors[cursorIndex], all.size());
            int[] targetIndices = new int[all.size()];
            int[] stored = new int[all.size()];
            int[] capacities = new int[all.size()];
            for (int offset = 0; offset < all.size(); offset++) {
                if (!takeOperation()) return new EnergyPlan(gameTime(), amount, List.of(), 0);
                int target = (start + offset) % all.size();
                targetIndices[offset] = target;
                IEnergyStorage handler = energy(all.get(target));
                if (handler == null) continue;
                stored[offset] = Math.max(0, handler.getEnergyStored());
                capacities[offset] = Math.max(0, handler.receiveEnergy(amount, true));
            }
            int[] assignments = DistributorEnergyMaintenancePolicy.assignments(amount, stored, capacities,
                    maintainByAmount, maintainTarget, fillMaintainedUnits, sequentialInsertion());
            List<FluidMove> moves = new ArrayList<>();
            int accepted = 0;
            int lastAssignedTarget = -1;
            for (int offset = 0; offset < assignments.length; offset++) {
                if (assignments[offset] <= 0) continue;
                moves.add(new FluidMove(targetIndices[offset], assignments[offset]));
                accepted += assignments[offset];
                lastAssignedTarget = targetIndices[offset];
            }
            energyReceiveCursors[cursorIndex] = lastAssignedTarget >= 0 ? lastAssignedTarget + 1 : start + 1;
            return new EnergyPlan(gameTime(), amount, List.copyOf(moves), accepted);
        }

        private EnergyPlan buildEnergyPlan(int amount, boolean receive) {
            List<Target> all = targets(side).energy;
            List<FluidMove> moves = new ArrayList<>();
            if (!all.isEmpty() && amount > 0) {
                int cursorIndex = side.ordinal();
                int cursor = receive ? energyReceiveCursors[cursorIndex] : energyExtractCursors[cursorIndex];
                int start = Math.floorMod(cursor, all.size());
                int share = DistributorInsertMode.offer(amount, all.size(), receive && sequentialInsertion());
                int planned = 0;
                for (int offset = 0; offset < all.size() && planned < amount; offset++) {
                    if (!takeOperation()) break;
                    int target = (start + offset) % all.size();
                    if (receive) energyReceiveCursors[cursorIndex] = target + 1;
                    else energyExtractCursors[cursorIndex] = target + 1;
                    IEnergyStorage handler = energy(all.get(target));
                    if (handler == null) continue;
                    int offer = Math.min(share, amount - planned);
                    int accepted = receive ? handler.receiveEnergy(offer, true) : handler.extractEnergy(offer, true);
                    if (accepted > 0) { moves.add(new FluidMove(target, accepted)); planned += accepted; }
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
                int stored = handler.getEnergyStored();
                int capacity = handler.getMaxEnergyStored();
                energyStoredAccumulator += stored;
                energyCapacityAccumulator += capacity;
                if (stored > 0) { energyOccupiedAccumulator++; energyExistingRefillAccumulator += Math.max(0, capacity - stored); }
                energyCanExtractAccumulator |= handler.canExtract();
                energyCanReceiveAccumulator |= handler.canReceive();
            }
            if (energySnapshotScanned >= all.size()) {
                energyStoredSnapshot = (int)Math.min(Integer.MAX_VALUE, energyStoredAccumulator);
                energyCapacitySnapshot = (int)Math.min(Integer.MAX_VALUE, energyCapacityAccumulator);
                energyOccupiedSnapshot = energyOccupiedAccumulator;
                energyExistingRefillSnapshot = energyExistingRefillAccumulator;
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
            energyOccupiedAccumulator = 0;
            energyExistingRefillAccumulator = 0L;
            energyCanExtractAccumulator = false;
            energyCanReceiveAccumulator = false;
        }
    }
}
