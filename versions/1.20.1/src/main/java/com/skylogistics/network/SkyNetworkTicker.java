package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.SkyMEInterfaceBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity.ExternalWhitelistCandidates;
import com.skylogistics.block.entity.NetworkEndpointBlockEntity;
import com.skylogistics.block.entity.NetworkEndpointBlockEntity.TargetResource;
import com.skylogistics.block.entity.SkyRSInterfaceBlockEntity;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.ae2.AppliedEnergisticsCompat;
import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.distributor.BudgetedDistributorHandler;
import com.skylogistics.compat.distributor.ConstrainedDistributorItemHandler;
import com.skylogistics.compat.distributor.DistributorItemInsertContext;
import com.skylogistics.compat.distributor.DistributorWorkDefer;
import com.skylogistics.compat.distributor.MaintainedStorageView;
import com.skylogistics.compat.ForceExtractionCompat;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.compat.refinedstorage.RefinedStorageCompat;
import com.skylogistics.compat.sophisticated.SophisticatedStorageCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.SkyNetworkRegistry.CachedEndpoint;
import com.skylogistics.network.SkyNetworkRegistry.LineIndex;
import com.skylogistics.network.SkyNetworkRegistry.ReadyLines;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.storage.ItemStackKey;
import com.skylogistics.util.MaintainedSlotPolicy;
import com.skylogistics.util.MaintainedResourcePolicy;
import com.skylogistics.util.OrderedMatchingPolicy;
import com.skylogistics.util.OrderedMatchingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public final class SkyNetworkTicker {
    private static final Map<CachedEndpoint, LongItemEndpointCache> LONG_ITEM_ENDPOINTS = new WeakHashMap<>();
    private static final Map<CachedEndpoint, LongFluidEndpointCache> LONG_FLUID_ENDPOINTS = new WeakHashMap<>();
    private static final Map<CachedEndpoint, ExactItemScan> SOURCE_EXACT_ITEM_SCANS = new WeakHashMap<>();
    private static final Map<CachedEndpoint, ExactItemScan> TARGET_EXACT_ITEM_SCANS = new WeakHashMap<>();
    private static final Map<CachedEndpoint, SlotLimitScan> SOURCE_SLOT_LIMIT_SCANS = new WeakHashMap<>();
    private static final Map<CachedEndpoint, SlotLimitScan> TARGET_SLOT_LIMIT_SCANS = new WeakHashMap<>();

    private SkyNetworkTicker() {
    }

    public static void clear() {
        LONG_ITEM_ENDPOINTS.clear();
        LONG_FLUID_ENDPOINTS.clear();
        SOURCE_EXACT_ITEM_SCANS.clear();
        TARGET_EXACT_ITEM_SCANS.clear();
        SOURCE_SLOT_LIMIT_SCANS.clear();
        TARGET_SLOT_LIMIT_SCANS.clear();
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        process(event.getServer());
    }

    private static void process(MinecraftServer server) {
        if (server.overworld() == null) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        int serverOpsPerTick = SkyLogisticsConfig.serverOpsPerTick();
        int lineOpsPerTick = SkyLogisticsConfig.lineOpsPerTick();
        int operations = 0;
        int endpointVisits = 0;
        ReadyLines lines = SkyNetworkRegistry.readyLines(server, gameTime);
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            LineIndex line = lines.get(lineIndex);
            if (!line.canProcess(gameTime)) {
                continue;
            }
            int lineOperationsBefore = operations;
            long nextWake = Long.MAX_VALUE;
            List<CachedEndpoint> globalItemOutputs = null;
            List<CachedEndpoint> globalFluidOutputs = null;
            List<CachedEndpoint> globalChemicalOutputs = null;
            List<CachedEndpoint> globalEnergyOutputs = null;
            List<CachedEndpoint> globalManaOutputs = null;
            List<CachedEndpoint> globalSourceOutputs = null;
            boolean lineBudgetExhausted = false;
            boolean endpointVisitBudgetExhausted = false;
            int lineEndpointVisits = 0;
            boolean localItemRoute = line.hasLocalItemRoute();
            boolean localFluidRoute = line.hasLocalFluidRoute();
            boolean localChemicalRoute = line.hasLocalChemicalRoute();
            boolean localEnergyRoute = line.hasLocalEnergyRoute();
            boolean localManaRoute = line.hasLocalManaRoute();
            boolean localSourceRoute = line.hasLocalSourceRoute();
            int inputCount = line.inputCount();
            for (int inputIndex = 0; inputIndex < inputCount; inputIndex++) {
                if (endpointVisits >= serverOpsPerTick) {
                    line.advanceInputCursor(lineEndpointVisits);
                    return;
                }
                if (lineEndpointVisits >= lineOpsPerTick) {
                    endpointVisitBudgetExhausted = true;
                    lineBudgetExhausted = true;
                    break;
                }
                CachedEndpoint input = line.inputAt(inputIndex);
                endpointVisits++;
                lineEndpointVisits++;
                if (operations >= serverOpsPerTick) {
                    line.advanceInputCursor(lineEndpointVisits);
                    return;
                }
                int lineOperations = operations - lineOperationsBefore;
                if (lineOperations >= lineOpsPerTick) {
                    lineBudgetExhausted = true;
                    break;
                }
                NetworkEndpointBlockEntity node = input.node();
                if (!node.isFaceRedstoneAllowed(input.direction())) {
                    nextWake = Math.min(nextWake, gameTime + 20L);
                    continue;
                }
                boolean dimensionUpgrade = node.hasDimensionUpgrade();
                boolean itemRoute = localItemRoute || dimensionUpgrade;
                boolean fluidRoute = localFluidRoute || dimensionUpgrade;
                boolean chemicalRoute = localChemicalRoute || dimensionUpgrade;
                boolean energyRoute = localEnergyRoute || dimensionUpgrade;
                boolean manaRoute = localManaRoute || dimensionUpgrade;
                boolean sourceRoute = localSourceRoute || dimensionUpgrade;
                int remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (itemRoute && node.isItemsEnabled(input.direction()) && input.canTryItems(gameTime)
                        && remainingLineBudget > 0) {
                    if (dimensionUpgrade && globalItemOutputs == null) {
                        globalItemOutputs = SkyNetworkRegistry.globalItemOutputs(line.lineId());
                    }
                    List<CachedEndpoint> targets = targetsFor(dimensionUpgrade, line.priorityItemOutputs(),
                            globalItemOutputs);
                    if (targets.isEmpty()) {
                        if (SkyNecklaceTicker.activeItemInserterCount(line.lineId()) == 0) {
                            input.recordItemFailure(gameTime);
                        } else {
                            input.deferItemsUntil(nextNecklaceWake(gameTime));
                        }
                    } else {
                        operations += transferItems(input, targets,
                                Math.min(serverOpsPerTick - operations, remainingLineBudget), gameTime);
                    }
                }
                if (operations >= serverOpsPerTick) {
                    line.advanceInputCursor(lineEndpointVisits);
                    return;
                }
                remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (remainingLineBudget <= 0) {
                    lineBudgetExhausted = true;
                    break;
                }
                if (fluidRoute && node.isFluidsEnabled(input.direction()) && input.canTryFluids(gameTime)) {
                    if (dimensionUpgrade && globalFluidOutputs == null) {
                        globalFluidOutputs = SkyNetworkRegistry.globalFluidOutputs(line.lineId());
                    }
                    List<CachedEndpoint> targets = targetsFor(dimensionUpgrade, line.priorityFluidOutputs(),
                            globalFluidOutputs);
                    if (targets.isEmpty()) {
                        input.recordFluidFailure(gameTime);
                    } else {
                        operations += transferFluids(input, targets,
                                Math.min(serverOpsPerTick - operations, remainingLineBudget), gameTime);
                    }
                }
                if (operations >= serverOpsPerTick) {
                    line.advanceInputCursor(lineEndpointVisits);
                    return;
                }
                remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (remainingLineBudget <= 0) {
                    lineBudgetExhausted = true;
                    break;
                }
                if (chemicalRoute && SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()
                        && node.isFluidsEnabled(input.direction())
                        && input.canTryChemicals(gameTime)) {
                    if (dimensionUpgrade && globalChemicalOutputs == null) {
                        globalChemicalOutputs = SkyNetworkRegistry.globalChemicalOutputs(line.lineId());
                    }
                    List<CachedEndpoint> targets = targetsFor(dimensionUpgrade, line.priorityChemicalOutputs(),
                            globalChemicalOutputs);
                    if (targets.isEmpty()) {
                        input.recordChemicalFailure(gameTime);
                    } else {
                        operations += transferChemicals(input, targets,
                                Math.min(serverOpsPerTick - operations, remainingLineBudget), gameTime);
                    }
                }
                if (operations >= serverOpsPerTick) {
                    line.advanceInputCursor(lineEndpointVisits);
                    return;
                }
                remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (remainingLineBudget <= 0) {
                    lineBudgetExhausted = true;
                    break;
                }
                if (energyRoute && node.isEnergyEnabled(input.direction()) && input.canTryEnergy(gameTime)) {
                    if (dimensionUpgrade && globalEnergyOutputs == null) {
                        globalEnergyOutputs = SkyNetworkRegistry.globalEnergyOutputs(line.lineId());
                    }
                    List<CachedEndpoint> targets = targetsFor(dimensionUpgrade, line.priorityEnergyOutputs(),
                            globalEnergyOutputs);
                    if (targets.isEmpty()) {
                        input.recordEnergyFailure(gameTime);
                    } else {
                        operations += transferEnergy(input, targets,
                                Math.min(serverOpsPerTick - operations, remainingLineBudget), gameTime);
                    }
                }
                remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (remainingLineBudget <= 0) {
                    lineBudgetExhausted = true;
                    break;
                }
                if (manaRoute && node.isEnergyEnabled(input.direction())
                        && canTransferMana() && input.canTryMana(gameTime)) {
                    if (dimensionUpgrade && globalManaOutputs == null) {
                        globalManaOutputs = SkyNetworkRegistry.globalManaOutputs(line.lineId());
                    }
                    List<CachedEndpoint> targets = targetsFor(dimensionUpgrade, line.priorityManaOutputs(),
                            globalManaOutputs);
                    if (targets.isEmpty()) {
                        input.recordManaFailure(gameTime);
                    } else {
                        operations += transferMana(input, targets,
                                Math.min(serverOpsPerTick - operations, remainingLineBudget), gameTime);
                    }
                }
                remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (remainingLineBudget <= 0) {
                    lineBudgetExhausted = true;
                    break;
                }
                if (sourceRoute && node.isEnergyEnabled(input.direction())
                        && canTransferSource() && input.canTrySource(gameTime)) {
                    if (dimensionUpgrade && globalSourceOutputs == null) {
                        globalSourceOutputs = SkyNetworkRegistry.globalSourceOutputs(line.lineId());
                    }
                    List<CachedEndpoint> targets = targetsFor(dimensionUpgrade, line.prioritySourceOutputs(),
                            globalSourceOutputs);
                    if (targets.isEmpty()) {
                        input.recordSourceFailure(gameTime);
                    } else {
                        operations += transferSource(input, targets,
                                Math.min(serverOpsPerTick - operations, remainingLineBudget), gameTime);
                    }
                }
                nextWake = nextInputWake(input, node, gameTime, nextWake, itemRoute, fluidRoute,
                        chemicalRoute, energyRoute, manaRoute, sourceRoute);
            }
            line.advanceInputCursor(endpointVisitBudgetExhausted ? lineEndpointVisits : 1);
            if (operations > lineOperationsBefore) {
                line.wakeNow();
            } else if (lineBudgetExhausted) {
                line.wakeNow();
            } else if (nextWake != Long.MAX_VALUE) {
                line.sleepUntil(nextWake);
            } else {
                line.sleepUntil(gameTime + 20L);
            }
        }
    }

    private static long nextNecklaceWake(long gameTime) {
        int interval = Math.max(1, SkyLogisticsConfig.skyNecklaceTickInterval());
        long remainder = Math.floorMod(gameTime, interval);
        return gameTime + (remainder == 0L ? interval : interval - remainder);
    }

    private static List<CachedEndpoint> targetsFor(boolean globalEnabled, List<CachedEndpoint> localOutputs,
            List<CachedEndpoint> globalOutputs) {
        if (globalEnabled && globalOutputs != null && !globalOutputs.isEmpty()) {
            return globalOutputs;
        }
        return localOutputs;
    }

    private static long nextInputWake(CachedEndpoint input, NetworkEndpointBlockEntity node, long gameTime,
            long current, boolean itemRoute, boolean fluidRoute, boolean chemicalRoute, boolean energyRoute,
            boolean manaRoute, boolean sourceRoute) {
        long nextWake = current;
        if (itemRoute && node.isItemsEnabled(input.direction())) {
            nextWake = Math.min(nextWake, input.nextItemWake(gameTime));
        }
        if (fluidRoute && node.isFluidsEnabled(input.direction())) {
            nextWake = Math.min(nextWake, input.nextFluidWake(gameTime));
            if (chemicalRoute && SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()) {
                nextWake = Math.min(nextWake, input.nextChemicalWake(gameTime));
            }
        }
        if (energyRoute && node.isEnergyEnabled(input.direction())) {
            nextWake = Math.min(nextWake, input.nextEnergyWake(gameTime));
            if (manaRoute && canTransferMana()) {
                nextWake = Math.min(nextWake, input.nextManaWake(gameTime));
            }
            if (sourceRoute && canTransferSource()) {
                nextWake = Math.min(nextWake, input.nextSourceWake(gameTime));
            }
        }
        return nextWake;
    }

    private static boolean canTransferMana() {
        return SkyLogisticsConfig.allowEnergyManaTransfer() && BotaniaCompat.isLoaded();
    }

    private static boolean canTransferSource() {
        return SkyLogisticsConfig.allowEnergySourceTransfer() && ArsNouveauCompat.isLoaded();
    }

    private static int transferItems(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        configureMaintainedBackoff(sourceEndpoint, targets,
                SkyLogisticsConfig.enableMaintainedItemHotSlotPolling(), CachedEndpoint::setItemMaintainedBackoff);
        if (isExternalNetworkItemEndpoint(sourceEndpoint)) {
            return transferExternalNetworkItems(sourceEndpoint, targets, budget, gameTime);
        }
        if (isDimensionItemEndpoint(sourceEndpoint)) {
            return transferDimensionItems(sourceEndpoint, targets, budget, gameTime);
        }
        NetworkEndpointBlockEntity sourceNode = sourceEndpoint.node();
        IItemHandler source = sourceEndpoint.itemHandler(gameTime);
        if (source == null || budget <= 0) {
            return 0;
        }
        SkyNodeBlockEntity orderedMatchingNode = sourceNode instanceof SkyNodeBlockEntity skyNode
                && skyNode.hasOrderedMatchingUpgrade() ? skyNode : null;
        boolean orderedMatching = orderedMatchingNode != null;
        boolean orderedPerItem = orderedMatching
                && orderedMatchingNode.getOrderedMatchingMode() == OrderedMatchingMode.PER_ITEM;
        int orderedMatchingOffset = orderedMatching ? orderedMatchingNode.getOrderedMatchingOffset() : 0;
        if (!orderedPerItem && sourceNode instanceof SkyNodeBlockEntity skyNode) {
            skyNode.clearOrderedMatchingBatch(sourceEndpoint.direction());
        }
        int slots = source.getSlots();
        if (deferExhaustedDistributor(sourceEndpoint, source, gameTime)) return 0;
        if (slots <= 0) {
            if (orderedPerItem) {
                orderedMatchingNode.trimOrderedMatchingDetentions(sourceEndpoint.direction(), 0);
                orderedMatchingNode.clearOrderedMatchingBatch(sourceEndpoint.direction());
                resetOrderedPerItemCursorIfIdle(sourceEndpoint, orderedMatchingNode, targets.size());
            }
            sourceEndpoint.recordItemFailure(gameTime);
            return 0;
        }
        SlotLimitCheck extractionSlotLimit = checkExtractionSlotLimit(sourceEndpoint, source, budget);
        int operations = extractionSlotLimit.checks();
        if (deferExhaustedDistributor(sourceEndpoint, source, gameTime)) return operations;
        if (!extractionSlotLimit.complete()) {
            return operations;
        }
        if (extractionSlotLimit.blocked()) {
            if (orderedPerItem) {
                resetOrderedPerItemCursorIfIdle(sourceEndpoint, orderedMatchingNode, targets.size());
            }
            sourceEndpoint.recordItemFailure(gameTime);
            return operations;
        }
        ExactItemScanResult exactScan = scanExactItems(sourceEndpoint, source,
                Math.max(0, budget - operations), SOURCE_EXACT_ITEM_SCANS);
        operations += exactScan.checks();
        if (deferExhaustedDistributor(sourceEndpoint, source, gameTime)) return operations;
        if (!exactScan.complete()) return operations;
        int exactExcess = exactScan.total() < 0L ? -1
                : (int)Math.min(Integer.MAX_VALUE,
                        Math.max(0L, exactScan.total()
                                - ((SkyNodeBlockEntity)sourceNode).getItemSlotLimit(sourceEndpoint.direction())));
        if (exactExcess == 0) {
            if (orderedPerItem) {
                if (exactScan.total() == 0L) {
                    orderedMatchingNode.trimOrderedMatchingDetentions(sourceEndpoint.direction(), 0);
                    orderedMatchingNode.clearOrderedMatchingBatch(sourceEndpoint.direction());
                }
                resetOrderedPerItemCursorIfIdle(sourceEndpoint, orderedMatchingNode, targets.size());
            }
            SOURCE_EXACT_ITEM_SCANS.remove(sourceEndpoint);
            sourceEndpoint.recordItemFailure(gameTime);
            return operations;
        }
        boolean foundCandidate = false;
        boolean movedFromHotPath = false;
        int successfulSlot = -1;
        int slotChecks = Math.min(slots, sourceNode.getOperationRate());
        boolean independentDistributorProbes = usesIndependentDistributorProbes(source);
        if (independentDistributorProbes && source instanceof BudgetedDistributorHandler distributor) {
            slotChecks = Math.min(slots,
                    Math.max(slotChecks, distributor.fairExtractionProbesDue(gameTime)));
        }
        boolean usedEmptyPreferredSlotFallback = false;
        boolean forceSequentialItemFallback = false;
        int firstTriedSlot = -1;
        int secondTriedSlot = -1;
        boolean sourceSlotsExhausted = false;
        int transferLimit = (int) Math.min(Integer.MAX_VALUE,
                sourceNode.limitItemTransfer(SkyLogisticsConfig.nodeItemTransferLimit()));
        if (orderedPerItem) {
            OrderedPerItemMoveResult detained = retryOrderedPerItemDetentions(sourceEndpoint,
                    orderedMatchingNode, source, targets, Math.max(1, budget - operations), gameTime);
            operations += detained.operations();
            if (exactExcess > 0) exactExcess = Math.max(0, exactExcess - detained.movedItems());
            if (!detained.continueSourceSearch() || exactExcess == 0 || operations >= budget) {
                if (exactExcess >= 0) SOURCE_EXACT_ITEM_SCANS.remove(sourceEndpoint);
                return operations;
            }
        }
        // The upgrade promises a strict order, so it deliberately bypasses hot-slot rotation and
        // rechecks from physical slot 0 even when that scan exceeds this endpoint's normal budget.
        if (orderedMatching) slotChecks = slots;
        for (int i = 0; i < slotChecks && (orderedMatching || operations < budget || i == 0); i++) {
            ItemSourceSearchResult search = orderedMatching
                    ? new ItemSourceSearchResult(i, 0, i + 1 >= slots, false)
                    : forceSequentialItemFallback
                    ? nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                            firstTriedSlot, secondTriedSlot, false, Math.max(1, budget - operations))
                    : nextItemSlot(sourceEndpoint, sourceNode, source, slots, gameTime,
                            firstTriedSlot, secondTriedSlot, Math.max(1, budget - operations));
            forceSequentialItemFallback = false;
            operations += search.skippedChecks();
            int slot = search.index();
            if (slot < 0) {
                sourceSlotsExhausted = search.exhausted();
                break;
            }
            if (orderedMatching && !orderedPerItem
                    && OrderedMatchingPolicy.isSourceSlotSkippedByOffset(slot, orderedMatchingOffset)) {
                continue;
            }
            if (firstTriedSlot < 0) {
                firstTriedSlot = slot;
            } else {
                secondTriedSlot = slot;
            }
            SimulatedItem simulatedItem = simulateSourceItem(sourceEndpoint, source, slot, transferLimit);
            ItemStack simulated = simulatedItem.stack();
            operations++;
            if (simulated.isEmpty()) {
                if (deferExhaustedDistributor(sourceEndpoint, source, gameTime)) return operations;
                if (!independentDistributorProbes) {
                    sourceEndpoint.recordItemSlotMiss(slot, gameTime);
                }
                if (search.preferred() && !usedEmptyPreferredSlotFallback && slotChecks < slots) {
                    usedEmptyPreferredSlotFallback = true;
                    forceSequentialItemFallback = true;
                    slotChecks++;
                    sourceEndpoint.resetItemSlotDiscoveryDeferral();
                }
                continue;
            }
            if (!sourceNode.allowsItem(sourceEndpoint.direction(), simulated)) {
                if (!independentDistributorProbes) {
                    sourceEndpoint.recordItemSlotRejected(slot, gameTime);
                }
                continue;
            }
            if (orderedPerItem) {
                int reserved = orderedMatchingNode.orderedMatchingReservedItems(
                        sourceEndpoint.direction(), slot, simulated);
                int available = OrderedMatchingPolicy.availableAfterDetention(
                        source.getStackInSlot(slot).getCount(), reserved);
                if (available <= 0) continue;
                if (simulated.getCount() > available) simulated = simulated.copyWithCount(available);
            }
            if (exactExcess > 0 && simulated.getCount() > exactExcess) simulated = simulated.copyWithCount(exactExcess);
            foundCandidate = true;
            sourceEndpoint.recordItemCandidateFound();
            int mappedTarget = orderedMatching && !orderedPerItem
                    ? OrderedMatchingPolicy.offsetTargetIndex(slot, targets.size(),
                            orderedMatchingOffset,
                            SkyLogisticsConfig.orderedMatchingWrapTargets()) : -1;
            if (orderedMatching && !orderedPerItem && mappedTarget < 0) {
                sourceEndpoint.recordItemFailure(gameTime);
                return operations;
            }
            List<CachedEndpoint> candidateTargets = orderedMatching && !orderedPerItem
                    ? List.of(targets.get(mappedTarget)) : targets;
            OrderedPerItemMoveResult orderedResult = orderedPerItem
                    ? tryMoveOrderedPerItem(sourceEndpoint, orderedMatchingNode, source, slot, simulated,
                            simulatedItem.forceExtractionSupported(), candidateTargets,
                            Math.max(1, budget - operations), gameTime) : null;
            MoveResult result = orderedResult == null
                    ? tryMoveItem(sourceEndpoint, source, slot, simulated,
                            simulatedItem.forceExtractionSupported(), candidateTargets,
                            Math.max(1, budget - operations), gameTime, Long.MAX_VALUE)
                    : new MoveResult(orderedResult.moved(), orderedResult.operations());
            operations += result.operations();
            if (result.moved()) {
                if (!independentDistributorProbes) {
                    sourceEndpoint.recordItemSlotSuccess(slot, slots, gameTime);
                }
                sourceEndpoint.recordItemSuccess();
                movedFromHotPath = true;
                successfulSlot = slot;
            }
            if (exactExcess >= 0) {
                SOURCE_EXACT_ITEM_SCANS.remove(sourceEndpoint);
                return operations;
            }
            if (orderedPerItem) {
                if (result.moved() || !orderedResult.continueSourceSearch()) return operations;
                continue;
            }
            if (orderedMatching && OrderedMatchingPolicy.stopAfterAttempt(result.moved(),
                    SkyLogisticsConfig.orderedMatchingContinueAfterTargetFailure())) return operations;
        }
        if (!independentDistributorProbes && movedFromHotPath
                && !usedEmptyPreferredSlotFallback && operations < budget
                && sourceEndpoint.shouldTryItemSlotDiscoveryAfterPreferred()) {
            operations += discoverAdditionalItemSlot(sourceEndpoint, sourceNode, source, slots, gameTime,
                    successfulSlot, budget - operations);
        }
        if (!foundCandidate) {
            if (orderedPerItem) {
                orderedMatchingNode.clearOrderedMatchingBatch(sourceEndpoint.direction());
                resetOrderedPerItemCursorIfIdle(sourceEndpoint, orderedMatchingNode, targets.size());
            }
            if (deferExhaustedDistributor(sourceEndpoint, source, gameTime)) return operations;
            if (exactExcess >= 0) SOURCE_EXACT_ITEM_SCANS.remove(sourceEndpoint);
            sourceEndpoint.recordItemSourceMiss(sourceSlotsExhausted ? slots : operations, slots, gameTime);
        }
        return operations;
    }

    private static boolean isExternalNetworkItemEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.node();
        return (blockEntity instanceof SkyMEInterfaceBlockEntity
                && AppliedEnergisticsCompat.isLoaded()
                && SkyLogisticsConfig.allowAe2ItemTransfer())
                || (blockEntity instanceof SkyRSInterfaceBlockEntity
                && RefinedStorageCompat.isLoaded()
                && SkyLogisticsConfig.allowRefinedStorageItemTransfer());
    }

    private static boolean deferExhaustedDistributor(CachedEndpoint endpoint, Object handler, long gameTime) {
        int retryTicks = DistributorWorkDefer.retryTicks(handler,
                SkyLogisticsConfig.distributorIndexingRetryTicks());
        if (retryTicks <= 0) return false;
        endpoint.deferItemsUntil(gameTime + retryTicks);
        return true;
    }

    private static boolean deferExhaustedDistributorFluid(CachedEndpoint endpoint, Object handler, long gameTime) {
        int retryTicks = DistributorWorkDefer.retryTicks(handler,
                SkyLogisticsConfig.distributorIndexingRetryTicks());
        if (retryTicks <= 0) return false;
        endpoint.deferFluidsUntil(gameTime + retryTicks);
        return true;
    }

    private static boolean deferExhaustedDistributorChemical(CachedEndpoint endpoint, Object handler, long gameTime) {
        int retryTicks = DistributorWorkDefer.retryTicks(handler,
                SkyLogisticsConfig.distributorIndexingRetryTicks());
        if (retryTicks <= 0) return false;
        endpoint.deferChemicalsUntil(gameTime + retryTicks);
        return true;
    }

    private static boolean deferExhaustedDistributorEnergy(CachedEndpoint endpoint, Object handler, long gameTime) {
        int retryTicks = DistributorWorkDefer.retryTicks(handler, SkyLogisticsConfig.distributorIndexingRetryTicks());
        if (retryTicks <= 0) return false;
        endpoint.deferEnergyUntil(gameTime + retryTicks);
        return true;
    }

    private static boolean deferExhaustedDistributorMana(CachedEndpoint endpoint, Object handler, long gameTime) {
        int retryTicks = DistributorWorkDefer.retryTicks(handler, SkyLogisticsConfig.distributorIndexingRetryTicks());
        if (retryTicks <= 0) return false;
        endpoint.deferManaUntil(gameTime + retryTicks);
        return true;
    }

    private static boolean deferExhaustedDistributorSource(CachedEndpoint endpoint, Object handler, long gameTime) {
        int retryTicks = DistributorWorkDefer.retryTicks(handler, SkyLogisticsConfig.distributorIndexingRetryTicks());
        if (retryTicks <= 0) return false;
        endpoint.deferSourceUntil(gameTime + retryTicks);
        return true;
    }

    private static boolean distributorWorkPending(Object handler) {
        return handler instanceof BudgetedDistributorHandler budgeted
                && (budgeted.distributorBudgetExhausted() || budgeted.distributorScanPending());
    }

    private static boolean usesIndependentDistributorProbes(Object handler) {
        return handler instanceof BudgetedDistributorHandler budgeted
                && budgeted.usesIndependentExtractionProbes();
    }

    private static int transferExternalNetworkItems(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets,
            int budget, long gameTime) {
        BlockEntity sourceBlockEntity = sourceEndpoint.node();
        LongItemEndpoint sourceLongEndpoint = longItemEndpoint(sourceEndpoint);
        if (sourceBlockEntity == null || sourceLongEndpoint == null || budget <= 0) {
            return 0;
        }
        ExternalWhitelistCandidates candidates = ((SkyNodeBlockEntity) sourceEndpoint.node())
                .externalWhitelistCandidates(sourceEndpoint.direction());
        if (!candidates.itemWhitelist() || candidates.itemSamples().isEmpty()) {
            sourceEndpoint.recordItemSourceMiss(0, 0, gameTime);
            return 0;
        }
        int operations = 0;
        boolean candidateFound = false;
        List<ItemStack> samples = candidates.itemSamples();
        for (int candidateOffset = 0; candidateOffset < samples.size(); candidateOffset++) {
            if (operations >= budget) {
                break;
            }
            ItemStack sample = samples.get(sourceEndpoint.nextExternalItemCandidate(samples.size()));
            operations++;
            LongItemResource resource = sourceLongEndpoint.resourceForStack(sample);
            if (resource.isEmpty()
                    || !sourceEndpoint.node().allowsItem(sourceEndpoint.direction(), resource.stack())) {
                continue;
            }
            candidateFound = true;
            sourceEndpoint.recordItemCandidateFound();
            MoveResult result = tryMoveLongItem(sourceEndpoint, sourceLongEndpoint, resource.stack(),
                    resource.amount(), targets, budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                sourceEndpoint.recordItemSuccess();
                return operations;
            }
        }
        if (!candidateFound) {
            sourceEndpoint.recordItemSourceMiss(operations, candidates.itemSamples().size(), gameTime);
        }
        return operations;
    }

    private static boolean isDimensionItemEndpoint(CachedEndpoint endpoint) {
        return endpoint.targetBlockEntity() instanceof BeyondDimensionsCompat.NetworkBoundHost;
    }

    private static int transferDimensionItems(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        BlockEntity sourceBlockEntity = sourceEndpoint.targetBlockEntity();
        if (!(sourceBlockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) || budget <= 0) {
            return 0;
        }
        DimensionDirectResult direct = tryTransferDimensionWhitelistItems(sourceEndpoint, sourceBlockEntity, targets,
                budget, gameTime);
        int operations = direct.operations();
        if (direct.moved()) {
            sourceEndpoint.recordItemSuccess();
            return operations;
        }
        if (!direct.scanFallback()) {
            if (!direct.candidateFound() && operations > 0) {
                sourceEndpoint.recordItemSourceMiss(operations, operations, gameTime);
            }
            return operations;
        }
        if (operations >= budget) {
            return operations;
        }
        NetworkEndpointBlockEntity sourceNode = sourceEndpoint.node();
        int slots = BeyondDimensionsCompat.itemTypeCount(sourceBlockEntity);
        if (slots <= 0) {
            sourceEndpoint.recordItemFailure(gameTime);
            return operations;
        }
        boolean foundCandidate = false;
        int slotChecks = Math.min(slots, sourceNode.getOperationRate());
        boolean usedEmptyPreferredSlotFallback = false;
        boolean forceSequentialItemFallback = false;
        int firstTriedSlot = -1;
        int secondTriedSlot = -1;
        boolean sourceSlotsExhausted = false;
        for (int i = 0; i < slotChecks && operations < budget; i++) {
            ItemSourceSearchResult search = forceSequentialItemFallback
                    ? nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                            firstTriedSlot, secondTriedSlot, false, budget - operations)
                    : nextItemSlot(sourceEndpoint, sourceNode, null, slots, gameTime,
                            firstTriedSlot, secondTriedSlot, budget - operations);
            forceSequentialItemFallback = false;
            operations += search.skippedChecks();
            int slot = search.index();
            if (slot < 0) {
                sourceSlotsExhausted = search.exhausted();
                break;
            }
            if (firstTriedSlot < 0) {
                firstTriedSlot = slot;
            } else {
                secondTriedSlot = slot;
            }
            BeyondDimensionsCompat.ItemResource resource = BeyondDimensionsCompat.itemResourceInSlot(sourceBlockEntity,
                    slot);
            operations++;
            if (resource.isEmpty()) {
                sourceEndpoint.recordItemSlotMiss(slot, gameTime);
                if (search.preferred() && !usedEmptyPreferredSlotFallback && slotChecks < slots) {
                    usedEmptyPreferredSlotFallback = true;
                    forceSequentialItemFallback = true;
                    slotChecks++;
                    sourceEndpoint.resetItemSlotDiscoveryDeferral();
                }
                continue;
            }
            ItemStack simulated = resource.stack();
            if (!sourceNode.allowsItem(sourceEndpoint.direction(), simulated)) {
                sourceEndpoint.recordItemSlotRejected(slot, gameTime);
                continue;
            }
            foundCandidate = true;
            sourceEndpoint.recordItemCandidateFound();
            MoveResult result = tryMoveDimensionItem(sourceEndpoint, sourceBlockEntity, simulated, resource.amount(),
                    targets, budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                sourceEndpoint.recordItemSlotSuccess(slot, slots, gameTime);
                sourceEndpoint.recordItemSuccess();
            }
        }
        if (!foundCandidate && !direct.candidateFound()) {
            sourceEndpoint.recordItemSourceMiss(sourceSlotsExhausted ? slots : operations, slots, gameTime);
        }
        return operations;
    }

    private static DimensionDirectResult tryTransferDimensionWhitelistItems(CachedEndpoint sourceEndpoint,
            BlockEntity sourceBlockEntity, List<CachedEndpoint> targets, int budget, long gameTime) {
        ExternalWhitelistCandidates candidates = ((SkyNodeBlockEntity) sourceEndpoint.node())
                .externalWhitelistCandidates(sourceEndpoint.direction());
        if (!candidates.itemWhitelist()) {
            return new DimensionDirectResult(false, 0, true, false);
        }
        int operations = 0;
        boolean candidateFound = false;
        List<ItemStack> samples = candidates.itemSamples();
        for (int candidateOffset = 0; candidateOffset < samples.size(); candidateOffset++) {
            if (operations >= budget) {
                break;
            }
            ItemStack sample = samples.get(sourceEndpoint.nextExternalItemCandidate(samples.size()));
            operations++;
            BeyondDimensionsCompat.ItemResource resource = BeyondDimensionsCompat.itemResourceForStack(
                    sourceBlockEntity, sample);
            if (resource.isEmpty() || !sourceEndpoint.node().allowsItem(sourceEndpoint.direction(), resource.stack())) {
                continue;
            }
            candidateFound = true;
            sourceEndpoint.recordItemCandidateFound();
            MoveResult result = tryMoveDimensionItem(sourceEndpoint, sourceBlockEntity, resource.stack(),
                    resource.amount(), targets, budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                return new DimensionDirectResult(true, operations, false, true);
            }
        }
        boolean scanFallback = false;
        for (TagKey<Item> tag : candidates.itemTags()) {
            if (operations >= budget) {
                break;
            }
            operations++;
            BeyondDimensionsCompat.ItemResource resource = BeyondDimensionsCompat.itemResourceForTag(
                    sourceBlockEntity, tag);
            if (resource.isEmpty()) {
                continue;
            }
            if (!sourceEndpoint.node().allowsItem(sourceEndpoint.direction(), resource.stack())) {
                scanFallback = true;
                continue;
            }
            candidateFound = true;
            sourceEndpoint.recordItemCandidateFound();
            MoveResult result = tryMoveDimensionItem(sourceEndpoint, sourceBlockEntity, resource.stack(),
                    resource.amount(), targets, budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                return new DimensionDirectResult(true, operations, false, true);
            }
            scanFallback = true;
        }
        return new DimensionDirectResult(false, operations, scanFallback, candidateFound);
    }

    private static MoveResult tryMoveDimensionItem(CachedEndpoint sourceEndpoint, BlockEntity sourceBlockEntity,
            ItemStack simulated, long available, List<CachedEndpoint> targets, int budget, long gameTime) {
        LongItemEndpoint sourceLongEndpoint = longItemEndpoint(sourceEndpoint);
        return sourceLongEndpoint == null ? new MoveResult(false, 0)
                : tryMoveLongItem(sourceEndpoint, sourceLongEndpoint, simulated, available, targets, budget, gameTime);
    }

    private static MoveResult tryMoveLongItem(CachedEndpoint sourceEndpoint, LongItemEndpoint sourceLongEndpoint,
            ItemStack simulated, long available, List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0 || simulated.isEmpty() || available <= 0L) {
            return new MoveResult(false, 0);
        }
        long skyContainerTransferLimit = sourceEndpoint.node()
                .limitItemTransfer(SkyLogisticsConfig.skyContainerTransferLimit());
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        int targetVisits = 0;
        int targetAttempts = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        int targetCount = targets.size();
        boolean singleTarget = targetCount == 1;
        ItemStackKey simulatedKey = singleTarget ? null : ItemStackKey.of(simulated);
        int targetIndex = singleTarget ? 0 : sourceEndpoint.itemTargetScanStart(simulatedKey, targetCount);
        targetLoop:
        for (int scannedTargets = 0; scannedTargets < targetCount; scannedTargets++) {
                CachedEndpoint targetEndpoint = targets.get(targetIndex);
                boolean orderedMatchingTarget = orderedMatchingTargetNode(targetEndpoint) != null;
                if (targetVisits >= budget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                targetVisits++;
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryItems(gameTime)
                        || !targetEndpoint.node().isItemsEnabled(targetEndpoint.direction())) {
                    targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    continue;
                }
                if (targetEndpoint.isItemFilterRejected(simulated, gameTime)) {
                    targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    continue;
                }
                if (!orderedMatchingTarget && targetEndpoint.hasActiveItemAcceptRejects(gameTime)) {
                    if (simulatedKey == null) simulatedKey = ItemStackKey.of(simulated);
                    if (targetEndpoint.isItemAcceptRejected(simulatedKey, gameTime)) {
                        targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                        continue;
                    }
                }
                if (targetAttempts >= targetAttemptBudget) {
                    if (!singleTarget) sourceEndpoint.resumeItemTargetScan(simulatedKey, targetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                targetAttempts++;
                operations++;
                int visitedTargetIndex = targetIndex;
                targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                if (!targetEndpoint.node().allowsItem(targetEndpoint.direction(), simulated)) {
                    targetEndpoint.recordItemFilterReject(simulated, gameTime);
                    continue;
                }
                LongItemEndpoint targetLongEndpoint = longItemEndpoint(targetEndpoint);
                if (targetLongEndpoint != null) {
                    long moved = moveLongItemStack(sourceEndpoint, sourceLongEndpoint, simulated, available,
                            targetEndpoint, targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        finishItemTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                        targetEndpoint.recordItemSuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordItemFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    if (simulatedKey == null) simulatedKey = ItemStackKey.of(simulated);
                    targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                HandlerMoveResult handlerResult = tryMoveLongItemToHandler(sourceEndpoint, sourceLongEndpoint,
                        simulated, available, targetEndpoint, targetEndpoint.itemHandler(gameTime), simulatedKey,
                        gameTime, Math.max(1, budget - Math.max(operations, targetVisits) + 1));
                targetVisits += Math.max(0, handlerResult.slotChecks() - 1);
                if (handlerResult.budgetExhausted()) {
                    if (!singleTarget) {
                        sourceEndpoint.resumeItemTargetScan(simulatedKey, visitedTargetIndex, targetCount);
                    }
                    budgetExhausted = true;
                    break targetLoop;
                }
                if (handlerResult.moved()) {
                    finishItemTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                    targetEndpoint.recordItemSuccess();
                    return targetMoveResult(true, operations, targetVisits);
                }
        }
        if (!singleTarget && !budgetExhausted) sourceEndpoint.resetItemTargetScan(simulatedKey);
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordItemFailure(gameTime);
        }
        return targetMoveResult(false, operations, targetVisits);
    }

    private static HandlerMoveResult tryMoveLongItemToHandler(CachedEndpoint sourceEndpoint,
            LongItemEndpoint sourceLongEndpoint,
            ItemStack simulated, long available, CachedEndpoint targetEndpoint, IItemHandler target,
            ItemStackKey simulatedKey, long gameTime, int slotCheckBudget) {
        if (target == null) {
            return HandlerMoveResult.NONE;
        }
        SlotLimitCheck slotLimitCheck = checkInsertionSlotLimit(targetEndpoint, target, simulated, slotCheckBudget);
        if (deferExhaustedDistributor(targetEndpoint, target, gameTime)) {
            return new HandlerMoveResult(false, slotLimitCheck.checks(), true);
        }
        if (!slotLimitCheck.complete()) {
            return new HandlerMoveResult(false, slotLimitCheck.checks(), true);
        }
        if (slotLimitCheck.blocked()) {
            return new HandlerMoveResult(false, slotLimitCheck.checks(), false);
        }
        int requested = (int) Math.min(Math.min(available, sourceEndpoint.node()
                .limitItemTransfer(SkyLogisticsConfig.nodeItemTransferLimit())), Integer.MAX_VALUE);
        if (requested <= 0) {
            return HandlerMoveResult.NONE;
        }
        ItemStack offer = simulated.copyWithCount(requested);
        int orderedTargetSlot = orderedMatchingTargetSlot(sourceEndpoint, targetEndpoint, target);
        if (orderedTargetSlot == -2) {
            return new HandlerMoveResult(false, slotLimitCheck.checks(), false);
        }
        TargetItemSelection selection = orderedTargetSlot >= 0
                ? fixedTargetItemSelection(targetEndpoint, target, offer, orderedTargetSlot)
                : selectTargetItemSlot(sourceEndpoint, targetEndpoint, target, offer,
                        Math.max(1, slotCheckBudget - slotLimitCheck.checks()));
        TargetItemSlot targetSlot = selection.slot();
        int movable = targetSlot.movable();
        if (movable <= 0) {
            if (deferExhaustedDistributor(targetEndpoint, target, gameTime)) {
                return new HandlerMoveResult(false,
                        slotLimitCheck.checks() + selection.checks(), true);
            }
            if (orderedTargetSlot < 0 && selection.exhaustive()) {
                if (simulatedKey == null) simulatedKey = ItemStackKey.of(simulated);
                targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
            }
            return new HandlerMoveResult(false, slotLimitCheck.checks() + selection.checks(), false);
        }
        long extractedAmount = sourceLongEndpoint.extract(-1, offer, movable, false);
        if (extractedAmount <= 0L) {
            sourceEndpoint.recordItemFailure(gameTime);
            return new HandlerMoveResult(false, slotLimitCheck.checks() + selection.checks(), false);
        }
        ItemStack extracted = offer.copyWithCount((int) extractedAmount);
        ItemStack leftover = target.insertItem(targetSlot.slot(), extracted, false);
        int inserted = extracted.getCount() - leftover.getCount();
        if (inserted > 0) {
            targetEndpoint.recordTargetItemSlotSuccess(targetSlot.lane(), targetSlot.slot(),
                    targetSlot.filledAfter(inserted, !leftover.isEmpty()), targetSlot.usedHot(), target.getSlots());
        } else {
            targetEndpoint.recordTargetItemSlotMiss(targetSlot.lane(), targetSlot.slot(), target.getSlots());
        }
        if (!leftover.isEmpty()) {
            long rolledBack = sourceLongEndpoint.insert(leftover, leftover.getCount(), false);
            if (rolledBack < leftover.getCount()) {
                SkyLogistics.LOGGER.warn(
                        "Item rollback failed after simulated target insertion changed during long item transfer. Source node {} face {}, target node {} face {}, extracted {}, target leftover {}, rollback remainder {}",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, leftover,
                        leftover.getCount() - rolledBack);
            }
        }
        return new HandlerMoveResult(true, slotLimitCheck.checks() + selection.checks(), false);
    }

    private static ItemSourceSearchResult nextItemSlot(CachedEndpoint sourceEndpoint,
            NetworkEndpointBlockEntity sourceNode, IItemHandler source,
            int slots, long gameTime, int firstTriedSlot, int secondTriedSlot, int skipBudget) {
        if (usesIndependentDistributorProbes(source)
                && source instanceof BudgetedDistributorHandler distributor) {
            int fairSlot = distributor.nextFairExtractionSlot(gameTime);
            if (fairSlot >= 0 && fairSlot < slots && !wasSlotTried(firstTriedSlot, secondTriedSlot, fairSlot)) {
                return new ItemSourceSearchResult(fairSlot, 0, false, false);
            }
            return new ItemSourceSearchResult(-1, 0, false, false);
        }
        int preferredSlot = sourceEndpoint.nextPreferredItemSlot(slots, gameTime, firstTriedSlot, secondTriedSlot);
        if (preferredSlot >= 0) {
            return new ItemSourceSearchResult(preferredSlot, 0, false, true);
        }
        if (sourceEndpoint.isItemSlotDiscoveryActive()) {
            ItemSourceSearchResult discovery = nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                    firstTriedSlot, secondTriedSlot, true, skipBudget);
            if (discovery.index() >= 0) {
                sourceEndpoint.recordItemSlotDiscoveryCheck();
            } else if (discovery.exhausted()) {
                sourceEndpoint.clearItemSlotDiscovery();
            }
            return discovery;
        }
        return nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                firstTriedSlot, secondTriedSlot, false, skipBudget);
    }

    private static int discoverAdditionalItemSlot(CachedEndpoint sourceEndpoint,
            NetworkEndpointBlockEntity sourceNode, IItemHandler source, int slots, long gameTime,
            int successfulSlot, int budget) {
        if (budget <= 0 || !sourceEndpoint.isItemSlotDiscoveryActive()) return 0;
        ItemSourceSearchResult search = nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                successfulSlot, -1, true, budget);
        int operations = search.skippedChecks();
        int slot = search.index();
        if (slot < 0) {
            if (search.exhausted()) sourceEndpoint.clearItemSlotDiscovery();
            return operations;
        }
        sourceEndpoint.recordItemSlotDiscoveryCheck();
        if (operations >= budget) return operations;
        ItemStack simulated = source.extractItem(slot, 1, true);
        operations++;
        if (simulated.isEmpty()) {
            if (deferExhaustedDistributor(sourceEndpoint, source, gameTime)) return operations;
            sourceEndpoint.recordItemSlotMiss(slot, gameTime);
        } else if (sourceNode.allowsItem(sourceEndpoint.direction(), simulated)) {
            sourceEndpoint.recordItemSlotSuccess(slot, slots, gameTime);
        } else {
            sourceEndpoint.recordItemSlotRejected(slot, gameTime);
        }
        return operations;
    }

    private static ItemSourceSearchResult nextSequentialItemSlot(CachedEndpoint sourceEndpoint,
            NetworkEndpointBlockEntity sourceNode, int slots, long gameTime, int firstTriedSlot, int secondTriedSlot,
            boolean ignoreEmptyCooldown, int skipBudget) {
        int skippedChecks = 0;
        int attemptLimit = Math.min(slots, SkyLogisticsConfig.sourceSearchAttemptsPerEndpoint());
        for (int attempts = 0; attempts < attemptLimit; attempts++) {
            int slot = sourceNode.nextItemStart(slots);
            if (wasSlotTried(firstTriedSlot, secondTriedSlot, slot)
                    || (!ignoreEmptyCooldown && !sourceEndpoint.canTryItemSlot(slot, gameTime))) {
                skippedChecks++;
                if (skippedChecks >= skipBudget) {
                    return new ItemSourceSearchResult(-1, skippedChecks, false, false);
                }
                continue;
            }
            return new ItemSourceSearchResult(slot, skippedChecks, false, false);
        }
        return new ItemSourceSearchResult(-1, skippedChecks, attemptLimit >= slots, false);
    }

    private static boolean wasSlotTried(int firstTriedSlot, int secondTriedSlot, int slot) {
        return firstTriedSlot == slot || secondTriedSlot == slot;
    }

    private static SlotLimitCheck checkExtractionSlotLimit(CachedEndpoint endpoint, IItemHandler source,
            int checkBudget) {
        NetworkEndpointBlockEntity node = endpoint.node();
        if (node instanceof SkyNodeBlockEntity skyNode && skyNode.isItemLimitByItems(endpoint.direction())) {
            SOURCE_SLOT_LIMIT_SCANS.remove(endpoint);
            return SlotLimitCheck.ALLOWED;
        }
        int limit = node.getItemSlotLimit(endpoint.direction());
        if (limit <= SkyNodeBlockEntity.ITEM_SLOT_LIMIT_UNLIMITED) {
            SOURCE_SLOT_LIMIT_SCANS.remove(endpoint);
            return SlotLimitCheck.ALLOWED;
        }
        int checks = 0;
        int slots = source.getSlots();
        SlotLimitScan scan = SOURCE_SLOT_LIMIT_SCANS.get(endpoint);
        if (scan == null || !scan.matches(slots, limit, null)) {
            scan = new SlotLimitScan(slots, limit, null);
            SOURCE_SLOT_LIMIT_SCANS.put(endpoint, scan);
        }
        while (scan.nextSlot < slots && checks < checkBudget) {
            int slot = scan.nextSlot++;
            checks++;
            ItemStack stack = source.getStackInSlot(slot);
            if (distributorWorkPending(source)) {
                scan.nextSlot--;
                return new SlotLimitCheck(false, checks, false);
            }
            if (!stack.isEmpty() && node.allowsItem(endpoint.direction(), stack) && ++scan.matchingSlots > limit) {
                SOURCE_SLOT_LIMIT_SCANS.remove(endpoint);
                return new SlotLimitCheck(false, checks, true);
            }
        }
        if (scan.nextSlot < slots) return new SlotLimitCheck(false, checks, false);
        SOURCE_SLOT_LIMIT_SCANS.remove(endpoint);
        return new SlotLimitCheck(true, checks, true);
    }

    private static ExactItemScanResult scanExactItems(CachedEndpoint endpoint, IItemHandler handler, int budget,
            Map<CachedEndpoint, ExactItemScan> scans) {
        if (handler instanceof ConstrainedDistributorItemHandler) {
            scans.remove(endpoint);
            return ExactItemScanResult.NOT_CONFIGURED;
        }
        if (!(endpoint.node() instanceof SkyNodeBlockEntity node)
                || !node.isItemLimitByItems(endpoint.direction())) {
            scans.remove(endpoint);
            return ExactItemScanResult.NOT_CONFIGURED;
        }
        int slots = handler.getSlots();
        ExactItemScan scan = scans.computeIfAbsent(endpoint, ignored -> new ExactItemScan(slots));
        if (scan.slots != slots) {
            scan = new ExactItemScan(slots);
            scans.put(endpoint, scan);
        }
        int checks = 0;
        while (scan.nextSlot < slots && checks < budget) {
            ItemStack stack = handler.getStackInSlot(scan.nextSlot++);
            checks++;
            if (distributorWorkPending(handler)) {
                scan.nextSlot--;
                return new ExactItemScanResult(false, scan.total, checks);
            }
            if (!stack.isEmpty() && node.allowsItem(endpoint.direction(), stack)) scan.total += stack.getCount();
        }
        return new ExactItemScanResult(scan.nextSlot >= slots, scan.total, checks);
    }

    private static SlotLimitCheck checkInsertionSlotLimit(CachedEndpoint endpoint, IItemHandler target,
            ItemStack candidate, int checkBudget) {
        if (target == null) {
            return SlotLimitCheck.ALLOWED;
        }
        if (target instanceof ConstrainedDistributorItemHandler) return SlotLimitCheck.ALLOWED;
        NetworkEndpointBlockEntity node = endpoint.node();
        net.minecraft.core.Direction direction = endpoint.direction();
        if (node instanceof SkyNodeBlockEntity skyNode && skyNode.isItemLimitByItems(direction)) return SlotLimitCheck.ALLOWED;
        int limit = node.getItemSlotLimit(direction);
        if (limit <= SkyNodeBlockEntity.ITEM_SLOT_LIMIT_UNLIMITED) {
            return SlotLimitCheck.ALLOWED;
        }
        int checks = 0;
        ItemStack probe = candidate.copy();
        probe.setCount(candidate.isEmpty() ? 0 : 1);
        boolean fillMaintainedSlots = SkyLogisticsConfig.fillMaintainedItemSlots();
        int slots = target.getSlots();
        ItemStackKey candidateKey = ItemStackKey.of(candidate);
        SlotLimitScan scan = TARGET_SLOT_LIMIT_SCANS.get(endpoint);
        if (scan == null || !scan.matches(slots, limit, candidateKey)) {
            scan = new SlotLimitScan(slots, limit, candidateKey);
            TARGET_SLOT_LIMIT_SCANS.put(endpoint, scan);
        }
        if (!fillMaintainedSlots) scan.canRefill = false;
        while (scan.nextSlot < slots && checks < checkBudget) {
            int slot = scan.nextSlot++;
            checks++;
            ItemStack existing = target.getStackInSlot(slot);
            if (existing.isEmpty() || !node.allowsItem(direction, existing)) {
                continue;
            }
            scan.matchingSlots++;
            if (MaintainedSlotPolicy.tracksRefillCandidate(fillMaintainedSlots)
                    && !scan.canRefill && !probe.isEmpty()
                    && ItemStack.isSameItemSameTags(existing, candidate)
                    && target.insertItem(slot, probe, true).getCount() < probe.getCount()) {
                scan.canRefill = true;
            }
            if (scan.matchingSlots >= limit && scan.canRefill) {
                TARGET_SLOT_LIMIT_SCANS.remove(endpoint);
                return new SlotLimitCheck(false, checks, true);
            }
        }
        if (scan.nextSlot < slots) return new SlotLimitCheck(false, checks, false);
        TARGET_SLOT_LIMIT_SCANS.remove(endpoint);
        return new SlotLimitCheck(scan.matchingSlots >= limit && !scan.canRefill, checks, true);
    }

    private static OrderedPerItemMoveResult retryOrderedPerItemDetentions(CachedEndpoint sourceEndpoint,
            SkyNodeBlockEntity sourceNode, IItemHandler source, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        int capacity = SkyLogisticsConfig.orderedMatchingPerItemDetentionQueueLength();
        sourceNode.trimOrderedMatchingDetentions(sourceEndpoint.direction(), capacity);
        int attempts = Math.min(sourceNode.orderedMatchingDetentionCount(sourceEndpoint.direction()),
                Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts()));
        int operations = 0;
        int movedItems = 0;
        boolean continueSourceSearch = true;
        for (int i = 0; i < attempts && operations < budget; i++) {
            SkyNodeBlockEntity.OrderedMatchingDetention detention =
                    sourceNode.peekOrderedMatchingDetention(sourceEndpoint.direction());
            if (detention == null) break;
            if (detention.sourceSlot() < 0 || detention.sourceSlot() >= source.getSlots()) {
                sourceNode.removeOrderedMatchingDetention(sourceEndpoint.direction(), detention);
                continue;
            }
            SimulatedItem simulatedItem = simulateSourceItem(sourceEndpoint, source, detention.sourceSlot(), 1);
            ItemStack simulated = simulatedItem.stack();
            operations++;
            if (simulated.isEmpty() || !detention.item().equals(ItemStackKey.of(simulated))
                    || !sourceNode.allowsItem(sourceEndpoint.direction(), simulated)) {
                sourceNode.removeOrderedMatchingDetention(sourceEndpoint.direction(), detention);
                continue;
            }
            int targetIndex = Math.floorMod(detention.targetIndex(), targets.size());
            MoveResult result = tryMoveItem(sourceEndpoint, source, detention.sourceSlot(), simulated.copyWithCount(1),
                    simulatedItem.forceExtractionSupported(), List.of(targets.get(targetIndex)),
                    Math.max(1, budget - operations), gameTime, 1L);
            operations += result.operations();
            if (result.moved()) {
                movedItems++;
                sourceNode.removeOrderedMatchingDetention(sourceEndpoint.direction(), detention);
                sourceNode.setOrderedMatchingCursor(sourceEndpoint.direction(), targetIndex + 1, targets.size());
            } else if (!SkyLogisticsConfig.orderedMatchingContinueAfterTargetFailure()) {
                continueSourceSearch = false;
                break;
            } else {
                sourceNode.rotateOrderedMatchingDetention(sourceEndpoint.direction(), detention);
            }
        }
        return new OrderedPerItemMoveResult(movedItems > 0, operations, continueSourceSearch, movedItems);
    }

    private static void resetOrderedPerItemCursorIfIdle(CachedEndpoint sourceEndpoint,
            SkyNodeBlockEntity sourceNode, int targetCount) {
        int detentionCount = sourceNode.orderedMatchingDetentionCount(sourceEndpoint.direction());
        boolean hasPendingBatch = sourceNode.hasOrderedMatchingBatch(sourceEndpoint.direction());
        if (OrderedMatchingPolicy.shouldResetPerItemCursor(false, detentionCount, hasPendingBatch)) {
            sourceNode.setOrderedMatchingCursor(sourceEndpoint.direction(), 0, targetCount);
        }
    }

    private static OrderedPerItemMoveResult tryMoveOrderedPerItem(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode,
            IItemHandler source, int slot, ItemStack simulated, boolean forceExtractionSupported,
            List<CachedEndpoint> targets, int budget, long gameTime) {
        int targetCount = targets.size();
        if (targetCount <= 0 || simulated.isEmpty() || budget <= 0) {
            return new OrderedPerItemMoveResult(false, 0, false, 0);
        }
        int cursor = sourceNode.getOrderedMatchingCursor(sourceEndpoint.direction(), targetCount);
        SkyNodeBlockEntity.OrderedMatchingBatch batch = sourceNode.prepareOrderedMatchingBatch(
                sourceEndpoint.direction(), slot, simulated, targetCount, cursor);
        OrderedMatchingPolicy.PerItemBatchPlan plan = batch.plan();
        int maxAttempts = Math.min(plan.remainingAssignments(),
                Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts()));
        int operations = 0;
        boolean moved = false;
        boolean continueSourceSearch = false;
        int detentionCapacity = SkyLogisticsConfig.orderedMatchingPerItemDetentionQueueLength();
        for (int attempt = 0; attempt < maxAttempts && (attempt == 0 || operations < budget); attempt++) {
            int targetIndex = plan.targetIndex();
            int amount = plan.amount();
            if (amount <= 0) break;
            ItemStack offer = simulated.copyWithCount(amount);
            MoveResult result = tryMoveItem(sourceEndpoint, source, slot, offer, forceExtractionSupported,
                    List.of(targets.get(targetIndex)), Math.max(1, budget - operations), gameTime, amount);
            operations += result.operations();
            if (result.moved()) {
                moved = true;
                sourceNode.setOrderedMatchingCursor(sourceEndpoint.direction(), targetIndex + 1, targetCount);
                plan.advance();
            } else {
                if (!SkyLogisticsConfig.orderedMatchingContinueAfterTargetFailure()
                        || !sourceNode.enqueueOrderedMatchingDetention(sourceEndpoint.direction(), slot, targetIndex,
                                simulated, detentionCapacity)) break;
                continueSourceSearch = true;
                sourceNode.setOrderedMatchingCursor(sourceEndpoint.direction(), targetIndex + 1, targetCount);
                plan.advance();
            }
        }
        if (plan.complete()) sourceNode.clearOrderedMatchingBatch(sourceEndpoint.direction());
        return new OrderedPerItemMoveResult(moved, operations, continueSourceSearch, 0);
    }

    private static MoveResult tryMoveItem(CachedEndpoint sourceEndpoint, IItemHandler source, int slot, ItemStack simulated,
            boolean forceExtractionSupported, List<CachedEndpoint> targets, int budget, long gameTime,
            long maxMoveAmount) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        LongItemEndpoint sourceLongEndpoint = longItemEndpoint(sourceEndpoint);
        long skyContainerTransferLimit = Math.min(maxMoveAmount, sourceEndpoint.node()
                .limitItemTransfer(SkyLogisticsConfig.skyContainerTransferLimit()));
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        int targetVisits = 0;
        int targetAttempts = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        int targetCount = targets.size();
        boolean singleTarget = targetCount == 1;
        ItemStackKey simulatedKey = singleTarget ? null : ItemStackKey.of(simulated);
        int targetIndex = singleTarget ? 0 : sourceEndpoint.itemTargetScanStart(simulatedKey, targetCount);
        targetLoop:
        for (int scannedTargets = 0; scannedTargets < targetCount; scannedTargets++) {
                CachedEndpoint targetEndpoint = targets.get(targetIndex);
                boolean orderedMatchingTarget = orderedMatchingTargetNode(targetEndpoint) != null;
                if (targetVisits >= budget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                targetVisits++;
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryItems(gameTime)
                        || !targetEndpoint.node().isItemsEnabled(targetEndpoint.direction())) {
                    targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    continue;
                }
                if (targetEndpoint.isItemFilterRejected(simulated, gameTime)) {
                    targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    continue;
                }
                if (!orderedMatchingTarget && targetEndpoint.hasActiveItemAcceptRejects(gameTime)) {
                    if (simulatedKey == null) simulatedKey = ItemStackKey.of(simulated);
                    if (targetEndpoint.isItemAcceptRejected(simulatedKey, gameTime)) {
                        targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                        continue;
                    }
                }
                if (targetAttempts >= targetAttemptBudget) {
                    if (!singleTarget) sourceEndpoint.resumeItemTargetScan(simulatedKey, targetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                targetAttempts++;
                operations++;
                int visitedTargetIndex = targetIndex;
                targetIndex = advanceItemTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                if (!targetEndpoint.node().allowsItem(targetEndpoint.direction(), simulated)) {
                    targetEndpoint.recordItemFilterReject(simulated, gameTime);
                    continue;
                }
                LongItemEndpoint targetLongEndpoint = longItemEndpoint(targetEndpoint);
                if (sourceLongEndpoint != null && targetLongEndpoint != null) {
                    long moved = moveLongItem(sourceEndpoint, sourceLongEndpoint, slot, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        finishItemTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                        targetEndpoint.recordItemSuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordItemFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    if (simulatedKey == null) simulatedKey = ItemStackKey.of(simulated);
                    targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                if (targetLongEndpoint != null) {
                    long moved = moveItemToLongTarget(sourceEndpoint, source, slot, simulated, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit, forceExtractionSupported);
                    if (moved > 0L) {
                        finishItemTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                        targetEndpoint.recordItemSuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordItemFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    if (simulatedKey == null) simulatedKey = ItemStackKey.of(simulated);
                    targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                IItemHandler target = targetEndpoint.itemHandler(gameTime);
                if (target == null) {
                    continue;
                }
                SlotLimitCheck slotLimitCheck = checkInsertionSlotLimit(targetEndpoint, target, simulated,
                        Math.max(1, budget - Math.max(operations, targetVisits) + 1));
                targetVisits += slotLimitCheck.checks();
                if (deferExhaustedDistributor(targetEndpoint, target, gameTime)) {
                    if (!singleTarget) {
                        sourceEndpoint.resumeItemTargetScan(simulatedKey, visitedTargetIndex, targetCount);
                    }
                    budgetExhausted = true;
                    break targetLoop;
                }
                if (!slotLimitCheck.complete()) {
                    if (!singleTarget) {
                        sourceEndpoint.resumeItemTargetScan(simulatedKey, visitedTargetIndex, targetCount);
                    }
                    budgetExhausted = true;
                    break targetLoop;
                }
                if (slotLimitCheck.blocked()) continue;
                ExactItemScanResult exactScan = scanExactItems(targetEndpoint, target,
                        Math.max(1, budget - Math.max(operations, targetVisits) + 1), TARGET_EXACT_ITEM_SCANS);
                targetVisits += Math.max(0, exactScan.checks() - 1);
                if (deferExhaustedDistributor(targetEndpoint, target, gameTime)) {
                    if (!singleTarget) {
                        sourceEndpoint.resumeItemTargetScan(simulatedKey, visitedTargetIndex, targetCount);
                    }
                    budgetExhausted = true;
                    break targetLoop;
                }
                if (!exactScan.complete()) {
                    if (!singleTarget) {
                        sourceEndpoint.resumeItemTargetScan(simulatedKey, visitedTargetIndex, targetCount);
                    }
                    budgetExhausted = true;
                    break targetLoop;
                }
                int exactRemaining = exactScan.total() < 0L ? -1
                        : (int)Math.min(Integer.MAX_VALUE,
                                Math.max(0L, (long)((SkyNodeBlockEntity)targetEndpoint.node())
                                        .getItemSlotLimit(targetEndpoint.direction())
                                        - exactScan.total()));
                TARGET_EXACT_ITEM_SCANS.remove(targetEndpoint);
                if (exactRemaining == 0) continue;
                ItemStack offer = exactRemaining > 0 && simulated.getCount() > exactRemaining
                        ? simulated.copyWithCount(exactRemaining) : simulated;
                int orderedTargetSlot = orderedMatchingTargetSlot(sourceEndpoint, targetEndpoint, target);
                if (orderedTargetSlot == -2) continue;
                TargetItemSelection selection = orderedTargetSlot >= 0
                        ? fixedTargetItemSelection(targetEndpoint, target, offer, orderedTargetSlot)
                        : selectTargetItemSlot(sourceEndpoint, targetEndpoint, target, offer,
                                Math.max(1, budget - Math.max(operations, targetVisits) + 1));
                targetVisits += Math.max(0, selection.checks() - 1);
                TargetItemSlot targetSlot = selection.slot();
                int movable = targetSlot.movable();
                if (movable <= 0) {
                    if (deferExhaustedDistributor(targetEndpoint, target, gameTime)) {
                        if (!singleTarget) {
                            sourceEndpoint.resumeItemTargetScan(simulatedKey, visitedTargetIndex, targetCount);
                        }
                        budgetExhausted = true;
                        break targetLoop;
                    }
                    if (orderedTargetSlot < 0 && selection.exhaustive()) {
                        if (simulatedKey == null) simulatedKey = ItemStackKey.of(simulated);
                        targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    }
                    continue;
                }
                ItemSlotTransfer transfer = transferItemSlot(sourceEndpoint, source, slot, targetEndpoint, target,
                        targetSlot.slot(), movable, forceExtractionSupported);
                if (!transfer.extracted()) {
                    sourceEndpoint.recordItemFailure(gameTime);
                    return targetMoveResult(false, operations, targetVisits);
                }
                int inserted = transfer.inserted();
                if (inserted > 0) {
                    targetEndpoint.recordTargetItemSlotSuccess(targetSlot.lane(), targetSlot.slot(),
                            targetSlot.filledAfter(inserted, transfer.hadLeftover()), targetSlot.usedHot(), target.getSlots());
                } else {
                    targetEndpoint.recordTargetItemSlotMiss(targetSlot.lane(), targetSlot.slot(), target.getSlots());
                }
                targetEndpoint.recordItemSuccess();
                finishItemTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                return targetMoveResult(true, operations, targetVisits);
        }
        if (!singleTarget && !budgetExhausted) sourceEndpoint.resetItemTargetScan(simulatedKey);
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordItemFailure(gameTime);
        }
        return targetMoveResult(false, operations, targetVisits);
    }

    private static TargetItemSelection selectTargetItemSlot(CachedEndpoint sourceEndpoint,
            CachedEndpoint endpoint, IItemHandler target, ItemStack stack, int checkBudget) {
        if (target instanceof ConstrainedDistributorItemHandler distributor) {
            endpoint.clearTargetItemCursor();
            int movable = distributor.planItemInsertion(stack,
                    distributorItemInsertContext(sourceEndpoint, endpoint),
                    candidate -> endpoint.node().allowsItem(endpoint.direction(), candidate));
            TargetItemSlot slot = movable <= 0 ? TargetItemSlot.NONE
                    : new TargetItemSlot(-1, 0, movable, 0, Integer.MAX_VALUE,
                            movable < stack.getCount(), false);
            return new TargetItemSelection(slot, 1, true);
        }
        int slots = target.getSlots();
        if (slots == 1) {
            endpoint.clearTargetItemCursor();
            return new TargetItemSelection(simulateSingleTargetItemSlot(target, stack), 1, true);
        }
        int lane = endpoint.targetItemCursorLane(stack, slots);
        int hotSlot = endpoint.targetItemHotSlot(lane, slots);
        int checks = 0;
        if (hotSlot >= 0) {
            TargetItemSlot candidate = simulateTargetItemSlot(endpoint, target, stack, lane, hotSlot, true);
            checks++;
            if (candidate.movable() > 0) return new TargetItemSelection(candidate, checks, false);
            endpoint.recordTargetItemHotMiss(lane, hotSlot, slots);
            if (checks >= checkBudget) return new TargetItemSelection(TargetItemSlot.NONE, checks, false);
        }
        int scanStart = endpoint.targetItemScanStart(lane, slots);
        TargetItemSlot emptyCandidate = TargetItemSlot.NONE;
        int scannedSlots = 0;
        int lastSlot = -1;
        for (int offset = 0; offset < slots && checks < checkBudget; offset++) {
            int slot = Math.floorMod(scanStart + offset, slots);
            if (slot == hotSlot) continue;
            scannedSlots++;
            lastSlot = slot;
            ItemStack existing = target.getStackInSlot(slot);
            TargetItemSlot candidate = simulateTargetItemSlot(endpoint, target, stack, lane, slot, false, existing);
            checks++;
            if (candidate.movable() <= 0) continue;
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stack)) {
                return new TargetItemSelection(candidate, checks, false);
            }
            if (emptyCandidate.movable() <= 0) emptyCandidate = candidate;
        }
        if (lastSlot >= 0) endpoint.recordTargetItemSlotMiss(lane, lastSlot, slots);
        boolean exhaustive = scannedSlots >= slots - (hotSlot >= 0 ? 1 : 0);
        return new TargetItemSelection(emptyCandidate, checks, exhaustive);
    }

    private static TargetItemSelection fixedTargetItemSelection(CachedEndpoint endpoint, IItemHandler target,
            ItemStack stack, int slot) {
        int lane = endpoint.targetItemCursorLane(stack, target.getSlots());
        return new TargetItemSelection(simulateTargetItemSlot(endpoint, target, stack, lane, slot, false), 1, true);
    }

    private static SkyNodeBlockEntity orderedMatchingTargetNode(CachedEndpoint endpoint) {
        return endpoint.node() instanceof SkyNodeBlockEntity node && node.hasOrderedMatchingUpgrade()
                && node.getOrderedMatchingMode() == OrderedMatchingMode.PER_SLOT ? node : null;
    }

    private static int orderedMatchingTargetSlot(CachedEndpoint sourceEndpoint, CachedEndpoint targetEndpoint,
            IItemHandler target) {
        if (target instanceof ConstrainedDistributorItemHandler) return -1;
        int targetSlots = target.getSlots();
        SkyNodeBlockEntity targetNode = orderedMatchingTargetNode(targetEndpoint);
        if (targetNode == null || targetSlots <= 0) return -1;
        int sourcePriorityIndex = itemSourcePriorityIndex(sourceEndpoint);
        if (sourcePriorityIndex < 0) return -2;
        int slot = OrderedMatchingPolicy.offsetPosition(sourcePriorityIndex,
                targetNode.getOrderedMatchingOffset(), targetSlots);
        return slot < 0 ? -2 : slot;
    }

    private static DistributorItemInsertContext distributorItemInsertContext(CachedEndpoint sourceEndpoint,
            CachedEndpoint targetEndpoint) {
        NetworkEndpointBlockEntity endpointNode = targetEndpoint.node();
        DistributorItemInsertContext.MaintainUnit unit = DistributorItemInsertContext.MaintainUnit.NONE;
        int amount = 0;
        if (endpointNode instanceof SkyNodeBlockEntity node) {
            amount = node.getItemSlotLimit(targetEndpoint.direction());
            if (amount > SkyNodeBlockEntity.ITEM_SLOT_LIMIT_UNLIMITED) {
                unit = node.isItemLimitByItems(targetEndpoint.direction())
                        ? DistributorItemInsertContext.MaintainUnit.ITEMS
                        : DistributorItemInsertContext.MaintainUnit.SLOTS;
            }
        }
        SkyNodeBlockEntity orderedNode = orderedMatchingTargetNode(targetEndpoint);
        boolean ordered = orderedNode != null;
        return new DistributorItemInsertContext(unit, amount, SkyLogisticsConfig.fillMaintainedItemSlots(),
                ordered, ordered ? itemSourcePriorityIndex(sourceEndpoint) : -1,
                ordered ? orderedNode.getOrderedMatchingOffset() : 0);
    }

    private static int itemSourcePriorityIndex(CachedEndpoint sourceEndpoint) {
        NetworkEndpointBlockEntity sourceNode = sourceEndpoint.node();
        if (!(sourceNode.getLevel() instanceof ServerLevel level)) return -1;
        List<CachedEndpoint> sources = sourceNode.hasDimensionUpgrade()
                ? SkyNetworkRegistry.globalItemInputs(level.getServer(), sourceNode.getLineId())
                : SkyNetworkRegistry.lineItemInputs(level.getServer(), level.dimension(), sourceNode.getLineId());
        return sources.indexOf(sourceEndpoint);
    }

    private static TargetItemSlot simulateSingleTargetItemSlot(IItemHandler target, ItemStack stack) {
        ItemStack remainder = target.insertItem(0, stack.copy(), true);
        int movable = stack.getCount() - remainder.getCount();
        if (movable <= 0) return TargetItemSlot.NONE;
        return new TargetItemSlot(-1, 0, movable, 0, Integer.MAX_VALUE, false, false);
    }

    private static TargetItemSlot simulateTargetItemSlot(CachedEndpoint endpoint, IItemHandler target, ItemStack stack, int lane,
            int slot, boolean usedHot) {
        return simulateTargetItemSlot(endpoint, target, stack, lane, slot, usedHot, target.getStackInSlot(slot));
    }

    private static TargetItemSlot simulateTargetItemSlot(CachedEndpoint endpoint, IItemHandler target, ItemStack stack, int lane,
            int slot, boolean usedHot, ItemStack existing) {
        ItemStack remainder = target.insertItem(slot, stack.copy(), true);
        int movable = stack.getCount() - remainder.getCount();
        if (movable <= 0) return TargetItemSlot.NONE;
        int existingCount = existing.isEmpty() ? 0 : existing.getCount();
        int effectiveLimit = SophisticatedStorageCompat.supports(endpoint.targetBlockEntity())
                ? target.getSlotLimit(slot)
                : Math.min(target.getSlotLimit(slot), stack.getMaxStackSize());
        return new TargetItemSlot(lane, slot, movable, existingCount, effectiveLimit,
                movable < stack.getCount(), usedHot);
    }

    private static SimulatedItem simulateSourceItem(CachedEndpoint endpoint, IItemHandler source, int slot,
            int transferLimit) {
        ItemStack simulated = source.extractItem(slot, transferLimit, true);
        boolean forceExtractionSupported = false;
        if (endpoint.node() instanceof SkyNodeBlockEntity node) {
            ForceExtractionCompat.FullSlotCandidate candidate = ForceExtractionCompat.fullSlotCandidate(node,
                    endpoint.targetBlockEntity(), source, slot, simulated, transferLimit);
            simulated = candidate.stack();
            forceExtractionSupported = candidate.forceExtractionSupported();
        }
        simulated = SophisticatedStorageCompat.fullSlotCandidate(
                endpoint.targetBlockEntity(), slot, simulated, transferLimit);
        return new SimulatedItem(simulated, forceExtractionSupported);
    }

    private static ItemSlotTransfer transferItemSlot(CachedEndpoint sourceEndpoint, IItemHandler source,
            int sourceSlot, CachedEndpoint targetEndpoint, IItemHandler target, int targetSlot, int amount,
            boolean forceExtractionSupported) {
        SourceExtraction extraction = extractSourceItem(sourceEndpoint, source, sourceSlot,
                source.getStackInSlot(sourceSlot), amount, forceExtractionSupported);
        ItemStack extracted = extraction.stack();
        if (extracted.isEmpty()) return new ItemSlotTransfer(false, 0, false);
        ItemStack leftover = target.insertItem(targetSlot, extracted, false);
        int inserted = extracted.getCount() - leftover.getCount();
        if (!leftover.isEmpty()) {
            ItemStack rollbackRemainder = rollbackSourceItem(sourceEndpoint, source, sourceSlot, extraction, leftover);
            if (!rollbackRemainder.isEmpty()) {
                SkyLogistics.LOGGER.warn(
                        "Item rollback failed after simulated target insertion changed during transfer. Source node {} face {}, target node {} face {}, source slot {}, extracted {}, target leftover {}, rollback remainder {}",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), sourceSlot, extracted,
                        leftover, rollbackRemainder);
            }
        }
        return new ItemSlotTransfer(true, inserted, !leftover.isEmpty());
    }

    private static SourceExtraction extractSourceItem(CachedEndpoint endpoint, IItemHandler source, int slot,
            ItemStack expected, int amount, boolean forceExtractionSupported) {
        ItemStack standard = source.extractItem(slot, amount, true);
        if (!standard.isEmpty() && standard.getCount() == amount
                && ItemStack.isSameItemSameTags(standard, expected)) {
            return new SourceExtraction(source.extractItem(slot, amount, false), DirectSource.NONE);
        }
        if (endpoint.node() instanceof SkyNodeBlockEntity node) {
            ForceExtractionCompat.DirectExtraction forced = forceExtractionSupported
                    ? ForceExtractionCompat.extractDirectSupported(source, slot, expected, amount)
                    : ForceExtractionCompat.extractDirect(node, endpoint.targetBlockEntity(), source, slot,
                            expected, amount);
            if (forced.supported()) return new SourceExtraction(forced.stack(), DirectSource.FORCED);
        }
        SophisticatedStorageCompat.DirectExtraction direct = SophisticatedStorageCompat.extractDirect(
                endpoint.targetBlockEntity(), slot, expected, amount);
        if (direct.supported()) return new SourceExtraction(direct.stack(), DirectSource.SOPHISTICATED);
        return new SourceExtraction(source.extractItem(slot, amount, false), DirectSource.NONE);
    }

    private static ItemStack rollbackSourceItem(CachedEndpoint endpoint, IItemHandler source, int slot,
            SourceExtraction extraction, ItemStack stack) {
        if (extraction.directSource() == DirectSource.FORCED
                && endpoint.node() instanceof SkyNodeBlockEntity node
                && ForceExtractionCompat.restoreDirect(node, endpoint.targetBlockEntity(), source, slot, stack)) {
            return ItemStack.EMPTY;
        }
        if (extraction.directSource() == DirectSource.SOPHISTICATED
                && SophisticatedStorageCompat.restoreDirect(endpoint.targetBlockEntity(), slot, stack)) {
            return ItemStack.EMPTY;
        }
        return ItemHandlerHelper.insertItemStacked(source, stack, false);
    }

    private static LongItemEndpoint longItemEndpoint(CachedEndpoint endpoint) {
        LongItemEndpointCache cached = LONG_ITEM_ENDPOINTS.get(endpoint);
        if (cached != null && cachedLongItemEndpointUsable(endpoint, cached.blockEntity())) {
            return cached.endpoint();
        }
        BlockEntity blockEntity = endpoint.node() instanceof SkyMEInterfaceBlockEntity
                || endpoint.node() instanceof SkyRSInterfaceBlockEntity
                ? endpoint.node() : endpoint.targetBlockEntity();
        boolean supported = blockEntity instanceof ItemVaultBlockEntity
                || blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost
                || (blockEntity instanceof SkyMEInterfaceBlockEntity
                && AppliedEnergisticsCompat.isLoaded()
                && SkyLogisticsConfig.allowAe2ItemTransfer())
                || (blockEntity instanceof SkyRSInterfaceBlockEntity
                && RefinedStorageCompat.isLoaded()
                && SkyLogisticsConfig.allowRefinedStorageItemTransfer());
        if (!supported) {
            LONG_ITEM_ENDPOINTS.remove(endpoint);
            return null;
        }
        if (cached != null && cached.blockEntity() == blockEntity) return cached.endpoint();
        LongItemEndpoint resolved;
        if (blockEntity instanceof ItemVaultBlockEntity vault) resolved = new ItemVaultLongEndpoint(vault);
        else if (blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
            resolved = new DimensionItemLongEndpoint(blockEntity);
        } else if (blockEntity instanceof SkyMEInterfaceBlockEntity) resolved = new Ae2ItemLongEndpoint(blockEntity);
        else resolved = new RefinedStorageItemLongEndpoint(blockEntity);
        LONG_ITEM_ENDPOINTS.put(endpoint, new LongItemEndpointCache(blockEntity, resolved));
        return resolved;
    }

    private static boolean cachedLongItemEndpointUsable(CachedEndpoint endpoint, BlockEntity blockEntity) {
        if (endpoint.node().isRemoved() || blockEntity.isRemoved()
                || blockEntity.getLevel() != endpoint.node().getLevel()) return false;
        if (blockEntity instanceof SkyMEInterfaceBlockEntity) {
            return AppliedEnergisticsCompat.isLoaded() && SkyLogisticsConfig.allowAe2ItemTransfer();
        }
        if (blockEntity instanceof SkyRSInterfaceBlockEntity) {
            return RefinedStorageCompat.isLoaded() && SkyLogisticsConfig.allowRefinedStorageItemTransfer();
        }
        return blockEntity instanceof ItemVaultBlockEntity
                || blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost;
    }

    private static long moveLongItem(CachedEndpoint sourceEndpoint, LongItemEndpoint sourceEndpointLong,
            int sourceSlot, CachedEndpoint targetEndpoint, LongItemEndpoint targetEndpointLong, long maxAmount) {
        if (sourceEndpointLong.sameStorage(targetEndpointLong)) {
            return 0L;
        }
        LongItemResource resource = sourceEndpointLong.resourceInSlot(sourceSlot);
        if (resource.isEmpty()) {
            return 0L;
        }
        long requested = Math.min(maxAmount, resource.amount());
        long accepted = targetEndpointLong.insert(resource.stack(), requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceEndpointLong.extract(sourceSlot, resource.stack(), accepted, false);
        if (extracted <= 0L) {
            return -1L;
        }
        long inserted = targetEndpointLong.insert(resource.stack(), extracted, false);
        if (inserted < extracted) {
            long rollback = extracted - inserted;
            long rolledBack = sourceEndpointLong.insert(resource.stack(), rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Item rollback failed after simulated long item insertion changed during transfer. Source node {} face {}, target node {} face {}, source slot {}, extracted {}, inserted {}, rollback remainder {}",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), sourceSlot, extracted,
                        inserted, rollback - rolledBack);
            }
        }
        return inserted;
    }

    private static long moveLongItemStack(CachedEndpoint sourceEndpoint, LongItemEndpoint sourceEndpointLong,
            ItemStack stack, long available, CachedEndpoint targetEndpoint, LongItemEndpoint targetEndpointLong,
            long maxAmount) {
        if (sourceEndpointLong.sameStorage(targetEndpointLong)) {
            return 0L;
        }
        long requested = Math.min(maxAmount, available);
        if (requested <= 0L || stack.isEmpty()) {
            return 0L;
        }
        long accepted = targetEndpointLong.insert(stack, requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceEndpointLong.extract(-1, stack, accepted, false);
        if (extracted <= 0L) {
            return -1L;
        }
        long inserted = targetEndpointLong.insert(stack, extracted, false);
        if (inserted < extracted) {
            long rollback = extracted - inserted;
            long rolledBack = sourceEndpointLong.insert(stack, rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Item rollback failed after simulated long item insertion changed during dimension transfer. Source node {} face {}, target node {} face {}, extracted {}, inserted {}, rollback remainder {}",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                        rollback - rolledBack);
            }
        }
        return inserted;
    }

    private static long moveItemToLongTarget(CachedEndpoint sourceEndpoint, IItemHandler source, int sourceSlot,
            ItemStack simulated, CachedEndpoint targetEndpoint, LongItemEndpoint targetEndpointLong, long maxAmount,
            boolean forceExtractionSupported) {
        long requested = Math.min(maxAmount, simulated.getCount());
        if (requested <= 0L) {
            return 0L;
        }
        long accepted = targetEndpointLong.insert(simulated, requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        int amount = (int) Math.min(Integer.MAX_VALUE, accepted);
        SourceExtraction extraction = extractSourceItem(sourceEndpoint, source, sourceSlot, simulated, amount,
                forceExtractionSupported);
        ItemStack extracted = extraction.stack();
        if (extracted.isEmpty()) return -1L;
        long inserted = targetEndpointLong.insert(extracted, extracted.getCount(), false);
        if (inserted < extracted.getCount()) {
            ItemStack rollback = extracted.copyWithCount((int) (extracted.getCount() - inserted));
            ItemStack rollbackRemainder = rollbackSourceItem(sourceEndpoint, source, sourceSlot, extraction, rollback);
            if (!rollbackRemainder.isEmpty()) {
                SkyLogistics.LOGGER.warn(
                        "Item rollback failed after simulated long target insertion changed during transfer. Source node {} face {}, target node {} face {}, source slot {}, extracted {}, inserted {}, rollback remainder {}",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), sourceSlot, extracted,
                        inserted, rollbackRemainder);
            }
        }
        return inserted;
    }

    private interface LongItemEndpoint {
        LongItemResource resourceInSlot(int slot);

        default LongItemResource resourceForStack(ItemStack stack) {
            return LongItemResource.EMPTY;
        }

        long insert(ItemStack stack, long amount, boolean simulate);

        long extract(int slot, ItemStack stack, long amount, boolean simulate);

        boolean sameStorage(LongItemEndpoint other);
    }

    private record LongItemEndpointCache(BlockEntity blockEntity, LongItemEndpoint endpoint) {
    }

    private record LongItemResource(ItemStack stack, long amount) {
        private static final LongItemResource EMPTY = new LongItemResource(ItemStack.EMPTY, 0L);

        private boolean isEmpty() {
            return stack.isEmpty() || amount <= 0L;
        }
    }

    private record ItemVaultLongEndpoint(ItemVaultBlockEntity vault) implements LongItemEndpoint {
        @Override
        public LongItemResource resourceInSlot(int slot) {
            ItemVaultBlockEntity.StoredItem stored = vault.storedItemInSlot(slot);
            return stored.stack().isEmpty() || stored.amount() <= 0L
                    ? LongItemResource.EMPTY
                    : new LongItemResource(stored.stack(), stored.amount());
        }

        @Override
        public long insert(ItemStack stack, long amount, boolean simulate) {
            return vault.insertStoredItem(stack, amount, simulate);
        }

        @Override
        public long extract(int slot, ItemStack stack, long amount, boolean simulate) {
            return vault.extractStoredItem(slot, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongItemEndpoint other) {
            return other instanceof ItemVaultLongEndpoint endpoint && vault == endpoint.vault;
        }
    }

    private record DimensionItemLongEndpoint(BlockEntity blockEntity) implements LongItemEndpoint {
        @Override
        public LongItemResource resourceInSlot(int slot) {
            BeyondDimensionsCompat.ItemResource resource = BeyondDimensionsCompat.itemResourceInSlot(blockEntity,
                    slot);
            return resource.isEmpty() ? LongItemResource.EMPTY : new LongItemResource(resource.stack(),
                    resource.amount());
        }

        @Override
        public long insert(ItemStack stack, long amount, boolean simulate) {
            return BeyondDimensionsCompat.insertItem(blockEntity, stack, amount, simulate);
        }

        @Override
        public long extract(int slot, ItemStack stack, long amount, boolean simulate) {
            return BeyondDimensionsCompat.extractItem(blockEntity, stack, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongItemEndpoint other) {
            return other instanceof DimensionItemLongEndpoint endpoint
                    && sameDimensionNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private record Ae2ItemLongEndpoint(BlockEntity blockEntity) implements LongItemEndpoint {
        @Override
        public LongItemResource resourceInSlot(int slot) {
            return LongItemResource.EMPTY;
        }

        @Override
        public LongItemResource resourceForStack(ItemStack stack) {
            AppliedEnergisticsCompat.ItemResource resource = AppliedEnergisticsCompat.itemResourceForStack(
                    blockEntity, stack);
            return resource.isEmpty() ? LongItemResource.EMPTY
                    : new LongItemResource(resource.stack(), resource.amount());
        }

        @Override
        public long insert(ItemStack stack, long amount, boolean simulate) {
            return AppliedEnergisticsCompat.insertItem(blockEntity, stack, amount, simulate);
        }

        @Override
        public long extract(int slot, ItemStack stack, long amount, boolean simulate) {
            return AppliedEnergisticsCompat.extractItem(blockEntity, stack, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongItemEndpoint other) {
            return other instanceof Ae2ItemLongEndpoint endpoint
                    && AppliedEnergisticsCompat.sameNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private record RefinedStorageItemLongEndpoint(BlockEntity blockEntity) implements LongItemEndpoint {
        @Override
        public LongItemResource resourceInSlot(int slot) {
            return LongItemResource.EMPTY;
        }

        @Override
        public LongItemResource resourceForStack(ItemStack stack) {
            RefinedStorageCompat.ItemResource resource = RefinedStorageCompat.itemResourceForStack(blockEntity,
                    stack);
            return resource.isEmpty() ? LongItemResource.EMPTY
                    : new LongItemResource(resource.stack(), resource.amount());
        }

        @Override
        public long insert(ItemStack stack, long amount, boolean simulate) {
            return RefinedStorageCompat.insertItem(blockEntity, stack, amount, simulate);
        }

        @Override
        public long extract(int slot, ItemStack stack, long amount, boolean simulate) {
            return RefinedStorageCompat.extractItem(blockEntity, stack, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongItemEndpoint other) {
            return other instanceof RefinedStorageItemLongEndpoint endpoint
                    && RefinedStorageCompat.sameNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private static int transferFluids(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        configureMaintainedBackoff(sourceEndpoint, targets,
                SkyLogisticsConfig.enableMaintainedFluidPolling(), CachedEndpoint::setFluidMaintainedBackoff);
        if (isExternalNetworkFluidEndpoint(sourceEndpoint)) {
            return transferExternalNetworkFluids(sourceEndpoint, targets, budget, gameTime);
        }
        NetworkEndpointBlockEntity sourceNode = sourceEndpoint.node();
        IFluidHandler source = sourceEndpoint.fluidHandler(gameTime);
        if (source == null || budget <= 0) {
            return 0;
        }
        int tanks = source.getTanks();
        if (deferExhaustedDistributorFluid(sourceEndpoint, source, gameTime)) return 0;
        if (tanks <= 0) {
            sourceEndpoint.recordFluidFailure(gameTime);
            return 0;
        }
        int operations = 0;
        boolean foundCandidate = false;
        boolean independentDistributorProbes = usesIndependentDistributorProbes(source);
        int tankChecks = Math.min(tanks,
                Math.min(sourceNode.getOperationRate(), SkyLogisticsConfig.externalTankScansPerEndpoint()));
        if (independentDistributorProbes && source instanceof BudgetedDistributorHandler distributor) {
            tankChecks = Math.min(tanks, Math.max(tankChecks, distributor.fairExtractionProbesDue(gameTime)));
        }
        int firstTriedTank = -1;
        int secondTriedTank = -1;
        boolean sourceTanksExhausted = false;
        for (int i = 0; i < tankChecks && operations < budget; i++) {
            SourceSearchResult search = nextFluidTank(sourceEndpoint, sourceNode, tanks, gameTime,
                    firstTriedTank, secondTriedTank, budget - operations);
            operations += search.skippedChecks();
            int tank = search.index();
            if (tank < 0) {
                sourceTanksExhausted = search.exhausted();
                break;
            }
            if (firstTriedTank < 0) {
                firstTriedTank = tank;
            } else {
                secondTriedTank = tank;
            }
            FluidStack inTank = source.getFluidInTank(tank);
            operations++;
            if (inTank.isEmpty()) {
                if (deferExhaustedDistributorFluid(sourceEndpoint, source, gameTime)) return operations;
                if (!independentDistributorProbes) sourceEndpoint.recordFluidTankMiss(tank, gameTime);
                continue;
            }
            int transferLimit = (int) Math.min(Integer.MAX_VALUE,
                    sourceNode.limitFluidTransfer(Integer.MAX_VALUE));
            FluidStack simulated = source.drain(copyWithAmount(inTank, transferLimit),
                    IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) {
                if (deferExhaustedDistributorFluid(sourceEndpoint, source, gameTime)) return operations;
                if (!independentDistributorProbes) sourceEndpoint.recordFluidTankMiss(tank, gameTime);
                continue;
            }
            if (!sourceNode.allowsFluid(sourceEndpoint.direction(), simulated)) {
                if (!independentDistributorProbes) sourceEndpoint.recordFluidTankRejected(tank, gameTime);
                continue;
            }
            foundCandidate = true;
            sourceEndpoint.recordFluidCandidateFound();
            MoveResult result = tryMoveFluid(sourceEndpoint, source, tank, simulated, targets,
                    budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                if (!independentDistributorProbes) sourceEndpoint.recordFluidTankSuccess(tank, tanks);
                sourceEndpoint.recordFluidSuccess();
            }
        }
        if (!foundCandidate) {
            if (deferExhaustedDistributorFluid(sourceEndpoint, source, gameTime)) return operations;
            sourceEndpoint.recordFluidSourceMiss(sourceTanksExhausted ? tanks : operations, tanks, gameTime);
        }
        return operations;
    }

    private static boolean isExternalNetworkFluidEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.node();
        return (blockEntity instanceof SkyMEInterfaceBlockEntity
                && AppliedEnergisticsCompat.isLoaded()
                && SkyLogisticsConfig.allowAe2FluidTransfer())
                || (blockEntity instanceof SkyRSInterfaceBlockEntity
                && RefinedStorageCompat.isLoaded()
                && SkyLogisticsConfig.allowRefinedStorageFluidTransfer());
    }

    private static int transferExternalNetworkFluids(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets,
            int budget, long gameTime) {
        BlockEntity sourceBlockEntity = sourceEndpoint.node();
        LongFluidEndpoint sourceLongEndpoint = longFluidEndpoint(sourceEndpoint);
        if (sourceBlockEntity == null || sourceLongEndpoint == null || budget <= 0) {
            return 0;
        }
        ExternalWhitelistCandidates candidates = ((SkyNodeBlockEntity) sourceEndpoint.node())
                .externalWhitelistCandidates(sourceEndpoint.direction());
        if (!candidates.fluidWhitelist() || candidates.fluidSamples().isEmpty()) {
            sourceEndpoint.recordFluidSourceMiss(0, 0, gameTime);
            return 0;
        }
        int operations = 0;
        boolean candidateFound = false;
        List<FluidStack> samples = candidates.fluidSamples();
        for (int candidateOffset = 0; candidateOffset < samples.size(); candidateOffset++) {
            if (operations >= budget) {
                break;
            }
            FluidStack sample = samples.get(sourceEndpoint.nextExternalFluidCandidate(samples.size()));
            operations++;
            LongFluidResource resource = externalNetworkFluidResourceForStack(sourceBlockEntity, sample);
            if (resource.isEmpty()
                    || !sourceEndpoint.node().allowsFluid(sourceEndpoint.direction(), resource.stack())) {
                continue;
            }
            candidateFound = true;
            sourceEndpoint.recordFluidCandidateFound();
            MoveResult result = tryMoveLongFluid(sourceEndpoint, sourceLongEndpoint, resource.stack(),
                    resource.amount(), targets, budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                sourceEndpoint.recordFluidSuccess();
                return operations;
            }
        }
        if (!candidateFound) {
            sourceEndpoint.recordFluidSourceMiss(operations, candidates.fluidSamples().size(), gameTime);
        }
        return operations;
    }

    private static LongFluidResource externalNetworkFluidResourceForStack(BlockEntity blockEntity, FluidStack sample) {
        if (blockEntity instanceof SkyMEInterfaceBlockEntity) {
            AppliedEnergisticsCompat.FluidResource resource = AppliedEnergisticsCompat.fluidResourceForStack(
                    blockEntity, sample);
            return resource.isEmpty() ? LongFluidResource.EMPTY
                    : new LongFluidResource(resource.stack(), resource.amount());
        }
        if (blockEntity instanceof SkyRSInterfaceBlockEntity) {
            RefinedStorageCompat.FluidResource resource = RefinedStorageCompat.fluidResourceForStack(blockEntity,
                    sample);
            return resource.isEmpty() ? LongFluidResource.EMPTY
                    : new LongFluidResource(resource.stack(), resource.amount());
        }
        return LongFluidResource.EMPTY;
    }

    private static SourceSearchResult nextFluidTank(CachedEndpoint sourceEndpoint, NetworkEndpointBlockEntity sourceNode,
            int tanks, long gameTime, int firstTriedTank, int secondTriedTank, int skipBudget) {
        IFluidHandler handler = sourceEndpoint.fluidHandler(gameTime);
        if (usesIndependentDistributorProbes(handler) && handler instanceof BudgetedDistributorHandler distributor) {
            int fairTank = distributor.nextFairExtractionSlot(gameTime);
            return fairTank >= 0 && fairTank < tanks && !wasSlotTried(firstTriedTank, secondTriedTank, fairTank)
                    ? new SourceSearchResult(fairTank, 0, false) : new SourceSearchResult(-1, 0, false);
        }
        if (sourceEndpoint.shouldTryFluidTankDiscoveryBeforePreferred()) {
            SourceSearchResult discovery = nextSequentialFluidTank(sourceEndpoint, sourceNode, tanks, gameTime,
                    firstTriedTank, secondTriedTank, true, skipBudget);
            if (discovery.index() >= 0) {
                sourceEndpoint.recordFluidTankDiscoveryCheck();
                return discovery;
            }
            if (discovery.exhausted()) {
                sourceEndpoint.clearFluidTankDiscovery();
            } else {
                return discovery;
            }
        }
        int preferredTank = sourceEndpoint.nextPreferredFluidTank(tanks, gameTime, firstTriedTank, secondTriedTank);
        if (preferredTank >= 0) {
            return new SourceSearchResult(preferredTank, 0, false);
        }
        if (sourceEndpoint.isFluidTankDiscoveryActive()) {
            SourceSearchResult discovery = nextSequentialFluidTank(sourceEndpoint, sourceNode, tanks, gameTime,
                    firstTriedTank, secondTriedTank, true, skipBudget);
            if (discovery.index() >= 0) {
                sourceEndpoint.recordFluidTankDiscoveryCheck();
            } else if (discovery.exhausted()) {
                sourceEndpoint.clearFluidTankDiscovery();
            }
            return discovery;
        }
        return nextSequentialFluidTank(sourceEndpoint, sourceNode, tanks, gameTime,
                firstTriedTank, secondTriedTank, false, skipBudget);
    }

    private static SourceSearchResult nextSequentialFluidTank(CachedEndpoint sourceEndpoint,
            NetworkEndpointBlockEntity sourceNode, int tanks, long gameTime, int firstTriedTank, int secondTriedTank,
            boolean ignoreEmptyCooldown, int skipBudget) {
        int skippedChecks = 0;
        int attemptLimit = Math.min(tanks, SkyLogisticsConfig.sourceSearchAttemptsPerEndpoint());
        for (int attempts = 0; attempts < attemptLimit; attempts++) {
            int tank = sourceNode.nextFluidStart(tanks);
            if (wasSlotTried(firstTriedTank, secondTriedTank, tank)
                    || (!ignoreEmptyCooldown && !sourceEndpoint.canTryFluidTank(tank, gameTime))) {
                skippedChecks++;
                if (skippedChecks >= skipBudget) {
                    return new SourceSearchResult(-1, skippedChecks, false);
                }
                continue;
            }
            return new SourceSearchResult(tank, skippedChecks, false);
        }
        return new SourceSearchResult(-1, skippedChecks, attemptLimit >= tanks);
    }

    private static MoveResult tryMoveFluid(CachedEndpoint sourceEndpoint, IFluidHandler source, int sourceTank,
            FluidStack simulated, List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        LongFluidEndpoint sourceLongEndpoint = longFluidEndpoint(sourceEndpoint);
        long skyContainerTransferLimit = sourceEndpoint.node()
                .limitFluidTransfer(SkyLogisticsConfig.skyContainerTransferLimit());
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        int targetVisits = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        FluidStackKey simulatedKey = FluidStackKey.of(simulated);
        int targetCount = targets.size();
        int targetIndex = sourceEndpoint.fluidTargetScanStart(simulatedKey, targetCount);
        targetLoop:
        for (int scannedTargets = 0; scannedTargets < targetCount; scannedTargets++) {
                CachedEndpoint targetEndpoint = targets.get(targetIndex);
                if (targetVisits >= budget) { budgetExhausted = true; break; }
                targetVisits++;
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    targetIndex = advanceFluidTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryFluids(gameTime)
                        || !targetEndpoint.node().isFluidsEnabled(targetEndpoint.direction())) {
                    targetIndex = advanceFluidTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    continue;
                }
                if (targetEndpoint.isFluidAcceptRejected(simulatedKey, gameTime)) {
                    targetIndex = advanceFluidTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    sourceEndpoint.resumeFluidTargetScan(simulatedKey, targetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                int visitedTargetIndex = targetIndex;
                targetIndex = advanceFluidTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                if (!targetEndpoint.node().allowsFluid(targetEndpoint.direction(), simulated)) {
                    targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                LongFluidEndpoint targetLongEndpoint = longFluidEndpoint(targetEndpoint);
                if (targetLongEndpoint != null) {
                    long moved = sourceLongEndpoint != null
                            ? moveLongFluid(sourceEndpoint, sourceLongEndpoint, sourceTank, targetEndpoint,
                                    targetLongEndpoint, skyContainerTransferLimit)
                            : moveFluidToLongTarget(sourceEndpoint, source, sourceTank, simulated, targetEndpoint,
                                    targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        finishFluidTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                        targetEndpoint.recordFluidSuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordFluidFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                IFluidHandler target = targetEndpoint.fluidHandler(gameTime);
                if (target == null) {
                    continue;
                }
                FluidStack maintainedOffer = copyWithAmount(simulated,
                        (int) maintainedFluidAllowance(targetEndpoint, target, simulated.getAmount()));
                int accepted = target.fill(maintainedOffer, IFluidHandler.FluidAction.SIMULATE);
                if (accepted <= 0) {
                    if (deferExhaustedDistributorFluid(targetEndpoint, target, gameTime)) {
                        sourceEndpoint.resumeFluidTargetScan(simulatedKey, visitedTargetIndex, targetCount);
                        budgetExhausted = true;
                        break targetLoop;
                    }
                    targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                FluidStack drained = source.drain(copyWithAmount(simulated, accepted), IFluidHandler.FluidAction.EXECUTE);
                if (drained.isEmpty()) {
                    if (deferExhaustedDistributorFluid(sourceEndpoint, source, gameTime)) {
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    sourceEndpoint.recordFluidFailure(gameTime);
                    return targetMoveResult(false, operations, targetVisits);
                }
                int inserted = target.fill(drained.copy(), IFluidHandler.FluidAction.EXECUTE);
                if (inserted < drained.getAmount()) {
                    FluidStack rollback = copyWithAmount(drained, drained.getAmount() - inserted);
                    int rolledBack = source.fill(rollback, IFluidHandler.FluidAction.EXECUTE);
                    if (rolledBack < rollback.getAmount()) {
                        SkyLogistics.LOGGER.warn(
                                "Fluid rollback failed after simulated target fill changed during transfer. Source node {} face {}, target node {} face {}, source tank {}, drained {}, inserted {}, rollback remainder {} mB",
                                sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                                targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), sourceTank, drained,
                                inserted, rollback.getAmount() - rolledBack);
                    }
                }
                targetEndpoint.recordFluidSuccess();
                finishFluidTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                return targetMoveResult(true, operations, targetVisits);
        }
        if (!budgetExhausted) sourceEndpoint.resetFluidTargetScan(simulatedKey);
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordFluidFailure(gameTime);
        }
        return targetMoveResult(false, operations, targetVisits);
    }

    private static MoveResult tryMoveLongFluid(CachedEndpoint sourceEndpoint, LongFluidEndpoint sourceLongEndpoint,
            FluidStack simulated, long available, List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0 || simulated.isEmpty() || available <= 0L) {
            return new MoveResult(false, 0);
        }
        long skyContainerTransferLimit = sourceEndpoint.node()
                .limitFluidTransfer(SkyLogisticsConfig.skyContainerTransferLimit());
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        int targetVisits = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        FluidStackKey simulatedKey = FluidStackKey.of(simulated);
        int targetCount = targets.size();
        int targetIndex = sourceEndpoint.fluidTargetScanStart(simulatedKey, targetCount);
        targetLoop:
        for (int scannedTargets = 0; scannedTargets < targetCount; scannedTargets++) {
                CachedEndpoint targetEndpoint = targets.get(targetIndex);
                if (targetVisits >= budget) { budgetExhausted = true; break; }
                targetVisits++;
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    targetIndex = advanceFluidTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryFluids(gameTime)
                        || !targetEndpoint.node().isFluidsEnabled(targetEndpoint.direction())) {
                    targetIndex = advanceFluidTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                    continue;
                }
                if (targetEndpoint.hasActiveFluidAcceptRejects(gameTime)) {
                    if (targetEndpoint.isFluidAcceptRejected(simulatedKey, gameTime)) {
                        targetIndex = advanceFluidTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                        continue;
                    }
                }
                if (operations >= targetAttemptBudget) {
                    sourceEndpoint.resumeFluidTargetScan(simulatedKey, targetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                int visitedTargetIndex = targetIndex;
                targetIndex = advanceFluidTargetScan(sourceEndpoint, simulatedKey, targetIndex, targetCount);
                if (!targetEndpoint.node().allowsFluid(targetEndpoint.direction(), simulated)) {
                    if (simulatedKey == null) simulatedKey = FluidStackKey.of(simulated);
                    targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                LongFluidEndpoint targetLongEndpoint = longFluidEndpoint(targetEndpoint);
                if (targetLongEndpoint != null) {
                    long moved = moveLongFluidStack(sourceEndpoint, sourceLongEndpoint, simulated, available,
                            targetEndpoint, targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        finishFluidTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                        targetEndpoint.recordFluidSuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordFluidFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                if (tryMoveLongFluidToHandler(sourceEndpoint, sourceLongEndpoint, simulated, available,
                        targetEndpoint, targetEndpoint.fluidHandler(gameTime), simulatedKey, gameTime)) {
                    finishFluidTargetScan(sourceEndpoint, simulatedKey, targets, visitedTargetIndex);
                    targetEndpoint.recordFluidSuccess();
                    return targetMoveResult(true, operations, targetVisits);
                }
        }
        if (!budgetExhausted) sourceEndpoint.resetFluidTargetScan(simulatedKey);
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordFluidFailure(gameTime);
        }
        return targetMoveResult(false, operations, targetVisits);
    }

    private static boolean tryMoveLongFluidToHandler(CachedEndpoint sourceEndpoint,
            LongFluidEndpoint sourceLongEndpoint, FluidStack simulated, long available, CachedEndpoint targetEndpoint,
            IFluidHandler target, FluidStackKey simulatedKey, long gameTime) {
        if (target == null || simulated.isEmpty() || available <= 0L) {
            return false;
        }
        int requested = (int) Math.min(Math.min(available,
                sourceEndpoint.node().limitFluidTransfer(Integer.MAX_VALUE)), Integer.MAX_VALUE);
        if (requested <= 0) {
            return false;
        }
        FluidStack offer = copyWithAmount(simulated, requested);
        offer.setAmount((int) maintainedFluidAllowance(targetEndpoint, target, offer.getAmount()));
        int accepted = target.fill(offer.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
            if (deferExhaustedDistributorFluid(targetEndpoint, target, gameTime)) return false;
            if (simulatedKey == null) simulatedKey = FluidStackKey.of(simulated);
            targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
            return false;
        }
        long extractedAmount = sourceLongEndpoint.extract(-1, offer, accepted, false);
        if (extractedAmount <= 0L) {
            sourceEndpoint.recordFluidFailure(gameTime);
            return false;
        }
        FluidStack extracted = copyWithAmount(offer, (int) Math.min(extractedAmount, Integer.MAX_VALUE));
        int inserted = target.fill(extracted.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (inserted < extracted.getAmount()) {
            FluidStack rollback = copyWithAmount(extracted, extracted.getAmount() - inserted);
            long rolledBack = sourceLongEndpoint.insert(rollback, rollback.getAmount(), false);
            if (rolledBack < rollback.getAmount()) {
                SkyLogistics.LOGGER.warn(
                        "Fluid rollback failed after simulated target fill changed during long fluid transfer. Source node {} face {}, target node {} face {}, drained {}, inserted {}, rollback remainder {} mB",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                        rollback.getAmount() - rolledBack);
            }
        }
        return true;
    }

    private static LongFluidEndpoint longFluidEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.node() instanceof SkyMEInterfaceBlockEntity
                || endpoint.node() instanceof SkyRSInterfaceBlockEntity
                ? endpoint.node() : endpoint.targetBlockEntity();
        boolean supported = blockEntity instanceof FluidVaultBlockEntity
                || blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost
                || (blockEntity instanceof SkyMEInterfaceBlockEntity
                && AppliedEnergisticsCompat.isLoaded()
                && SkyLogisticsConfig.allowAe2FluidTransfer())
                || (blockEntity instanceof SkyRSInterfaceBlockEntity
                && RefinedStorageCompat.isLoaded()
                && SkyLogisticsConfig.allowRefinedStorageFluidTransfer());
        if (!supported) {
            LONG_FLUID_ENDPOINTS.remove(endpoint);
            return null;
        }
        LongFluidEndpointCache cached = LONG_FLUID_ENDPOINTS.get(endpoint);
        if (cached != null && cached.blockEntity() == blockEntity) return cached.endpoint();
        LongFluidEndpoint resolved;
        if (blockEntity instanceof FluidVaultBlockEntity vault) resolved = new FluidVaultLongEndpoint(vault);
        else if (blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
            resolved = new DimensionFluidLongEndpoint(blockEntity);
        } else if (blockEntity instanceof SkyMEInterfaceBlockEntity) resolved = new Ae2FluidLongEndpoint(blockEntity);
        else resolved = new RefinedStorageFluidLongEndpoint(blockEntity);
        LONG_FLUID_ENDPOINTS.put(endpoint, new LongFluidEndpointCache(blockEntity, resolved));
        return resolved;
    }

    private static long moveFluidToLongTarget(CachedEndpoint sourceEndpoint, IFluidHandler source, int sourceTank,
            FluidStack simulated, CachedEndpoint targetEndpoint, LongFluidEndpoint targetLongEndpoint, long maxAmount) {
        long requested = Math.min(maxAmount, simulated.getAmount());
        long accepted = targetLongEndpoint.insert(simulated, requested, true);
        if (accepted <= 0L) return 0L;
        FluidStack drained = source.drain(copyWithAmount(simulated, (int) Math.min(accepted, Integer.MAX_VALUE)),
                IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) return -1L;
        long inserted = targetLongEndpoint.insert(drained, drained.getAmount(), false);
        if (inserted < drained.getAmount()) {
            FluidStack rollback = copyWithAmount(drained, drained.getAmount() - (int) inserted);
            int rolledBack = source.fill(rollback, IFluidHandler.FluidAction.EXECUTE);
            if (rolledBack < rollback.getAmount()) {
                SkyLogistics.LOGGER.warn(
                        "Fluid rollback failed after simulated long target insertion changed. Source node {} face {}, target node {} face {}, source tank {}, drained {}, inserted {}, rollback remainder {} mB",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), sourceTank, drained, inserted,
                        rollback.getAmount() - rolledBack);
            }
        }
        return inserted;
    }

    private static long moveLongFluid(CachedEndpoint sourceEndpoint, LongFluidEndpoint sourceEndpointLong,
            int sourceTank, CachedEndpoint targetEndpoint, LongFluidEndpoint targetEndpointLong, long maxAmount) {
        if (sourceEndpointLong.sameStorage(targetEndpointLong)) {
            return 0L;
        }
        LongFluidResource resource = sourceEndpointLong.resourceInTank(sourceTank);
        if (resource.isEmpty()) {
            return 0L;
        }
        long requested = Math.min(maxAmount, resource.amount());
        long accepted = targetEndpointLong.insert(resource.stack(), requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceEndpointLong.extract(sourceTank, resource.stack(), accepted, false);
        if (extracted <= 0L) {
            return -1L;
        }
        long inserted = targetEndpointLong.insert(resource.stack(), extracted, false);
        if (inserted < extracted) {
            long rollback = extracted - inserted;
            long rolledBack = sourceEndpointLong.insert(resource.stack(), rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Fluid rollback failed after simulated long fluid insertion changed during transfer. Source node {} face {}, target node {} face {}, source tank {}, extracted {}, inserted {}, rollback remainder {} mB",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), sourceTank, extracted,
                        inserted, rollback - rolledBack);
            }
        }
        return inserted;
    }

    private static long moveLongFluidStack(CachedEndpoint sourceEndpoint, LongFluidEndpoint sourceEndpointLong,
            FluidStack stack, long available, CachedEndpoint targetEndpoint, LongFluidEndpoint targetEndpointLong,
            long maxAmount) {
        if (sourceEndpointLong.sameStorage(targetEndpointLong)) {
            return 0L;
        }
        long requested = Math.min(maxAmount, available);
        if (requested <= 0L || stack.isEmpty()) {
            return 0L;
        }
        long accepted = targetEndpointLong.insert(stack, requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceEndpointLong.extract(-1, stack, accepted, false);
        if (extracted <= 0L) {
            return -1L;
        }
        long inserted = targetEndpointLong.insert(stack, extracted, false);
        if (inserted < extracted) {
            long rollback = extracted - inserted;
            long rolledBack = sourceEndpointLong.insert(stack, rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Fluid rollback failed after simulated long fluid insertion changed during transfer. Source node {} face {}, target node {} face {}, extracted {}, inserted {}, rollback remainder {} mB",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                        rollback - rolledBack);
            }
        }
        return inserted;
    }

    private interface LongFluidEndpoint {
        LongFluidResource resourceInTank(int tank);

        long insert(FluidStack stack, long amount, boolean simulate);

        long extract(int tank, FluidStack stack, long amount, boolean simulate);

        boolean sameStorage(LongFluidEndpoint other);
    }

    private record LongFluidEndpointCache(BlockEntity blockEntity, LongFluidEndpoint endpoint) {
    }

    private record LongFluidResource(FluidStack stack, long amount) {
        private static final LongFluidResource EMPTY = new LongFluidResource(FluidStack.EMPTY, 0L);

        private boolean isEmpty() {
            return stack.isEmpty() || amount <= 0L;
        }
    }

    private record FluidVaultLongEndpoint(FluidVaultBlockEntity vault) implements LongFluidEndpoint {
        @Override
        public LongFluidResource resourceInTank(int tank) {
            FluidVaultBlockEntity.StoredFluid stored = vault.storedFluidInTank(tank);
            return stored.stack().isEmpty() || stored.amount() <= 0L
                    ? LongFluidResource.EMPTY
                    : new LongFluidResource(stored.stack(), stored.amount());
        }

        @Override
        public long insert(FluidStack stack, long amount, boolean simulate) {
            return vault.insertStoredFluid(stack, amount, simulate);
        }

        @Override
        public long extract(int tank, FluidStack stack, long amount, boolean simulate) {
            return vault.extractStoredFluid(tank, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongFluidEndpoint other) {
            return other instanceof FluidVaultLongEndpoint endpoint && vault == endpoint.vault;
        }
    }

    private record DimensionFluidLongEndpoint(BlockEntity blockEntity) implements LongFluidEndpoint {
        @Override
        public LongFluidResource resourceInTank(int tank) {
            BeyondDimensionsCompat.FluidResource resource = BeyondDimensionsCompat.fluidResourceInTank(blockEntity,
                    tank);
            return resource.isEmpty() ? LongFluidResource.EMPTY : new LongFluidResource(resource.stack(),
                    resource.amount());
        }

        @Override
        public long insert(FluidStack stack, long amount, boolean simulate) {
            return BeyondDimensionsCompat.insertFluid(blockEntity, stack, amount, simulate);
        }

        @Override
        public long extract(int tank, FluidStack stack, long amount, boolean simulate) {
            return BeyondDimensionsCompat.extractFluid(blockEntity, stack, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongFluidEndpoint other) {
            return other instanceof DimensionFluidLongEndpoint endpoint
                    && sameDimensionNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private record Ae2FluidLongEndpoint(BlockEntity blockEntity) implements LongFluidEndpoint {
        @Override
        public LongFluidResource resourceInTank(int tank) {
            return LongFluidResource.EMPTY;
        }

        @Override
        public long insert(FluidStack stack, long amount, boolean simulate) {
            return AppliedEnergisticsCompat.insertFluid(blockEntity, stack, amount, simulate);
        }

        @Override
        public long extract(int tank, FluidStack stack, long amount, boolean simulate) {
            return AppliedEnergisticsCompat.extractFluid(blockEntity, stack, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongFluidEndpoint other) {
            return other instanceof Ae2FluidLongEndpoint endpoint
                    && AppliedEnergisticsCompat.sameNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private record RefinedStorageFluidLongEndpoint(BlockEntity blockEntity) implements LongFluidEndpoint {
        @Override
        public LongFluidResource resourceInTank(int tank) {
            return LongFluidResource.EMPTY;
        }

        @Override
        public long insert(FluidStack stack, long amount, boolean simulate) {
            return RefinedStorageCompat.insertFluid(blockEntity, stack, amount, simulate);
        }

        @Override
        public long extract(int tank, FluidStack stack, long amount, boolean simulate) {
            return RefinedStorageCompat.extractFluid(blockEntity, stack, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongFluidEndpoint other) {
            return other instanceof RefinedStorageFluidLongEndpoint endpoint
                    && RefinedStorageCompat.sameNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private static boolean sameDimensionNetwork(BlockEntity first, BlockEntity second) {
        if (first == second) {
            return true;
        }
        if (first instanceof BeyondDimensionsCompat.NetworkBoundHost firstHost
                && second instanceof BeyondDimensionsCompat.NetworkBoundHost secondHost) {
            int firstNet = firstHost.getDimensionNetworkId();
            return firstNet >= 0 && firstNet == secondHost.getDimensionNetworkId();
        }
        return false;
    }

    private static int transferChemicals(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        configureMaintainedBackoff(sourceEndpoint, targets,
                SkyLogisticsConfig.enableMaintainedChemicalPolling(), CachedEndpoint::setChemicalMaintainedBackoff);
        if (!SkyLogisticsConfig.allowFluidChemicalTransfer()) {
            return 0;
        }
        NetworkEndpointBlockEntity sourceNode = sourceEndpoint.node();
        ChemicalHandlerBridge source = sourceEndpoint.chemicalHandler(gameTime);
        if (source == null || budget <= 0) {
            return 0;
        }
        int tanks = source.getTanks();
        if (deferExhaustedDistributorChemical(sourceEndpoint, source, gameTime)) return 0;
        if (tanks <= 0) {
            sourceEndpoint.recordChemicalFailure(gameTime);
            return 0;
        }
        int operations = 0;
        boolean foundCandidate = false;
        boolean independentDistributorProbes = usesIndependentDistributorProbes(source);
        int tankChecks = Math.min(tanks,
                Math.min(sourceNode.getOperationRate(), SkyLogisticsConfig.externalTankScansPerEndpoint()));
        if (independentDistributorProbes && source instanceof BudgetedDistributorHandler distributor) {
            tankChecks = Math.min(tanks, Math.max(tankChecks, distributor.fairExtractionProbesDue(gameTime)));
        }
        int firstTriedTank = -1;
        int secondTriedTank = -1;
        boolean sourceTanksExhausted = false;
        for (int i = 0; i < tankChecks && operations < budget; i++) {
            SourceSearchResult search = nextChemicalTank(sourceEndpoint, sourceNode, tanks, gameTime,
                    firstTriedTank, secondTriedTank, budget - operations);
            operations += search.skippedChecks();
            int tank = search.index();
            if (tank < 0) {
                sourceTanksExhausted = search.exhausted();
                break;
            }
            if (firstTriedTank < 0) {
                firstTriedTank = tank;
            } else {
                secondTriedTank = tank;
            }
            ChemicalStackView inTank = source.getChemicalInTank(tank);
            operations++;
            if (inTank.isEmpty()) {
                if (deferExhaustedDistributorChemical(sourceEndpoint, source, gameTime)) return operations;
                if (!independentDistributorProbes) sourceEndpoint.recordChemicalTankMiss(tank, gameTime);
                continue;
            }
            long transferLimit = sourceNode.limitChemicalTransfer(Long.MAX_VALUE);
            ChemicalStackView simulated = source.extractChemical(tank, transferLimit, true);
            if (simulated.isEmpty()) {
                if (deferExhaustedDistributorChemical(sourceEndpoint, source, gameTime)) return operations;
                if (!independentDistributorProbes) sourceEndpoint.recordChemicalTankMiss(tank, gameTime);
                continue;
            }
            if (!sourceNode.allowsChemical(sourceEndpoint.direction(), simulated)) continue;
            foundCandidate = true;
            sourceEndpoint.recordChemicalCandidateFound();
            MoveResult result = tryMoveChemical(sourceEndpoint, source, tank, simulated, targets,
                    budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                if (!independentDistributorProbes) sourceEndpoint.recordChemicalTankSuccess(tank, tanks);
                sourceEndpoint.recordChemicalSuccess();
            }
        }
        if (!foundCandidate) {
            if (deferExhaustedDistributorChemical(sourceEndpoint, source, gameTime)) return operations;
            sourceEndpoint.recordChemicalSourceMiss(sourceTanksExhausted ? tanks : operations, tanks, gameTime);
        }
        return operations;
    }

    private static SourceSearchResult nextChemicalTank(CachedEndpoint sourceEndpoint, NetworkEndpointBlockEntity sourceNode,
            int tanks, long gameTime, int firstTriedTank, int secondTriedTank, int skipBudget) {
        ChemicalHandlerBridge handler = sourceEndpoint.chemicalHandler(gameTime);
        if (usesIndependentDistributorProbes(handler) && handler instanceof BudgetedDistributorHandler distributor) {
            int fairTank = distributor.nextFairExtractionSlot(gameTime);
            return fairTank >= 0 && fairTank < tanks && !wasSlotTried(firstTriedTank, secondTriedTank, fairTank)
                    ? new SourceSearchResult(fairTank, 0, false) : new SourceSearchResult(-1, 0, false);
        }
        if (sourceEndpoint.shouldTryChemicalTankDiscoveryBeforePreferred()) {
            SourceSearchResult discovery = nextSequentialChemicalTank(sourceEndpoint, sourceNode, tanks, gameTime,
                    firstTriedTank, secondTriedTank, true, skipBudget);
            if (discovery.index() >= 0) {
                sourceEndpoint.recordChemicalTankDiscoveryCheck();
                return discovery;
            }
            if (discovery.exhausted()) {
                sourceEndpoint.clearChemicalTankDiscovery();
            } else {
                return discovery;
            }
        }
        int preferredTank = sourceEndpoint.nextPreferredChemicalTank(tanks, gameTime, firstTriedTank, secondTriedTank);
        if (preferredTank >= 0) {
            return new SourceSearchResult(preferredTank, 0, false);
        }
        if (sourceEndpoint.isChemicalTankDiscoveryActive()) {
            SourceSearchResult discovery = nextSequentialChemicalTank(sourceEndpoint, sourceNode, tanks, gameTime,
                    firstTriedTank, secondTriedTank, true, skipBudget);
            if (discovery.index() >= 0) {
                sourceEndpoint.recordChemicalTankDiscoveryCheck();
            } else if (discovery.exhausted()) {
                sourceEndpoint.clearChemicalTankDiscovery();
            }
            return discovery;
        }
        return nextSequentialChemicalTank(sourceEndpoint, sourceNode, tanks, gameTime,
                firstTriedTank, secondTriedTank, false, skipBudget);
    }

    private static SourceSearchResult nextSequentialChemicalTank(CachedEndpoint sourceEndpoint,
            NetworkEndpointBlockEntity sourceNode, int tanks, long gameTime, int firstTriedTank, int secondTriedTank,
            boolean ignoreEmptyCooldown, int skipBudget) {
        int skippedChecks = 0;
        int attemptLimit = Math.min(tanks, SkyLogisticsConfig.sourceSearchAttemptsPerEndpoint());
        for (int attempts = 0; attempts < attemptLimit; attempts++) {
            int tank = sourceNode.nextFluidStart(tanks);
            if (wasSlotTried(firstTriedTank, secondTriedTank, tank)
                    || (!ignoreEmptyCooldown && !sourceEndpoint.canTryChemicalTank(tank, gameTime))) {
                skippedChecks++;
                if (skippedChecks >= skipBudget) {
                    return new SourceSearchResult(-1, skippedChecks, false);
                }
                continue;
            }
            return new SourceSearchResult(tank, skippedChecks, false);
        }
        return new SourceSearchResult(-1, skippedChecks, attemptLimit >= tanks);
    }

    private static MoveResult tryMoveChemical(CachedEndpoint sourceEndpoint, ChemicalHandlerBridge source,
            int sourceTank, ChemicalStackView simulated, List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        int targetVisits = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        int targetCount = targets.size();
        int targetIndex = sourceEndpoint.chemicalTargetScanStart(simulated, targetCount);
        targetLoop:
        for (int scannedTargets = 0; scannedTargets < targetCount; scannedTargets++) {
                CachedEndpoint targetEndpoint = targets.get(targetIndex);
                if (targetVisits >= budget) { budgetExhausted = true; break; }
                targetVisits++;
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    targetIndex = advanceChemicalTargetScan(sourceEndpoint, simulated, targetIndex, targetCount);
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryChemicals(gameTime)
                        || !targetEndpoint.node().isFluidsEnabled(targetEndpoint.direction())
                        || targetEndpoint.isChemicalAcceptRejected(simulated, gameTime)) {
                    targetIndex = advanceChemicalTargetScan(sourceEndpoint, simulated, targetIndex, targetCount);
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    sourceEndpoint.resumeChemicalTargetScan(simulated, targetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                int visitedTargetIndex = targetIndex;
                targetIndex = advanceChemicalTargetScan(sourceEndpoint, simulated, targetIndex, targetCount);
                if (!targetEndpoint.node().allowsChemical(targetEndpoint.direction(), simulated)) continue;
                ChemicalHandlerBridge target = targetEndpoint.chemicalHandler(gameTime);
                if (target == null) {
                    continue;
                }
                long accepted = target.insertChemical(simulated.copyWithAmount(
                        maintainedChemicalAllowance(targetEndpoint, target, simulated.getAmount())), true);
                if (accepted <= 0L) {
                    if (deferExhaustedDistributorChemical(targetEndpoint, target, gameTime)) {
                        sourceEndpoint.resumeChemicalTargetScan(simulated, visitedTargetIndex, targetCount);
                        budgetExhausted = true;
                        break targetLoop;
                    }
                    targetEndpoint.recordChemicalAcceptReject(simulated, gameTime);
                    continue;
                }
                ChemicalStackView drained = source.extractChemical(sourceTank, accepted, false);
                if (drained.isEmpty()) {
                    if (deferExhaustedDistributorChemical(sourceEndpoint, source, gameTime)) {
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    sourceEndpoint.recordChemicalFailure(gameTime);
                    return targetMoveResult(false, operations, targetVisits);
                }
                long inserted = target.insertChemical(drained, false);
                if (inserted < drained.getAmount()) {
                    ChemicalStackView rollback = drained.copyWithAmount(drained.getAmount() - inserted);
                    long rolledBack = source.insertChemical(rollback, false);
                    if (rolledBack < rollback.getAmount()) {
                        SkyLogistics.LOGGER.warn(
                                "Chemical rollback failed after simulated target insertion changed during transfer. Source node {} face {}, target node {} face {}, source tank {}, drained {}, inserted {}, rollback remainder {}",
                                sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                                targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), sourceTank, drained,
                                inserted, rollback.getAmount() - rolledBack);
                    }
                }
                targetEndpoint.recordChemicalSuccess();
                finishChemicalTargetScan(sourceEndpoint, simulated, targets, visitedTargetIndex);
                return targetMoveResult(true, operations, targetVisits);
        }
        if (!budgetExhausted) sourceEndpoint.resetChemicalTargetScan(simulated);
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordChemicalFailure(gameTime);
        }
        return targetMoveResult(false, operations, targetVisits);
    }

    private static int transferEnergy(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        configureMaintainedBackoff(sourceEndpoint, targets, SkyLogisticsConfig.enableMaintainedEnergyPolling(),
                CachedEndpoint::setEnergyMaintainedBackoff);
        if (budget <= 0) {
            return 0;
        }
        if (!sourceEndpoint.node().allowsEnergy(sourceEndpoint.direction())) return 0;
        LongEnergyEndpoint sourceLongEndpoint = longEnergyEndpoint(sourceEndpoint);
        IEnergyStorage source = sourceLongEndpoint == null ? sourceEndpoint.energyHandler(gameTime) : null;
        if (sourceLongEndpoint == null && source == null) return 0;
        if (source != null && deferExhaustedDistributorEnergy(sourceEndpoint, source, gameTime)) return 0;
        int transferLimit = (int) Math.min(Integer.MAX_VALUE,
                sourceEndpoint.node().limitEnergyTransfer(SkyLogisticsConfig.nodeEnergyTransferLimit()));
        int simulated = sourceLongEndpoint != null
                ? (int) Math.min(transferLimit, sourceLongEndpoint.energyStored())
                : source.extractEnergy(transferLimit, true);
        int operations = 1;
        if (simulated <= 0) {
            sourceEndpoint.recordEnergyFailure(gameTime);
            return operations;
        }
        MoveResult result = tryMoveEnergy(sourceEndpoint, source, sourceLongEndpoint, simulated, targets,
                budget - operations, gameTime);
        operations += result.operations();
        if (result.moved()) {
            sourceEndpoint.recordEnergySuccess();
        }
        return operations;
    }

    private static MoveResult tryMoveEnergy(CachedEndpoint sourceEndpoint, IEnergyStorage source,
            LongEnergyEndpoint sourceLongEndpoint, int simulated, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        long skyContainerTransferLimit = sourceEndpoint.node()
                .limitEnergyTransfer(SkyLogisticsConfig.skyContainerTransferLimit());
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        int targetVisits = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        int targetCount = targets.size();
        int targetIndex = sourceEndpoint.resourceTargetScanStart(TargetResource.ENERGY, targetCount);
        targetLoop:
        for (int scannedTargets = 0; scannedTargets < targetCount; scannedTargets++) {
                CachedEndpoint targetEndpoint = targets.get(targetIndex);
                if (targetVisits >= budget) { budgetExhausted = true; break; }
                targetVisits++;
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.ENERGY, targetIndex,
                            targetCount);
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryEnergy(gameTime)
                        || !targetEndpoint.node().isEnergyEnabled(targetEndpoint.direction())
                        || !targetEndpoint.node().allowsEnergy(targetEndpoint.direction())) {
                    targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.ENERGY, targetIndex,
                            targetCount);
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    sourceEndpoint.resumeResourceTargetScan(TargetResource.ENERGY, targetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                int visitedTargetIndex = targetIndex;
                targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.ENERGY, targetIndex,
                        targetCount);
                LongEnergyEndpoint targetLongEndpoint = longEnergyEndpoint(targetEndpoint);
                if (sourceLongEndpoint != null && targetLongEndpoint != null) {
                    long moved = moveLongEnergy(sourceEndpoint, sourceLongEndpoint, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        finishResourceTargetScan(sourceEndpoint, TargetResource.ENERGY, targets,
                                visitedTargetIndex);
                        targetEndpoint.recordEnergySuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordEnergyFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    targetEndpoint.recordEnergyFailure(gameTime);
                    continue;
                }
                if (targetLongEndpoint != null) {
                    long moved = moveEnergyToLongTarget(sourceEndpoint, source, targetEndpoint, targetLongEndpoint,
                            simulated);
                    if (moved > 0L) {
                        finishResourceTargetScan(sourceEndpoint, TargetResource.ENERGY, targets,
                                visitedTargetIndex);
                        targetEndpoint.recordEnergySuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordEnergyFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    targetEndpoint.recordEnergyFailure(gameTime);
                    continue;
                }
                IEnergyStorage target = targetEndpoint.energyHandler(gameTime);
                if (target == null) {
                    continue;
                }
                if (deferExhaustedDistributorEnergy(targetEndpoint, target, gameTime)) {
                    deferExhaustedDistributorEnergy(sourceEndpoint, target, gameTime);
                    sourceEndpoint.resumeResourceTargetScan(TargetResource.ENERGY, visitedTargetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                if (sourceLongEndpoint != null) {
                    long moved = moveLongEnergyToHandler(sourceEndpoint, sourceLongEndpoint, targetEndpoint, target,
                            simulated);
                    if (moved > 0L) {
                        finishResourceTargetScan(sourceEndpoint, TargetResource.ENERGY, targets,
                                visitedTargetIndex);
                        targetEndpoint.recordEnergySuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordEnergyFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    targetEndpoint.recordEnergyFailure(gameTime);
                    continue;
                }
                int accepted = target.receiveEnergy((int) maintainedEnergyAllowance(targetEndpoint, target,
                        simulated), true);
                if (accepted <= 0) {
                    targetEndpoint.recordEnergyFailure(gameTime);
                    continue;
                }
                int extracted = source.extractEnergy(accepted, false);
                if (extracted <= 0) {
                    sourceEndpoint.recordEnergyFailure(gameTime);
                    return targetMoveResult(false, operations, targetVisits);
                }
                int inserted = target.receiveEnergy(extracted, false);
                if (inserted < extracted) {
                    int rollback = extracted - inserted;
                    int rolledBack = source.receiveEnergy(rollback, false);
                    if (rolledBack < rollback) {
                        SkyLogistics.LOGGER.warn(
                                "Energy rollback failed after simulated target receive changed during transfer. Source node {} face {}, target node {} face {}, extracted {} energy, inserted {} energy, rollback remainder {} energy",
                                sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                                targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                                rollback - rolledBack);
                    }
                }
                targetEndpoint.recordEnergySuccess();
                finishResourceTargetScan(sourceEndpoint, TargetResource.ENERGY, targets, visitedTargetIndex);
                return targetMoveResult(true, operations, targetVisits);
        }
        if (!budgetExhausted) sourceEndpoint.resetResourceTargetScan(TargetResource.ENERGY);
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordEnergyFailure(gameTime);
        }
        return targetMoveResult(false, operations, targetVisits);
    }

    private static LongEnergyEndpoint longEnergyEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.node();
        if (blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
            return new DimensionEnergyLongEndpoint(blockEntity);
        }
        if (blockEntity instanceof SkyMEInterfaceBlockEntity && AppliedEnergisticsCompat.isLoaded()
                && AppliedEnergisticsCompat.supportsAppFluxEnergyEndpoint()) {
            return new Ae2EnergyLongEndpoint(blockEntity);
        }
        return null;
    }

    private static long moveLongEnergyToHandler(CachedEndpoint sourceEndpoint, LongEnergyEndpoint sourceLongEndpoint,
            CachedEndpoint targetEndpoint, IEnergyStorage target, int requested) {
        int accepted = target.receiveEnergy((int) maintainedEnergyAllowance(targetEndpoint, target,
                requested), true);
        if (accepted <= 0) return 0L;
        long extracted = sourceLongEndpoint.extractEnergy(accepted, false);
        if (extracted <= 0L) return -1L;
        int inserted = target.receiveEnergy((int) extracted, false);
        if (inserted < extracted) {
            long rollback = extracted - inserted;
            long rolledBack = sourceLongEndpoint.insertEnergy(rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Energy rollback failed after simulated handler insertion changed. Source node {} face {}, target node {} face {}, extracted {}, inserted {}, rollback remainder {}",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                        rollback - rolledBack);
            }
        }
        return inserted;
    }

    private static long moveEnergyToLongTarget(CachedEndpoint sourceEndpoint, IEnergyStorage source,
            CachedEndpoint targetEndpoint, LongEnergyEndpoint targetLongEndpoint, int requested) {
        long accepted = targetLongEndpoint.insertEnergy(requested, true);
        if (accepted <= 0L) return 0L;
        int extracted = source.extractEnergy((int) Math.min(accepted, Integer.MAX_VALUE), false);
        if (extracted <= 0) return -1L;
        long inserted = targetLongEndpoint.insertEnergy(extracted, false);
        if (inserted < extracted) {
            int rollback = extracted - (int) inserted;
            int rolledBack = source.receiveEnergy(rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Energy rollback failed after simulated long target insertion changed. Source node {} face {}, target node {} face {}, extracted {}, inserted {}, rollback remainder {}",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                        rollback - rolledBack);
            }
        }
        return inserted;
    }

    private static long moveLongEnergy(CachedEndpoint sourceEndpoint, LongEnergyEndpoint sourceEndpointLong,
            CachedEndpoint targetEndpoint, LongEnergyEndpoint targetEndpointLong, long maxAmount) {
        if (sourceEndpointLong.sameStorage(targetEndpointLong)) {
            return 0L;
        }
        long stored = sourceEndpointLong.energyStored();
        if (stored <= 0L) {
            return 0L;
        }
        long requested = Math.min(maxAmount, stored);
        long accepted = targetEndpointLong.insertEnergy(requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceEndpointLong.extractEnergy(accepted, false);
        if (extracted <= 0L) {
            return -1L;
        }
        long inserted = targetEndpointLong.insertEnergy(extracted, false);
        if (inserted < extracted) {
            long rollback = extracted - inserted;
            long rolledBack = sourceEndpointLong.insertEnergy(rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Energy rollback failed after simulated long energy insertion changed during transfer. Source node {} face {}, target node {} face {}, extracted {} energy, inserted {} energy, rollback remainder {} energy",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                        rollback - rolledBack);
            }
        }
        return inserted;
    }

    private interface LongEnergyEndpoint {
        long energyStored();

        long insertEnergy(long amount, boolean simulate);

        long extractEnergy(long amount, boolean simulate);

        boolean sameStorage(LongEnergyEndpoint other);
    }

    private record DimensionEnergyLongEndpoint(BlockEntity blockEntity) implements LongEnergyEndpoint {
        @Override
        public long energyStored() {
            return BeyondDimensionsCompat.energyStored(blockEntity);
        }

        @Override
        public long insertEnergy(long amount, boolean simulate) {
            return BeyondDimensionsCompat.insertEnergy(blockEntity, amount, simulate);
        }

        @Override
        public long extractEnergy(long amount, boolean simulate) {
            return BeyondDimensionsCompat.extractEnergy(blockEntity, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongEnergyEndpoint other) {
            return other instanceof DimensionEnergyLongEndpoint endpoint
                    && sameDimensionNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private record Ae2EnergyLongEndpoint(BlockEntity blockEntity) implements LongEnergyEndpoint {
        @Override
        public long energyStored() {
            return AppliedEnergisticsCompat.energyStored(blockEntity);
        }

        @Override
        public long insertEnergy(long amount, boolean simulate) {
            return AppliedEnergisticsCompat.insertEnergy(blockEntity, amount, simulate);
        }

        @Override
        public long extractEnergy(long amount, boolean simulate) {
            return AppliedEnergisticsCompat.extractEnergy(blockEntity, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongEnergyEndpoint other) {
            return other instanceof Ae2EnergyLongEndpoint endpoint
                    && AppliedEnergisticsCompat.sameNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private static int transferMana(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        configureMaintainedBackoff(sourceEndpoint, targets, SkyLogisticsConfig.enableMaintainedManaPolling(),
                CachedEndpoint::setManaMaintainedBackoff);
        if (!canTransferMana() || budget <= 0
                || !sourceEndpoint.node().allowsMana(sourceEndpoint.direction())) {
            return 0;
        }
        ManaHandlerBridge source = sourceEndpoint.manaHandler(gameTime);
        if (source == null) {
            return 0;
        }
        if (deferExhaustedDistributorMana(sourceEndpoint, source, gameTime)) return 0;
        int transferLimit = (int) Math.min(Integer.MAX_VALUE,
                sourceEndpoint.node().limitManaTransfer(SkyLogisticsConfig.nodeEnergyTransferLimit()));
        int simulated = source.extractMana(transferLimit, true);
        int operations = 1;
        if (simulated <= 0) {
            sourceEndpoint.recordManaFailure(gameTime);
            return operations;
        }
        MoveResult result = tryMoveMana(sourceEndpoint, source, simulated, targets, budget - operations, gameTime);
        operations += result.operations();
        if (result.moved()) {
            sourceEndpoint.recordManaSuccess();
        }
        return operations;
    }

    private static MoveResult tryMoveMana(CachedEndpoint sourceEndpoint, ManaHandlerBridge source, int simulated,
            List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        LongManaEndpoint sourceLongEndpoint = longManaEndpoint(sourceEndpoint);
        long skyContainerTransferLimit = SkyLogisticsConfig.skyContainerTransferLimit();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        int targetVisits = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        int targetCount = targets.size();
        int targetIndex = sourceEndpoint.resourceTargetScanStart(TargetResource.MANA, targetCount);
        targetLoop:
        for (int scannedTargets = 0; scannedTargets < targetCount; scannedTargets++) {
                CachedEndpoint targetEndpoint = targets.get(targetIndex);
                if (targetVisits >= budget) { budgetExhausted = true; break; }
                targetVisits++;
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.MANA, targetIndex,
                            targetCount);
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryMana(gameTime)
                        || !targetEndpoint.node().allowsMana(targetEndpoint.direction())
                        || !targetEndpoint.node().isEnergyEnabled(targetEndpoint.direction())) {
                    targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.MANA, targetIndex,
                            targetCount);
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    sourceEndpoint.resumeResourceTargetScan(TargetResource.MANA, targetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                int visitedTargetIndex = targetIndex;
                targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.MANA, targetIndex,
                        targetCount);
                LongManaEndpoint targetLongEndpoint = sourceLongEndpoint == null ? null
                        : longManaEndpoint(targetEndpoint);
                if (targetLongEndpoint != null) {
                    long moved = moveLongMana(sourceEndpoint, sourceLongEndpoint, targetEndpoint, targetLongEndpoint,
                            skyContainerTransferLimit);
                    if (moved > 0L) {
                        finishResourceTargetScan(sourceEndpoint, TargetResource.MANA, targets,
                                visitedTargetIndex);
                        targetEndpoint.recordManaSuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordManaFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    targetEndpoint.recordManaFailure(gameTime);
                    continue;
                }
                ManaHandlerBridge target = targetEndpoint.manaHandler(gameTime);
                if (target == null) {
                    continue;
                }
                if (deferExhaustedDistributorMana(targetEndpoint, target, gameTime)) {
                    deferExhaustedDistributorMana(sourceEndpoint, target, gameTime);
                    sourceEndpoint.resumeResourceTargetScan(TargetResource.MANA, visitedTargetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                int accepted = target.insertMana((int) maintainedManaAllowance(targetEndpoint, target,
                        simulated), true);
                if (accepted <= 0) {
                    targetEndpoint.recordManaFailure(gameTime);
                    continue;
                }
                int extracted = source.extractMana(accepted, false);
                if (extracted <= 0) {
                    sourceEndpoint.recordManaFailure(gameTime);
                    return targetMoveResult(false, operations, targetVisits);
                }
                int inserted = target.insertMana(extracted, false);
                if (inserted < extracted) {
                    int rollback = extracted - inserted;
                    int rolledBack = source.insertMana(rollback, false);
                    if (rolledBack < rollback) {
                        SkyLogistics.LOGGER.warn(
                                "Mana rollback failed after simulated target receive changed during transfer. Source node {} face {}, target node {} face {}, extracted {} mana, inserted {} mana, rollback remainder {} mana",
                                sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                                targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                                rollback - rolledBack);
                    }
                }
                targetEndpoint.recordManaSuccess();
                finishResourceTargetScan(sourceEndpoint, TargetResource.MANA, targets, visitedTargetIndex);
                return targetMoveResult(true, operations, targetVisits);
        }
        if (!budgetExhausted) sourceEndpoint.resetResourceTargetScan(TargetResource.MANA);
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordManaFailure(gameTime);
        }
        return targetMoveResult(false, operations, targetVisits);
    }

    private static LongManaEndpoint longManaEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.targetBlockEntity();
        if (blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
            return new DimensionManaLongEndpoint(blockEntity);
        }
        return null;
    }

    private static long moveLongMana(CachedEndpoint sourceEndpoint, LongManaEndpoint sourceEndpointLong,
            CachedEndpoint targetEndpoint, LongManaEndpoint targetEndpointLong, long maxAmount) {
        if (sourceEndpointLong.sameStorage(targetEndpointLong)) {
            return 0L;
        }
        long stored = sourceEndpointLong.manaStored();
        if (stored <= 0L) {
            return 0L;
        }
        long requested = Math.min(maxAmount, stored);
        long accepted = targetEndpointLong.insertMana(requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceEndpointLong.extractMana(accepted, false);
        if (extracted <= 0L) {
            return -1L;
        }
        long inserted = targetEndpointLong.insertMana(extracted, false);
        if (inserted < extracted) {
            long rollback = extracted - inserted;
            long rolledBack = sourceEndpointLong.insertMana(rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Mana rollback failed after simulated long mana insertion changed during transfer. Source node {} face {}, target node {} face {}, extracted {} mana, inserted {} mana, rollback remainder {} mana",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                        rollback - rolledBack);
            }
        }
        return inserted;
    }

    private interface LongManaEndpoint {
        long manaStored();

        long insertMana(long amount, boolean simulate);

        long extractMana(long amount, boolean simulate);

        boolean sameStorage(LongManaEndpoint other);
    }

    private record DimensionManaLongEndpoint(BlockEntity blockEntity) implements LongManaEndpoint {
        @Override
        public long manaStored() {
            return BeyondDimensionsCompat.manaStored(blockEntity);
        }

        @Override
        public long insertMana(long amount, boolean simulate) {
            return BeyondDimensionsCompat.insertMana(blockEntity, amount, simulate);
        }

        @Override
        public long extractMana(long amount, boolean simulate) {
            return BeyondDimensionsCompat.extractMana(blockEntity, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongManaEndpoint other) {
            return other instanceof DimensionManaLongEndpoint endpoint
                    && sameDimensionNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private static int transferSource(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        configureMaintainedBackoff(sourceEndpoint, targets, SkyLogisticsConfig.enableMaintainedSourcePolling(),
                CachedEndpoint::setSourceMaintainedBackoff);
        if (!canTransferSource() || budget <= 0
                || !sourceEndpoint.node().allowsSource(sourceEndpoint.direction())) {
            return 0;
        }
        SourceHandlerBridge source = sourceEndpoint.sourceHandler(gameTime);
        if (source == null) {
            return 0;
        }
        if (deferExhaustedDistributorSource(sourceEndpoint, source, gameTime)) return 0;
        int transferLimit = (int) Math.min(Integer.MAX_VALUE,
                sourceEndpoint.node().limitSourceTransfer(SkyLogisticsConfig.nodeEnergyTransferLimit()));
        int simulated = source.extractSource(transferLimit, true);
        int operations = 1;
        if (simulated <= 0) {
            sourceEndpoint.recordSourceFailure(gameTime);
            return operations;
        }
        MoveResult result = tryMoveSource(sourceEndpoint, source, simulated, targets, budget - operations, gameTime);
        operations += result.operations();
        if (result.moved()) {
            sourceEndpoint.recordSourceSuccess();
        }
        return operations;
    }

    private static MoveResult tryMoveSource(CachedEndpoint sourceEndpoint, SourceHandlerBridge source, int simulated,
            List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        LongSourceEndpoint sourceLongEndpoint = longSourceEndpoint(sourceEndpoint);
        long skyContainerTransferLimit = SkyLogisticsConfig.skyContainerTransferLimit();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        int targetVisits = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        int targetCount = targets.size();
        int targetIndex = sourceEndpoint.resourceTargetScanStart(TargetResource.SOURCE, targetCount);
        targetLoop:
        for (int scannedTargets = 0; scannedTargets < targetCount; scannedTargets++) {
                CachedEndpoint targetEndpoint = targets.get(targetIndex);
                if (targetVisits >= budget) { budgetExhausted = true; break; }
                targetVisits++;
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.SOURCE, targetIndex,
                            targetCount);
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTrySource(gameTime)
                        || !targetEndpoint.node().allowsSource(targetEndpoint.direction())
                        || !targetEndpoint.node().isEnergyEnabled(targetEndpoint.direction())) {
                    targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.SOURCE, targetIndex,
                            targetCount);
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    sourceEndpoint.resumeResourceTargetScan(TargetResource.SOURCE, targetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                int visitedTargetIndex = targetIndex;
                targetIndex = advanceResourceTargetScan(sourceEndpoint, TargetResource.SOURCE, targetIndex,
                        targetCount);
                LongSourceEndpoint targetLongEndpoint = sourceLongEndpoint == null ? null
                        : longSourceEndpoint(targetEndpoint);
                if (targetLongEndpoint != null) {
                    long moved = moveLongSource(sourceEndpoint, sourceLongEndpoint, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        finishResourceTargetScan(sourceEndpoint, TargetResource.SOURCE, targets,
                                visitedTargetIndex);
                        targetEndpoint.recordSourceSuccess();
                        return targetMoveResult(true, operations, targetVisits);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordSourceFailure(gameTime);
                        return targetMoveResult(false, operations, targetVisits);
                    }
                    targetEndpoint.recordSourceFailure(gameTime);
                    continue;
                }
                SourceHandlerBridge target = targetEndpoint.sourceHandler(gameTime);
                if (target == null) {
                    continue;
                }
                if (deferExhaustedDistributorSource(targetEndpoint, target, gameTime)) {
                    deferExhaustedDistributorSource(sourceEndpoint, target, gameTime);
                    sourceEndpoint.resumeResourceTargetScan(TargetResource.SOURCE, visitedTargetIndex, targetCount);
                    budgetExhausted = true;
                    break targetLoop;
                }
                int accepted = target.insertSource((int) maintainedSourceAllowance(targetEndpoint, target,
                        simulated), true);
                if (accepted <= 0) {
                    targetEndpoint.recordSourceFailure(gameTime);
                    continue;
                }
                int extracted = source.extractSource(accepted, false);
                if (extracted <= 0) {
                    sourceEndpoint.recordSourceFailure(gameTime);
                    return targetMoveResult(false, operations, targetVisits);
                }
                int inserted = target.insertSource(extracted, false);
                if (inserted < extracted) {
                    int rollback = extracted - inserted;
                    int rolledBack = source.insertSource(rollback, false);
                    if (rolledBack < rollback) {
                        SkyLogistics.LOGGER.warn(
                                "Source rollback failed after simulated target receive changed during transfer. Source node {} face {}, target node {} face {}, extracted {} source, inserted {} source, rollback remainder {} source",
                                sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                                targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                                rollback - rolledBack);
                    }
                }
                targetEndpoint.recordSourceSuccess();
                finishResourceTargetScan(sourceEndpoint, TargetResource.SOURCE, targets, visitedTargetIndex);
                return targetMoveResult(true, operations, targetVisits);
        }
        if (!budgetExhausted) sourceEndpoint.resetResourceTargetScan(TargetResource.SOURCE);
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordSourceFailure(gameTime);
        }
        return targetMoveResult(false, operations, targetVisits);
    }

    private static LongSourceEndpoint longSourceEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.targetBlockEntity();
        if (blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
            return new DimensionSourceLongEndpoint(blockEntity);
        }
        return null;
    }

    private static long moveLongSource(CachedEndpoint sourceEndpoint, LongSourceEndpoint sourceEndpointLong,
            CachedEndpoint targetEndpoint, LongSourceEndpoint targetEndpointLong, long maxAmount) {
        if (sourceEndpointLong.sameStorage(targetEndpointLong)) {
            return 0L;
        }
        long stored = sourceEndpointLong.sourceStored();
        if (stored <= 0L) {
            return 0L;
        }
        long requested = Math.min(maxAmount, stored);
        long accepted = targetEndpointLong.insertSource(requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceEndpointLong.extractSource(accepted, false);
        if (extracted <= 0L) {
            return -1L;
        }
        long inserted = targetEndpointLong.insertSource(extracted, false);
        if (inserted < extracted) {
            long rollback = extracted - inserted;
            long rolledBack = sourceEndpointLong.insertSource(rollback, false);
            if (rolledBack < rollback) {
                SkyLogistics.LOGGER.warn(
                        "Source rollback failed after simulated long source insertion changed during transfer. Source node {} face {}, target node {} face {}, extracted {} source, inserted {} source, rollback remainder {} source",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, inserted,
                        rollback - rolledBack);
            }
        }
        return inserted;
    }

    private interface LongSourceEndpoint {
        long sourceStored();

        long insertSource(long amount, boolean simulate);

        long extractSource(long amount, boolean simulate);

        boolean sameStorage(LongSourceEndpoint other);
    }

    private record DimensionSourceLongEndpoint(BlockEntity blockEntity) implements LongSourceEndpoint {
        @Override
        public long sourceStored() {
            return BeyondDimensionsCompat.sourceStored(blockEntity);
        }

        @Override
        public long insertSource(long amount, boolean simulate) {
            return BeyondDimensionsCompat.insertSource(blockEntity, amount, simulate);
        }

        @Override
        public long extractSource(long amount, boolean simulate) {
            return BeyondDimensionsCompat.extractSource(blockEntity, amount, simulate);
        }

        @Override
        public boolean sameStorage(LongSourceEndpoint other) {
            return other instanceof DimensionSourceLongEndpoint endpoint
                    && sameDimensionNetwork(blockEntity, endpoint.blockEntity);
        }
    }

    private static int advanceItemTargetScan(CachedEndpoint sourceEndpoint, ItemStackKey key, int currentIndex,
            int targetCount) {
        int nextIndex = (currentIndex + 1) % targetCount;
        if (targetCount > 1) sourceEndpoint.resumeItemTargetScan(key, nextIndex, targetCount);
        return nextIndex;
    }

    private static void finishItemTargetScan(CachedEndpoint sourceEndpoint, ItemStackKey key,
            List<CachedEndpoint> targets, int successfulIndex) {
        if (targets.size() <= 1) return;
        int nextIndex = successfulIndex + 1;
        int topPriority = targets.get(0).node().getPriority(targets.get(0).direction());
        int successfulPriority = targets.get(successfulIndex).node()
                .getPriority(targets.get(successfulIndex).direction());
        if (successfulPriority == topPriority && nextIndex < targets.size()
                && targets.get(nextIndex).node().getPriority(targets.get(nextIndex).direction()) == topPriority) {
            sourceEndpoint.resumeItemTargetScan(key, nextIndex, targets.size());
        } else {
            sourceEndpoint.resetItemTargetScan(key);
        }
    }

    private static int advanceFluidTargetScan(CachedEndpoint sourceEndpoint, FluidStackKey key, int currentIndex,
            int targetCount) {
        int nextIndex = (currentIndex + 1) % targetCount;
        sourceEndpoint.resumeFluidTargetScan(key, nextIndex, targetCount);
        return nextIndex;
    }

    private static void finishFluidTargetScan(CachedEndpoint sourceEndpoint, FluidStackKey key,
            List<CachedEndpoint> targets, int successfulIndex) {
        int nextIndex = successfulIndex + 1;
        int topPriority = targets.get(0).node().getPriority(targets.get(0).direction());
        int successfulPriority = targets.get(successfulIndex).node()
                .getPriority(targets.get(successfulIndex).direction());
        if (successfulPriority == topPriority && nextIndex < targets.size()
                && targets.get(nextIndex).node().getPriority(targets.get(nextIndex).direction()) == topPriority) {
            sourceEndpoint.resumeFluidTargetScan(key, nextIndex, targets.size());
        } else {
            sourceEndpoint.resetFluidTargetScan(key);
        }
    }

    private static int advanceChemicalTargetScan(CachedEndpoint sourceEndpoint, ChemicalStackView key,
            int currentIndex, int targetCount) {
        int nextIndex = (currentIndex + 1) % targetCount;
        sourceEndpoint.resumeChemicalTargetScan(key, nextIndex, targetCount);
        return nextIndex;
    }

    private static void finishChemicalTargetScan(CachedEndpoint sourceEndpoint, ChemicalStackView key,
            List<CachedEndpoint> targets, int successfulIndex) {
        int nextIndex = successfulIndex + 1;
        int topPriority = targets.get(0).node().getPriority(targets.get(0).direction());
        int successfulPriority = targets.get(successfulIndex).node()
                .getPriority(targets.get(successfulIndex).direction());
        if (successfulPriority == topPriority && nextIndex < targets.size()
                && targets.get(nextIndex).node().getPriority(targets.get(nextIndex).direction()) == topPriority) {
            sourceEndpoint.resumeChemicalTargetScan(key, nextIndex, targets.size());
        } else {
            sourceEndpoint.resetChemicalTargetScan(key);
        }
    }

    private static int advanceResourceTargetScan(CachedEndpoint sourceEndpoint, TargetResource resource,
            int currentIndex, int targetCount) {
        int nextIndex = (currentIndex + 1) % targetCount;
        sourceEndpoint.resumeResourceTargetScan(resource, nextIndex, targetCount);
        return nextIndex;
    }

    private static void finishResourceTargetScan(CachedEndpoint sourceEndpoint, TargetResource resource,
            List<CachedEndpoint> targets, int successfulIndex) {
        int nextIndex = successfulIndex + 1;
        int topPriority = targets.get(0).node().getPriority(targets.get(0).direction());
        int successfulPriority = targets.get(successfulIndex).node()
                .getPriority(targets.get(successfulIndex).direction());
        if (successfulPriority == topPriority && nextIndex < targets.size()
                && targets.get(nextIndex).node().getPriority(targets.get(nextIndex).direction()) == topPriority) {
            sourceEndpoint.resumeResourceTargetScan(resource, nextIndex, targets.size());
        } else {
            sourceEndpoint.resetResourceTargetScan(resource);
        }
    }

    private static MoveResult targetMoveResult(boolean moved, int operations, int targetVisits) {
        return new MoveResult(moved, Math.max(operations, targetVisits));
    }

    private record DimensionDirectResult(boolean moved, int operations, boolean scanFallback,
                                         boolean candidateFound) {
    }

    private record SourceSearchResult(int index, int skippedChecks, boolean exhausted) {
    }

    private record ItemSourceSearchResult(int index, int skippedChecks, boolean exhausted, boolean preferred) {
    }

    private record TargetItemSlot(int lane, int slot, int movable, int existingCount, int effectiveLimit,
            boolean simulationLimited, boolean usedHot) {
        private static final TargetItemSlot NONE = new TargetItemSlot(-1, -1, 0, 0, 0, false, false);

        private boolean filledAfter(int inserted, boolean hadLeftover) {
            return hadLeftover || existingCount + inserted >= effectiveLimit
                    || (simulationLimited && inserted >= movable);
        }
    }

    private record ItemSlotTransfer(boolean extracted, int inserted, boolean hadLeftover) {
    }

    private enum DirectSource {
        NONE,
        FORCED,
        SOPHISTICATED
    }

    private record SourceExtraction(ItemStack stack, DirectSource directSource) {
    }

    private record SimulatedItem(ItemStack stack, boolean forceExtractionSupported) {
    }

    private record TargetItemSelection(TargetItemSlot slot, int checks, boolean exhaustive) {
    }

    private record HandlerMoveResult(boolean moved, int slotChecks, boolean budgetExhausted) {
        private static final HandlerMoveResult NONE = new HandlerMoveResult(false, 0, false);
    }

    private record SlotLimitCheck(boolean blocked, int checks, boolean complete) {
        private static final SlotLimitCheck ALLOWED = new SlotLimitCheck(false, 0, true);
    }

    private record MoveResult(boolean moved, int operations) {
    }

    private record OrderedPerItemMoveResult(boolean moved, int operations, boolean continueSourceSearch,
            int movedItems) {
    }

    private static final class ExactItemScan {
        private final int slots;
        private int nextSlot;
        private long total;

        private ExactItemScan(int slots) {
            this.slots = slots;
        }
    }

    private record ExactItemScanResult(boolean complete, long total, int checks) {
        private static final ExactItemScanResult NOT_CONFIGURED = new ExactItemScanResult(true, -1L, 0);
    }

    private static final class SlotLimitScan {
        private final int slots;
        private final int limit;
        private final ItemStackKey candidate;
        private int nextSlot;
        private int matchingSlots;
        private boolean canRefill;

        private SlotLimitScan(int slots, int limit, ItemStackKey candidate) {
            this.slots = slots;
            this.limit = limit;
            this.candidate = candidate;
        }

        private boolean matches(int slots, int limit, ItemStackKey candidate) {
            return this.slots == slots && this.limit == limit && java.util.Objects.equals(this.candidate, candidate);
        }
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    private static void configureMaintainedBackoff(CachedEndpoint source, List<CachedEndpoint> targets,
            boolean enabled, BiConsumer<CachedEndpoint, Boolean> setter) {
        boolean active = MaintainedResourcePolicy.pathUsesMaintainedBackoff(enabled,
                source.node().getMaintainAmount(source.direction()), targets.stream().anyMatch(endpoint ->
                        endpoint.node().getMaintainAmount(endpoint.direction()) > 0L));
        setter.accept(source, active);
        targets.forEach(endpoint -> setter.accept(endpoint, active));
    }


    private static long maintainedAllowance(CachedEndpoint endpoint, long requested, long stored, int occupied,
            long existingRefillCapacity) {
        NetworkEndpointBlockEntity node = endpoint.node();
        long target = node.getMaintainAmount(endpoint.direction());
        return MaintainedResourcePolicy.insertionAllowance(node.isMaintainByAmount(endpoint.direction()),
                requested, stored, occupied, target, SkyLogisticsConfig.fillMaintainedItemSlots(),
                existingRefillCapacity);
    }

    private static long maintainedFluidAllowance(CachedEndpoint endpoint, IFluidHandler handler, long requested) {
        if (!SkyLogisticsConfig.enableMaintainedFluidPolling() || endpoint.node().getMaintainAmount(endpoint.direction()) <= 0L) return requested;
        long stored = 0L, refill = 0L; int occupied = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) { FluidStack stack = handler.getFluidInTank(tank); if (stack.isEmpty() || !endpoint.node().allowsFluid(endpoint.direction(), stack)) continue; stored += stack.getAmount(); occupied++; refill += Math.max(0, handler.getTankCapacity(tank) - stack.getAmount()); }
        return maintainedAllowance(endpoint, requested, stored, occupied, refill);
    }

    private static long maintainedChemicalAllowance(CachedEndpoint endpoint, ChemicalHandlerBridge handler, long requested) {
        if (!SkyLogisticsConfig.enableMaintainedChemicalPolling() || endpoint.node().getMaintainAmount(endpoint.direction()) <= 0L) return requested;
        long stored = 0L; int occupied = 0; for (int tank = 0; tank < handler.getTanks(); tank++) { ChemicalStackView stack = handler.getChemicalInTank(tank); if (stack.isEmpty() || !endpoint.node().allowsChemical(endpoint.direction(), stack)) continue; stored += stack.getAmount(); occupied++; }
        return maintainedAllowance(endpoint, requested, stored, occupied, occupied > 0 ? requested : 0L);
    }

    private static long maintainedEnergyAllowance(CachedEndpoint endpoint, IEnergyStorage handler, long requested) {
        if (!SkyLogisticsConfig.enableMaintainedEnergyPolling() || endpoint.node().getMaintainAmount(endpoint.direction()) <= 0L) return requested;
        long stored = handler instanceof MaintainedStorageView view ? view.maintainedStoredAmount() : handler.getEnergyStored();
        int occupied = handler instanceof MaintainedStorageView view ? view.maintainedOccupiedStorageUnits() : stored > 0L ? 1 : 0;
        long refill = handler instanceof MaintainedStorageView view ? view.maintainedExistingUnitRefillCapacity() : stored > 0L ? Math.max(0L, handler.getMaxEnergyStored() - stored) : 0L;
        return maintainedAllowance(endpoint, requested, stored, occupied, refill);
    }

    private static long maintainedManaAllowance(CachedEndpoint endpoint, ManaHandlerBridge handler, long requested) {
        if (!SkyLogisticsConfig.enableMaintainedManaPolling() || endpoint.node().getMaintainAmount(endpoint.direction()) <= 0L) return requested;
        long stored = handler instanceof MaintainedStorageView view ? view.maintainedStoredAmount() : handler.getCurrentMana();
        int occupied = handler instanceof MaintainedStorageView view ? view.maintainedOccupiedStorageUnits() : stored > 0L ? 1 : 0;
        long refill = handler instanceof MaintainedStorageView view ? view.maintainedExistingUnitRefillCapacity() : stored > 0L ? Math.max(0L, handler.getMaxMana() - stored) : 0L;
        return maintainedAllowance(endpoint, requested, stored, occupied, refill);
    }

    private static long maintainedSourceAllowance(CachedEndpoint endpoint, SourceHandlerBridge handler, long requested) {
        if (!SkyLogisticsConfig.enableMaintainedSourcePolling() || endpoint.node().getMaintainAmount(endpoint.direction()) <= 0L) return requested;
        long stored = handler instanceof MaintainedStorageView view ? view.maintainedStoredAmount() : handler.getCurrentSource();
        int occupied = handler instanceof MaintainedStorageView view ? view.maintainedOccupiedStorageUnits() : stored > 0L ? 1 : 0;
        long refill = handler instanceof MaintainedStorageView view ? view.maintainedExistingUnitRefillCapacity() : stored > 0L ? Math.max(0L, handler.getMaxSource() - stored) : 0L;
        return maintainedAllowance(endpoint, requested, stored, occupied, refill);
    }

}
