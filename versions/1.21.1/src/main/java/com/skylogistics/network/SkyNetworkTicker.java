package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.SkyNetworkRegistry.CachedEndpoint;
import com.skylogistics.network.SkyNetworkRegistry.LineIndex;
import com.skylogistics.network.SkyNetworkRegistry.ReadyLines;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.storage.ItemStackKey;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class SkyNetworkTicker {
    private SkyNetworkTicker() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        process(event.getServer());
    }

    private static void process(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        int serverOpsPerTick = SkyLogisticsConfig.serverOpsPerTick();
        int lineOpsPerTick = SkyLogisticsConfig.lineOpsPerTick();
        int operations = 0;
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
            boolean lineBudgetExhausted = false;
            int inputCount = line.inputCount();
            for (int inputIndex = 0; inputIndex < inputCount; inputIndex++) {
                CachedEndpoint input = line.inputAt(inputIndex);
                if (operations >= serverOpsPerTick) {
                    line.advanceInputCursor();
                    return;
                }
                int lineOperations = operations - lineOperationsBefore;
                if (lineOperations >= lineOpsPerTick) {
                    lineBudgetExhausted = true;
                    break;
                }
                SkyNodeBlockEntity node = input.node();
                if (!node.isFaceRedstoneAllowed(input.direction())) {
                    nextWake = Math.min(nextWake, gameTime + 20L);
                    continue;
                }
                boolean dimensionUpgrade = node.hasDimensionUpgrade();
                int remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (node.isItemsEnabled(input.direction()) && input.canTryItems(gameTime)) {
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
                    line.advanceInputCursor();
                    return;
                }
                remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (remainingLineBudget <= 0) {
                    lineBudgetExhausted = true;
                    break;
                }
                if (node.isFluidsEnabled(input.direction()) && input.canTryFluids(gameTime)) {
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
                    line.advanceInputCursor();
                    return;
                }
                remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (remainingLineBudget <= 0) {
                    lineBudgetExhausted = true;
                    break;
                }
                if (SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()
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
                    line.advanceInputCursor();
                    return;
                }
                remainingLineBudget = lineOpsPerTick - (operations - lineOperationsBefore);
                if (remainingLineBudget <= 0) {
                    lineBudgetExhausted = true;
                    break;
                }
                if (node.isEnergyEnabled(input.direction())
                        && (input.canTryEnergy(gameTime)
                        || (canTransferMana() && input.canTryMana(gameTime))
                        || (canTransferSource() && input.canTrySource(gameTime)))) {
                    if (dimensionUpgrade && globalEnergyOutputs == null) {
                        globalEnergyOutputs = SkyNetworkRegistry.globalEnergyOutputs(line.lineId());
                    }
                    List<CachedEndpoint> targets = targetsFor(dimensionUpgrade, line.priorityEnergyOutputs(),
                            globalEnergyOutputs);
                    if (targets.isEmpty()) {
                        if (input.canTryEnergy(gameTime)) {
                            input.recordEnergyFailure(gameTime);
                        }
                        if (canTransferSource() && input.canTrySource(gameTime)) {
                            input.recordSourceFailure(gameTime);
                        }
                        if (canTransferMana() && input.canTryMana(gameTime)) {
                            input.recordManaFailure(gameTime);
                        }
                    } else {
                        int resourceBudget = Math.min(serverOpsPerTick - operations, remainingLineBudget);
                        if (input.canTryEnergy(gameTime)) {
                            int used = transferEnergy(input, targets, resourceBudget, gameTime);
                            operations += used;
                            resourceBudget -= used;
                        }
                        if (canTransferMana() && resourceBudget > 0 && input.canTryMana(gameTime)) {
                            int used = transferMana(input, targets, resourceBudget, gameTime);
                            operations += used;
                            resourceBudget -= used;
                        }
                        if (canTransferSource() && resourceBudget > 0 && input.canTrySource(gameTime)) {
                            operations += transferSource(input, targets, resourceBudget, gameTime);
                        }
                    }
                }
                nextWake = nextInputWake(input, node, gameTime, nextWake);
            }
            line.advanceInputCursor();
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

    private static long nextInputWake(CachedEndpoint input, SkyNodeBlockEntity node, long gameTime, long current) {
        long nextWake = current;
        if (node.isItemsEnabled(input.direction())) {
            nextWake = Math.min(nextWake, input.nextItemWake(gameTime));
        }
        if (node.isFluidsEnabled(input.direction())) {
            nextWake = Math.min(nextWake, input.nextFluidWake(gameTime));
            if (SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()) {
                nextWake = Math.min(nextWake, input.nextChemicalWake(gameTime));
            }
        }
        if (node.isEnergyEnabled(input.direction())) {
            nextWake = Math.min(nextWake, input.nextEnergyWake(gameTime));
            if (canTransferMana()) {
                nextWake = Math.min(nextWake, input.nextManaWake(gameTime));
            }
            if (canTransferSource()) {
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
        if (isDimensionItemEndpoint(sourceEndpoint)) {
            return transferDimensionItems(sourceEndpoint, targets, budget, gameTime);
        }
        SkyNodeBlockEntity sourceNode = sourceEndpoint.node();
        IItemHandler source = sourceEndpoint.itemHandler(gameTime);
        if (source == null || budget <= 0) {
            return 0;
        }
        int slots = source.getSlots();
        if (slots <= 0) {
            sourceEndpoint.recordItemFailure(gameTime);
            return 0;
        }
        if (isExtractionBlockedBySlotLimit(sourceNode, sourceEndpoint.direction(), source)) {
            sourceEndpoint.recordItemFailure(gameTime);
            return 0;
        }
        int operations = 0;
        boolean foundCandidate = false;
        int slotChecks = Math.min(slots, sourceNode.getOperationRate());
        int firstTriedSlot = -1;
        int secondTriedSlot = -1;
        boolean sourceSlotsExhausted = false;
        int transferLimit = SkyLogisticsConfig.nodeItemTransferLimit();
        for (int i = 0; i < slotChecks && operations < budget; i++) {
            SourceSearchResult search = nextItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                    firstTriedSlot, secondTriedSlot, budget - operations);
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
            ItemStack simulated = source.extractItem(slot, transferLimit, true);
            operations++;
            if (simulated.isEmpty()) {
                sourceEndpoint.recordItemSlotMiss(slot, gameTime);
                continue;
            }
            if (!sourceNode.allowsItem(sourceEndpoint.direction(), simulated)) {
                sourceEndpoint.recordItemSlotRejected(slot, gameTime);
                continue;
            }
            foundCandidate = true;
            sourceEndpoint.recordItemCandidateFound();
            MoveResult result = tryMoveItem(sourceEndpoint, source, slot, simulated, targets,
                    budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                sourceEndpoint.recordItemSlotSuccess(slot, slots);
                sourceEndpoint.recordItemSuccess();
            }
        }
        if (!foundCandidate) {
            sourceEndpoint.recordItemSourceMiss(sourceSlotsExhausted ? slots : operations, slots, gameTime);
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
        SkyNodeBlockEntity sourceNode = sourceEndpoint.node();
        int slots = BeyondDimensionsCompat.itemTypeCount(sourceBlockEntity);
        if (slots <= 0) {
            sourceEndpoint.recordItemFailure(gameTime);
            return operations;
        }
        boolean foundCandidate = false;
        int slotChecks = Math.min(slots, sourceNode.getOperationRate());
        int firstTriedSlot = -1;
        int secondTriedSlot = -1;
        boolean sourceSlotsExhausted = false;
        for (int i = 0; i < slotChecks && operations < budget; i++) {
            SourceSearchResult search = nextItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                    firstTriedSlot, secondTriedSlot, budget - operations);
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
                sourceEndpoint.recordItemSlotSuccess(slot, slots);
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
        DimensionWhitelistCandidates candidates = dimensionWhitelistCandidates(sourceEndpoint.node(),
                sourceEndpoint.direction());
        if (!candidates.hasWhitelist()) {
            return new DimensionDirectResult(false, 0, true, false);
        }
        int operations = 0;
        boolean candidateFound = false;
        for (ItemStack sample : candidates.samples()) {
            if (operations >= budget) {
                break;
            }
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
        for (TagKey<Item> tag : candidates.tags()) {
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

    private static DimensionWhitelistCandidates dimensionWhitelistCandidates(SkyNodeBlockEntity node,
            net.minecraft.core.Direction direction) {
        List<ItemStack> samples = new ArrayList<>();
        List<TagKey<Item>> tags = new ArrayList<>();
        boolean hasWhitelist = false;
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            FilterListItem.CompiledFilter compiled = FilterListItem.compile(node.getFaceFilter(direction, slot));
            if (!compiled.whitelist() || !compiled.hasItemRules()) {
                continue;
            }
            hasWhitelist = true;
            samples.addAll(compiled.itemSamples());
            tags.addAll(compiled.itemTags());
        }
        return new DimensionWhitelistCandidates(hasWhitelist, samples, tags);
    }

    private static MoveResult tryMoveDimensionItem(CachedEndpoint sourceEndpoint, BlockEntity sourceBlockEntity,
            ItemStack simulated, long available, List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0 || simulated.isEmpty() || available <= 0L) {
            return new MoveResult(false, 0);
        }
        LongItemEndpoint sourceLongEndpoint = new DimensionItemLongEndpoint(sourceBlockEntity);
        long skyContainerTransferLimit = SkyLogisticsConfig.skyContainerTransferLimit();
        int targetCursor = sourceEndpoint.node().nextTargetCursor();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        ItemStackKey simulatedKey = ItemStackKey.of(simulated);
        targetLoop:
        for (int groupStart = 0; groupStart < targets.size();) {
            int groupEnd = priorityGroupEnd(targets, groupStart);
            int groupSize = groupEnd - groupStart;
            int groupCursor = Math.floorMod(targetCursor, groupSize);
            for (int groupOffset = 0; groupOffset < groupSize; groupOffset++) {
                CachedEndpoint targetEndpoint = targetInGroup(targets, groupStart, groupSize, groupCursor,
                        groupOffset);
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryItems(gameTime)
                        || !targetEndpoint.node().isItemsEnabled(targetEndpoint.direction())) {
                    continue;
                }
                if (targetEndpoint.isItemFilterRejected(simulated, gameTime)
                        || targetEndpoint.isItemAcceptRejected(simulatedKey, gameTime)) {
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                if (!targetEndpoint.node().allowsItem(targetEndpoint.direction(), simulated)) {
                    targetEndpoint.recordItemFilterReject(simulated, gameTime);
                    continue;
                }
                LongItemEndpoint targetLongEndpoint = longItemEndpoint(targetEndpoint);
                if (targetLongEndpoint != null) {
                    long moved = moveLongItemStack(sourceEndpoint, sourceLongEndpoint, simulated, available,
                            targetEndpoint, targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        targetEndpoint.recordItemSuccess();
                        return new MoveResult(true, operations);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordItemFailure(gameTime);
                        return new MoveResult(false, operations);
                    }
                    targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                if (tryMoveDimensionItemToHandler(sourceEndpoint, sourceBlockEntity, simulated, available,
                        targetEndpoint, targetEndpoint.itemHandler(gameTime), simulatedKey, gameTime)) {
                    targetEndpoint.recordItemSuccess();
                    return new MoveResult(true, operations);
                }
            }
            groupStart = groupEnd;
        }
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordItemFailure(gameTime);
        }
        return new MoveResult(false, operations);
    }

    private static boolean tryMoveDimensionItemToHandler(CachedEndpoint sourceEndpoint, BlockEntity sourceBlockEntity,
            ItemStack simulated, long available, CachedEndpoint targetEndpoint, IItemHandler target,
            ItemStackKey simulatedKey, long gameTime) {
        if (target == null || isInsertionBlockedBySlotLimit(targetEndpoint, target, simulated)) {
            return false;
        }
        int requested = (int) Math.min(Math.min(available, SkyLogisticsConfig.nodeItemTransferLimit()),
                Integer.MAX_VALUE);
        if (requested <= 0) {
            return false;
        }
        ItemStack offer = simulated.copyWithCount(requested);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, offer.copy(), true);
        int movable = offer.getCount() - remainder.getCount();
        if (movable <= 0) {
            targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
            return false;
        }
        long extractedAmount = BeyondDimensionsCompat.extractItem(sourceBlockEntity, offer, movable, false);
        if (extractedAmount <= 0L) {
            sourceEndpoint.recordItemFailure(gameTime);
            return false;
        }
        ItemStack extracted = offer.copyWithCount((int) extractedAmount);
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, extracted, false);
        if (!leftover.isEmpty()) {
            long rolledBack = BeyondDimensionsCompat.insertItem(sourceBlockEntity, leftover, leftover.getCount(),
                    false);
            if (rolledBack < leftover.getCount()) {
                SkyLogistics.LOGGER.warn(
                        "Item rollback failed after simulated target insertion changed during dimension transfer. Source node {} face {}, target node {} face {}, extracted {}, target leftover {}, rollback remainder {}",
                        sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                        targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), extracted, leftover,
                        leftover.getCount() - rolledBack);
            }
        }
        return true;
    }

    private static SourceSearchResult nextItemSlot(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode,
            int slots, long gameTime, int firstTriedSlot, int secondTriedSlot, int skipBudget) {
        if (sourceEndpoint.isItemSlotDiscoveryActive()) {
            SourceSearchResult discovery = nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                    firstTriedSlot, secondTriedSlot, true, skipBudget);
            if (discovery.index() >= 0) {
                sourceEndpoint.recordItemSlotDiscoveryCheck();
                return discovery;
            }
            if (discovery.exhausted()) {
                sourceEndpoint.clearItemSlotDiscovery();
            } else {
                return discovery;
            }
        }
        int preferredSlot = sourceEndpoint.nextPreferredItemSlot(slots, gameTime, firstTriedSlot, secondTriedSlot);
        if (preferredSlot >= 0) {
            return new SourceSearchResult(preferredSlot, 0, false);
        }
        return nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                firstTriedSlot, secondTriedSlot, false, skipBudget);
    }

    private static SourceSearchResult nextSequentialItemSlot(CachedEndpoint sourceEndpoint,
            SkyNodeBlockEntity sourceNode, int slots, long gameTime, int firstTriedSlot, int secondTriedSlot,
            boolean ignoreEmptyCooldown, int skipBudget) {
        int skippedChecks = 0;
        int attemptLimit = Math.min(slots, SkyLogisticsConfig.sourceSearchAttemptsPerEndpoint());
        for (int attempts = 0; attempts < attemptLimit; attempts++) {
            int slot = sourceNode.nextItemStart(slots);
            if (wasSlotTried(firstTriedSlot, secondTriedSlot, slot)
                    || (!ignoreEmptyCooldown && !sourceEndpoint.canTryItemSlot(slot, gameTime))) {
                skippedChecks++;
                if (skippedChecks >= skipBudget) {
                    return new SourceSearchResult(-1, skippedChecks, false);
                }
                continue;
            }
            return new SourceSearchResult(slot, skippedChecks, false);
        }
        return new SourceSearchResult(-1, skippedChecks, attemptLimit >= slots);
    }

    private static boolean wasSlotTried(int firstTriedSlot, int secondTriedSlot, int slot) {
        return firstTriedSlot == slot || secondTriedSlot == slot;
    }

    private static boolean isExtractionBlockedBySlotLimit(SkyNodeBlockEntity node, net.minecraft.core.Direction direction,
            IItemHandler source) {
        int limit = node.getItemSlotLimit(direction);
        return limit > SkyNodeBlockEntity.ITEM_SLOT_LIMIT_UNLIMITED
                && countMatchingItemSlots(source, stack -> node.allowsItem(direction, stack), limit + 1) <= limit;
    }

    private static boolean isInsertionBlockedBySlotLimit(CachedEndpoint endpoint, IItemHandler target,
            ItemStack candidate) {
        if (target == null) {
            return false;
        }
        SkyNodeBlockEntity node = endpoint.node();
        net.minecraft.core.Direction direction = endpoint.direction();
        int limit = node.getItemSlotLimit(direction);
        return limit > SkyNodeBlockEntity.ITEM_SLOT_LIMIT_UNLIMITED
                && countMatchingItemSlots(target, stack -> node.allowsItem(direction, stack), limit) >= limit
                && !canRefillMatchingItemSlot(target, node, direction, candidate);
    }

    private static int countMatchingItemSlots(IItemHandler handler, Predicate<ItemStack> predicate, int stopAt) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                count++;
                if (count >= stopAt) {
                    return count;
                }
            }
        }
        return count;
    }

    private static boolean canRefillMatchingItemSlot(IItemHandler handler, SkyNodeBlockEntity node,
            net.minecraft.core.Direction direction, ItemStack candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        ItemStack probe = candidate.copy();
        probe.setCount(1);
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack existing = handler.getStackInSlot(slot);
            if (existing.isEmpty() || !node.allowsItem(direction, existing)
                    || !StackData.sameItemAndComponents(existing, candidate)) {
                continue;
            }
            if (handler.insertItem(slot, probe, true).getCount() < probe.getCount()) {
                return true;
            }
        }
        return false;
    }

    private static MoveResult tryMoveItem(CachedEndpoint sourceEndpoint, IItemHandler source, int slot, ItemStack simulated,
            List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        LongItemEndpoint sourceLongEndpoint = longItemEndpoint(sourceEndpoint);
        long skyContainerTransferLimit = SkyLogisticsConfig.skyContainerTransferLimit();
        int targetCursor = sourceEndpoint.node().nextTargetCursor();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        ItemStackKey simulatedKey = null;
        targetLoop:
        for (int groupStart = 0; groupStart < targets.size();) {
            int groupEnd = priorityGroupEnd(targets, groupStart);
            int groupSize = groupEnd - groupStart;
            int groupCursor = Math.floorMod(targetCursor, groupSize);
            for (int groupOffset = 0; groupOffset < groupSize; groupOffset++) {
                CachedEndpoint targetEndpoint = targetInGroup(targets, groupStart, groupSize, groupCursor,
                        groupOffset);
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryItems(gameTime)
                        || !targetEndpoint.node().isItemsEnabled(targetEndpoint.direction())) {
                    continue;
                }
                if (targetEndpoint.isItemFilterRejected(simulated, gameTime)) {
                    continue;
                }
                if (simulatedKey == null) {
                    simulatedKey = ItemStackKey.of(simulated);
                }
                if (targetEndpoint.isItemAcceptRejected(simulatedKey, gameTime)) {
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                if (!targetEndpoint.node().allowsItem(targetEndpoint.direction(), simulated)) {
                    targetEndpoint.recordItemFilterReject(simulated, gameTime);
                    continue;
                }
                LongItemEndpoint targetLongEndpoint = longItemEndpoint(targetEndpoint);
                if (sourceLongEndpoint != null && targetLongEndpoint != null) {
                    long moved = moveLongItem(sourceEndpoint, sourceLongEndpoint, slot, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        targetEndpoint.recordItemSuccess();
                        return new MoveResult(true, operations);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordItemFailure(gameTime);
                        return new MoveResult(false, operations);
                    }
                    targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                if (targetLongEndpoint != null) {
                    long moved = moveItemToLongTarget(sourceEndpoint, source, slot, simulated, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        targetEndpoint.recordItemSuccess();
                        return new MoveResult(true, operations);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordItemFailure(gameTime);
                        return new MoveResult(false, operations);
                    }
                    targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                IItemHandler target = targetEndpoint.itemHandler(gameTime);
                if (isInsertionBlockedBySlotLimit(targetEndpoint, target, simulated)) {
                    continue;
                }
                if (target == null) {
                    continue;
                }
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, simulated.copy(), true);
                int movable = simulated.getCount() - remainder.getCount();
                if (movable <= 0) {
                    targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                ItemStack extracted = source.extractItem(slot, movable, false);
                if (extracted.isEmpty()) {
                    sourceEndpoint.recordItemFailure(gameTime);
                    return new MoveResult(false, operations);
                }
                ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, extracted, false);
                if (!leftover.isEmpty()) {
                    ItemStack rollbackRemainder = ItemHandlerHelper.insertItemStacked(source, leftover, false);
                    if (!rollbackRemainder.isEmpty()) {
                        SkyLogistics.LOGGER.warn(
                                "Item rollback failed after simulated target insertion changed during transfer. Source node {} face {}, target node {} face {}, source slot {}, extracted {}, target leftover {}, rollback remainder {}",
                                sourceEndpoint.node().getBlockPos(), sourceEndpoint.direction(),
                                targetEndpoint.node().getBlockPos(), targetEndpoint.direction(), slot, extracted,
                                leftover, rollbackRemainder);
                    }
                }
                targetEndpoint.recordItemSuccess();
                return new MoveResult(true, operations);
            }
            groupStart = groupEnd;
        }
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordItemFailure(gameTime);
        }
        return new MoveResult(false, operations);
    }

    private static LongItemEndpoint longItemEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.targetBlockEntity();
        if (blockEntity instanceof ItemVaultBlockEntity vault) {
            return new ItemVaultLongEndpoint(vault);
        }
        if (blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
            return new DimensionItemLongEndpoint(blockEntity);
        }
        return null;
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
            ItemStack simulated, CachedEndpoint targetEndpoint, LongItemEndpoint targetEndpointLong, long maxAmount) {
        long requested = Math.min(maxAmount, simulated.getCount());
        if (requested <= 0L) {
            return 0L;
        }
        long accepted = targetEndpointLong.insert(simulated, requested, true);
        if (accepted <= 0L) {
            return 0L;
        }
        ItemStack extracted = source.extractItem(sourceSlot, (int) Math.min(Integer.MAX_VALUE, accepted), false);
        if (extracted.isEmpty()) {
            return -1L;
        }
        long inserted = targetEndpointLong.insert(extracted, extracted.getCount(), false);
        if (inserted < extracted.getCount()) {
            ItemStack rollback = extracted.copyWithCount((int) (extracted.getCount() - inserted));
            ItemStack rollbackRemainder = ItemHandlerHelper.insertItemStacked(source, rollback, false);
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

        long insert(ItemStack stack, long amount, boolean simulate);

        long extract(int slot, ItemStack stack, long amount, boolean simulate);

        boolean sameStorage(LongItemEndpoint other);
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

    private static int transferFluids(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        SkyNodeBlockEntity sourceNode = sourceEndpoint.node();
        IFluidHandler source = sourceEndpoint.fluidHandler(gameTime);
        if (source == null || budget <= 0) {
            return 0;
        }
        int tanks = source.getTanks();
        if (tanks <= 0) {
            sourceEndpoint.recordFluidFailure(gameTime);
            return 0;
        }
        int operations = 0;
        boolean foundCandidate = false;
        int tankChecks = Math.min(tanks,
                Math.min(sourceNode.getOperationRate(), SkyLogisticsConfig.externalTankScansPerEndpoint()));
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
                sourceEndpoint.recordFluidTankMiss(tank, gameTime);
                continue;
            }
            FluidStack simulated = source.drain(copyWithAmount(inTank, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty()) {
                sourceEndpoint.recordFluidTankMiss(tank, gameTime);
                continue;
            }
            if (!sourceNode.allowsFluid(sourceEndpoint.direction(), simulated)) {
                sourceEndpoint.recordFluidTankRejected(tank, gameTime);
                continue;
            }
            foundCandidate = true;
            sourceEndpoint.recordFluidCandidateFound();
            MoveResult result = tryMoveFluid(sourceEndpoint, source, tank, simulated, targets,
                    budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                sourceEndpoint.recordFluidTankSuccess(tank, tanks);
                sourceEndpoint.recordFluidSuccess();
            }
        }
        if (!foundCandidate) {
            sourceEndpoint.recordFluidSourceMiss(sourceTanksExhausted ? tanks : operations, tanks, gameTime);
        }
        return operations;
    }

    private static SourceSearchResult nextFluidTank(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode,
            int tanks, long gameTime, int firstTriedTank, int secondTriedTank, int skipBudget) {
        if (sourceEndpoint.isFluidTankDiscoveryActive()) {
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
        return nextSequentialFluidTank(sourceEndpoint, sourceNode, tanks, gameTime,
                firstTriedTank, secondTriedTank, false, skipBudget);
    }

    private static SourceSearchResult nextSequentialFluidTank(CachedEndpoint sourceEndpoint,
            SkyNodeBlockEntity sourceNode, int tanks, long gameTime, int firstTriedTank, int secondTriedTank,
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
        long skyContainerTransferLimit = SkyLogisticsConfig.skyContainerTransferLimit();
        int targetCursor = sourceEndpoint.node().nextTargetCursor();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        FluidStackKey simulatedKey = null;
        targetLoop:
        for (int groupStart = 0; groupStart < targets.size();) {
            int groupEnd = priorityGroupEnd(targets, groupStart);
            int groupSize = groupEnd - groupStart;
            int groupCursor = Math.floorMod(targetCursor, groupSize);
            for (int groupOffset = 0; groupOffset < groupSize; groupOffset++) {
                CachedEndpoint targetEndpoint = targetInGroup(targets, groupStart, groupSize, groupCursor,
                        groupOffset);
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryFluids(gameTime)
                        || !targetEndpoint.node().isFluidsEnabled(targetEndpoint.direction())) {
                    continue;
                }
                if (simulatedKey == null) {
                    simulatedKey = FluidStackKey.of(simulated);
                }
                if (targetEndpoint.isFluidAcceptRejected(simulatedKey, gameTime)) {
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                if (!targetEndpoint.node().allowsFluid(targetEndpoint.direction(), simulated)) {
                    targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                LongFluidEndpoint targetLongEndpoint = longFluidEndpoint(targetEndpoint);
                if (sourceLongEndpoint != null && targetLongEndpoint != null) {
                    long moved = moveLongFluid(sourceEndpoint, sourceLongEndpoint, sourceTank, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        targetEndpoint.recordFluidSuccess();
                        return new MoveResult(true, operations);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordFluidFailure(gameTime);
                        return new MoveResult(false, operations);
                    }
                    targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                IFluidHandler target = targetEndpoint.fluidHandler(gameTime);
                if (target == null) {
                    continue;
                }
                int accepted = target.fill(simulated.copy(), IFluidHandler.FluidAction.SIMULATE);
                if (accepted <= 0) {
                    targetEndpoint.recordFluidAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                FluidStack drained = source.drain(copyWithAmount(simulated, accepted), IFluidHandler.FluidAction.EXECUTE);
                if (drained.isEmpty()) {
                    sourceEndpoint.recordFluidFailure(gameTime);
                    return new MoveResult(false, operations);
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
                return new MoveResult(true, operations);
            }
            groupStart = groupEnd;
        }
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordFluidFailure(gameTime);
        }
        return new MoveResult(false, operations);
    }

    private static LongFluidEndpoint longFluidEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.targetBlockEntity();
        if (blockEntity instanceof FluidVaultBlockEntity vault) {
            return new FluidVaultLongEndpoint(vault);
        }
        if (blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
            return new DimensionFluidLongEndpoint(blockEntity);
        }
        return null;
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

    private interface LongFluidEndpoint {
        LongFluidResource resourceInTank(int tank);

        long insert(FluidStack stack, long amount, boolean simulate);

        long extract(int tank, FluidStack stack, long amount, boolean simulate);

        boolean sameStorage(LongFluidEndpoint other);
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
        if (!SkyLogisticsConfig.allowFluidChemicalTransfer()) {
            return 0;
        }
        SkyNodeBlockEntity sourceNode = sourceEndpoint.node();
        ChemicalHandlerBridge source = sourceEndpoint.chemicalHandler(gameTime);
        if (source == null || budget <= 0) {
            return 0;
        }
        int tanks = source.getTanks();
        if (tanks <= 0) {
            sourceEndpoint.recordChemicalFailure(gameTime);
            return 0;
        }
        int operations = 0;
        boolean foundCandidate = false;
        int tankChecks = Math.min(tanks,
                Math.min(sourceNode.getOperationRate(), SkyLogisticsConfig.externalTankScansPerEndpoint()));
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
                sourceEndpoint.recordChemicalTankMiss(tank, gameTime);
                continue;
            }
            ChemicalStackView simulated = source.extractChemical(tank, Long.MAX_VALUE, true);
            if (simulated.isEmpty()) {
                sourceEndpoint.recordChemicalTankMiss(tank, gameTime);
                continue;
            }
            foundCandidate = true;
            sourceEndpoint.recordChemicalCandidateFound();
            MoveResult result = tryMoveChemical(sourceEndpoint, source, tank, simulated, targets,
                    budget - operations, gameTime);
            operations += result.operations();
            if (result.moved()) {
                sourceEndpoint.recordChemicalTankSuccess(tank, tanks);
                sourceEndpoint.recordChemicalSuccess();
            }
        }
        if (!foundCandidate) {
            sourceEndpoint.recordChemicalSourceMiss(sourceTanksExhausted ? tanks : operations, tanks, gameTime);
        }
        return operations;
    }

    private static SourceSearchResult nextChemicalTank(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode,
            int tanks, long gameTime, int firstTriedTank, int secondTriedTank, int skipBudget) {
        if (sourceEndpoint.isChemicalTankDiscoveryActive()) {
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
        return nextSequentialChemicalTank(sourceEndpoint, sourceNode, tanks, gameTime,
                firstTriedTank, secondTriedTank, false, skipBudget);
    }

    private static SourceSearchResult nextSequentialChemicalTank(CachedEndpoint sourceEndpoint,
            SkyNodeBlockEntity sourceNode, int tanks, long gameTime, int firstTriedTank, int secondTriedTank,
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
        int targetCursor = sourceEndpoint.node().nextTargetCursor();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        targetLoop:
        for (int groupStart = 0; groupStart < targets.size();) {
            int groupEnd = priorityGroupEnd(targets, groupStart);
            int groupSize = groupEnd - groupStart;
            int groupCursor = Math.floorMod(targetCursor, groupSize);
            for (int groupOffset = 0; groupOffset < groupSize; groupOffset++) {
                CachedEndpoint targetEndpoint = targetInGroup(targets, groupStart, groupSize, groupCursor,
                        groupOffset);
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryChemicals(gameTime)
                        || !targetEndpoint.node().isFluidsEnabled(targetEndpoint.direction())
                        || targetEndpoint.isChemicalAcceptRejected(simulated, gameTime)) {
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                ChemicalHandlerBridge target = targetEndpoint.chemicalHandler(gameTime);
                if (target == null) {
                    continue;
                }
                long accepted = target.insertChemical(simulated, true);
                if (accepted <= 0L) {
                    targetEndpoint.recordChemicalAcceptReject(simulated, gameTime);
                    continue;
                }
                ChemicalStackView drained = source.extractChemical(sourceTank, accepted, false);
                if (drained.isEmpty()) {
                    sourceEndpoint.recordChemicalFailure(gameTime);
                    return new MoveResult(false, operations);
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
                return new MoveResult(true, operations);
            }
            groupStart = groupEnd;
        }
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordChemicalFailure(gameTime);
        }
        return new MoveResult(false, operations);
    }

    private static int transferEnergy(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        if (budget <= 0) {
            return 0;
        }
        IEnergyStorage source = sourceEndpoint.energyHandler(gameTime);
        if (source == null) {
            return 0;
        }
        int simulated = source.extractEnergy(SkyLogisticsConfig.nodeEnergyTransferLimit(), true);
        int operations = 1;
        if (simulated <= 0) {
            sourceEndpoint.recordEnergyFailure(gameTime);
            return operations;
        }
        MoveResult result = tryMoveEnergy(sourceEndpoint, source, simulated, targets, budget - operations, gameTime);
        operations += result.operations();
        if (result.moved()) {
            sourceEndpoint.recordEnergySuccess();
        }
        return operations;
    }

    private static MoveResult tryMoveEnergy(CachedEndpoint sourceEndpoint, IEnergyStorage source, int simulated,
            List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        LongEnergyEndpoint sourceLongEndpoint = longEnergyEndpoint(sourceEndpoint);
        long skyContainerTransferLimit = SkyLogisticsConfig.skyContainerTransferLimit();
        int targetCursor = sourceEndpoint.node().nextTargetCursor();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        targetLoop:
        for (int groupStart = 0; groupStart < targets.size();) {
            int groupEnd = priorityGroupEnd(targets, groupStart);
            int groupSize = groupEnd - groupStart;
            int groupCursor = Math.floorMod(targetCursor, groupSize);
            for (int groupOffset = 0; groupOffset < groupSize; groupOffset++) {
                CachedEndpoint targetEndpoint = targetInGroup(targets, groupStart, groupSize, groupCursor,
                        groupOffset);
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryEnergy(gameTime)
                        || !targetEndpoint.node().isEnergyEnabled(targetEndpoint.direction())) {
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                LongEnergyEndpoint targetLongEndpoint = sourceLongEndpoint == null ? null
                        : longEnergyEndpoint(targetEndpoint);
                if (targetLongEndpoint != null) {
                    long moved = moveLongEnergy(sourceEndpoint, sourceLongEndpoint, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        targetEndpoint.recordEnergySuccess();
                        return new MoveResult(true, operations);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordEnergyFailure(gameTime);
                        return new MoveResult(false, operations);
                    }
                    targetEndpoint.recordEnergyFailure(gameTime);
                    continue;
                }
                IEnergyStorage target = targetEndpoint.energyHandler(gameTime);
                if (target == null) {
                    continue;
                }
                int accepted = target.receiveEnergy(simulated, true);
                if (accepted <= 0) {
                    targetEndpoint.recordEnergyFailure(gameTime);
                    continue;
                }
                int extracted = source.extractEnergy(accepted, false);
                if (extracted <= 0) {
                    sourceEndpoint.recordEnergyFailure(gameTime);
                    return new MoveResult(false, operations);
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
                return new MoveResult(true, operations);
            }
            groupStart = groupEnd;
        }
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordEnergyFailure(gameTime);
        }
        return new MoveResult(false, operations);
    }

    private static LongEnergyEndpoint longEnergyEndpoint(CachedEndpoint endpoint) {
        BlockEntity blockEntity = endpoint.targetBlockEntity();
        if (blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
            return new DimensionEnergyLongEndpoint(blockEntity);
        }
        return null;
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

    private static int transferMana(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        if (!canTransferMana() || budget <= 0) {
            return 0;
        }
        ManaHandlerBridge source = sourceEndpoint.manaHandler(gameTime);
        if (source == null) {
            return 0;
        }
        int simulated = source.extractMana(SkyLogisticsConfig.nodeEnergyTransferLimit(), true);
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
        int targetCursor = sourceEndpoint.node().nextTargetCursor();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        targetLoop:
        for (int groupStart = 0; groupStart < targets.size();) {
            int groupEnd = priorityGroupEnd(targets, groupStart);
            int groupSize = groupEnd - groupStart;
            int groupCursor = Math.floorMod(targetCursor, groupSize);
            for (int groupOffset = 0; groupOffset < groupSize; groupOffset++) {
                CachedEndpoint targetEndpoint = targetInGroup(targets, groupStart, groupSize, groupCursor,
                        groupOffset);
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTryMana(gameTime)
                        || !targetEndpoint.node().isEnergyEnabled(targetEndpoint.direction())) {
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                ManaHandlerBridge target = targetEndpoint.manaHandler(gameTime);
                if (target == null) {
                    continue;
                }
                int accepted = target.insertMana(simulated, true);
                if (accepted <= 0) {
                    targetEndpoint.recordManaFailure(gameTime);
                    continue;
                }
                int extracted = source.extractMana(accepted, false);
                if (extracted <= 0) {
                    sourceEndpoint.recordManaFailure(gameTime);
                    return new MoveResult(false, operations);
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
                return new MoveResult(true, operations);
            }
            groupStart = groupEnd;
        }
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordManaFailure(gameTime);
        }
        return new MoveResult(false, operations);
    }

    private static int transferSource(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
        if (!canTransferSource() || budget <= 0) {
            return 0;
        }
        SourceHandlerBridge source = sourceEndpoint.sourceHandler(gameTime);
        if (source == null) {
            return 0;
        }
        int simulated = source.extractSource(SkyLogisticsConfig.nodeEnergyTransferLimit(), true);
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
        int targetCursor = sourceEndpoint.node().nextTargetCursor();
        int targetAttemptBudget = Math.min(budget, SkyLogisticsConfig.endpointTargetAttempts());
        int operations = 0;
        boolean redstoneBlocked = false;
        boolean budgetExhausted = false;
        targetLoop:
        for (int groupStart = 0; groupStart < targets.size();) {
            int groupEnd = priorityGroupEnd(targets, groupStart);
            int groupSize = groupEnd - groupStart;
            int groupCursor = Math.floorMod(targetCursor, groupSize);
            for (int groupOffset = 0; groupOffset < groupSize; groupOffset++) {
                CachedEndpoint targetEndpoint = targetInGroup(targets, groupStart, groupSize, groupCursor,
                        groupOffset);
                if (!targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())) {
                    redstoneBlocked = true;
                    continue;
                }
                if (!targetEndpoint.canTrySource(gameTime)
                        || !targetEndpoint.node().isEnergyEnabled(targetEndpoint.direction())) {
                    continue;
                }
                if (operations >= targetAttemptBudget) {
                    budgetExhausted = true;
                    break targetLoop;
                }
                operations++;
                LongSourceEndpoint targetLongEndpoint = sourceLongEndpoint == null ? null
                        : longSourceEndpoint(targetEndpoint);
                if (targetLongEndpoint != null) {
                    long moved = moveLongSource(sourceEndpoint, sourceLongEndpoint, targetEndpoint,
                            targetLongEndpoint, skyContainerTransferLimit);
                    if (moved > 0L) {
                        targetEndpoint.recordSourceSuccess();
                        return new MoveResult(true, operations);
                    }
                    if (moved < 0L) {
                        sourceEndpoint.recordSourceFailure(gameTime);
                        return new MoveResult(false, operations);
                    }
                    targetEndpoint.recordSourceFailure(gameTime);
                    continue;
                }
                SourceHandlerBridge target = targetEndpoint.sourceHandler(gameTime);
                if (target == null) {
                    continue;
                }
                int accepted = target.insertSource(simulated, true);
                if (accepted <= 0) {
                    targetEndpoint.recordSourceFailure(gameTime);
                    continue;
                }
                int extracted = source.extractSource(accepted, false);
                if (extracted <= 0) {
                    sourceEndpoint.recordSourceFailure(gameTime);
                    return new MoveResult(false, operations);
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
                return new MoveResult(true, operations);
            }
            groupStart = groupEnd;
        }
        if (!redstoneBlocked && !budgetExhausted) {
            sourceEndpoint.recordSourceFailure(gameTime);
        }
        return new MoveResult(false, operations);
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

    private static int priorityGroupEnd(List<CachedEndpoint> targets, int groupStart) {
        int priority = targets.get(groupStart).node().getPriority(targets.get(groupStart).direction());
        int groupEnd = groupStart + 1;
        while (groupEnd < targets.size()
                && targets.get(groupEnd).node().getPriority(targets.get(groupEnd).direction()) == priority) {
            groupEnd++;
        }
        return groupEnd;
    }

    private static CachedEndpoint targetInGroup(List<CachedEndpoint> targets, int groupStart, int groupSize,
            int cursor, int offset) {
        return targets.get(groupStart + (cursor + offset) % groupSize);
    }

    private record DimensionWhitelistCandidates(boolean hasWhitelist, List<ItemStack> samples,
                                                List<TagKey<Item>> tags) {
    }

    private record DimensionDirectResult(boolean moved, int operations, boolean scanFallback,
                                         boolean candidateFound) {
    }

    private record SourceSearchResult(int index, int skippedChecks, boolean exhausted) {
    }

    private record MoveResult(boolean moved, int operations) {
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }
}
