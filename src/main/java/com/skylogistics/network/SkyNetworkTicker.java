package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.network.SkyNetworkRegistry.CachedEndpoint;
import com.skylogistics.network.SkyNetworkRegistry.LineIndex;
import com.skylogistics.network.SkyNetworkRegistry.ReadyLines;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.storage.ItemStackKey;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
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
                        input.recordItemFailure(gameTime);
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
                if (MekanismCompat.isLoaded() && node.isFluidsEnabled(input.direction())
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
                if (node.isEnergyEnabled(input.direction()) && input.canTryEnergy(gameTime)) {
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
            if (MekanismCompat.isLoaded()) {
                nextWake = Math.min(nextWake, input.nextChemicalWake(gameTime));
            }
        }
        if (node.isEnergyEnabled(input.direction())) {
            nextWake = Math.min(nextWake, input.nextEnergyWake(gameTime));
        }
        return nextWake;
    }

    private static int transferItems(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
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
        int operations = 0;
        boolean foundCandidate = false;
        int slotChecks = Math.min(slots, sourceNode.getOperationRate());
        int firstTriedSlot = -1;
        int secondTriedSlot = -1;
        boolean sourceSlotsExhausted = false;
        int transferLimit = SkyLogisticsConfig.nodeItemTransferLimit();
        for (int i = 0; i < slotChecks && operations < budget; i++) {
            int slot = nextItemSlot(sourceEndpoint, sourceNode, slots, gameTime, firstTriedSlot, secondTriedSlot);
            if (slot < 0) {
                sourceSlotsExhausted = true;
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
                return operations;
            }
        }
        if (!foundCandidate) {
            sourceEndpoint.recordItemSourceMiss(sourceSlotsExhausted ? slots : operations, slots, gameTime);
        }
        return operations;
    }

    private static int nextItemSlot(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode, int slots,
            long gameTime, int firstTriedSlot, int secondTriedSlot) {
        if (sourceEndpoint.isItemSlotDiscoveryActive()) {
            int discoverySlot = nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                    firstTriedSlot, secondTriedSlot, true);
            if (discoverySlot >= 0) {
                sourceEndpoint.recordItemSlotDiscoveryCheck();
                return discoverySlot;
            }
            sourceEndpoint.clearItemSlotDiscovery();
        }
        int preferredSlot = sourceEndpoint.nextPreferredItemSlot(slots, gameTime, firstTriedSlot, secondTriedSlot);
        if (preferredSlot >= 0) {
            return preferredSlot;
        }
        return nextSequentialItemSlot(sourceEndpoint, sourceNode, slots, gameTime,
                firstTriedSlot, secondTriedSlot, false);
    }

    private static int nextSequentialItemSlot(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode, int slots,
            long gameTime, int firstTriedSlot, int secondTriedSlot, boolean ignoreEmptyCooldown) {
        for (int attempts = 0; attempts < slots; attempts++) {
            int slot = sourceNode.nextItemStart(slots);
            if (wasSlotTried(firstTriedSlot, secondTriedSlot, slot)
                    || (!ignoreEmptyCooldown && !sourceEndpoint.canTryItemSlot(slot, gameTime))) {
                continue;
            }
            return slot;
        }
        return -1;
    }

    private static boolean wasSlotTried(int firstTriedSlot, int secondTriedSlot, int slot) {
        return firstTriedSlot == slot || secondTriedSlot == slot;
    }

    private static MoveResult tryMoveItem(CachedEndpoint sourceEndpoint, IItemHandler source, int slot, ItemStack simulated,
            List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        ItemVaultBlockEntity sourceVault = sourceEndpoint.targetBlockEntity() instanceof ItemVaultBlockEntity vault
                ? vault
                : null;
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
                if (sourceVault != null && targetEndpoint.targetBlockEntity() instanceof ItemVaultBlockEntity targetVault) {
                    long moved = sourceVault.transferTo(targetVault, slot, skyContainerTransferLimit);
                    if (moved > 0) {
                        targetEndpoint.recordItemSuccess();
                        return new MoveResult(true, operations);
                    }
                    targetEndpoint.recordItemAcceptReject(simulatedKey, gameTime);
                    continue;
                }
                IItemHandler target = targetEndpoint.itemHandler(gameTime);
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
            int tank = nextFluidTank(sourceEndpoint, sourceNode, tanks, gameTime, firstTriedTank, secondTriedTank);
            if (tank < 0) {
                sourceTanksExhausted = true;
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
                return operations;
            }
        }
        if (!foundCandidate) {
            sourceEndpoint.recordFluidSourceMiss(sourceTanksExhausted ? tanks : operations, tanks, gameTime);
        }
        return operations;
    }

    private static int nextFluidTank(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode, int tanks,
            long gameTime, int firstTriedTank, int secondTriedTank) {
        if (sourceEndpoint.isFluidTankDiscoveryActive()) {
            int discoveryTank = nextSequentialFluidTank(sourceEndpoint, sourceNode, tanks, gameTime,
                    firstTriedTank, secondTriedTank, true);
            if (discoveryTank >= 0) {
                sourceEndpoint.recordFluidTankDiscoveryCheck();
                return discoveryTank;
            }
            sourceEndpoint.clearFluidTankDiscovery();
        }
        int preferredTank = sourceEndpoint.nextPreferredFluidTank(tanks, gameTime, firstTriedTank, secondTriedTank);
        if (preferredTank >= 0) {
            return preferredTank;
        }
        return nextSequentialFluidTank(sourceEndpoint, sourceNode, tanks, gameTime,
                firstTriedTank, secondTriedTank, false);
    }

    private static int nextSequentialFluidTank(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode, int tanks,
            long gameTime, int firstTriedTank, int secondTriedTank, boolean ignoreEmptyCooldown) {
        for (int attempts = 0; attempts < tanks; attempts++) {
            int tank = sourceNode.nextFluidStart(tanks);
            if (wasSlotTried(firstTriedTank, secondTriedTank, tank)
                    || (!ignoreEmptyCooldown && !sourceEndpoint.canTryFluidTank(tank, gameTime))) {
                continue;
            }
            return tank;
        }
        return -1;
    }

    private static MoveResult tryMoveFluid(CachedEndpoint sourceEndpoint, IFluidHandler source, int sourceTank,
            FluidStack simulated, List<CachedEndpoint> targets, int budget, long gameTime) {
        if (budget <= 0) {
            return new MoveResult(false, 0);
        }
        FluidVaultBlockEntity sourceVault = sourceEndpoint.targetBlockEntity() instanceof FluidVaultBlockEntity vault
                ? vault
                : null;
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
                if (sourceVault != null && targetEndpoint.targetBlockEntity() instanceof FluidVaultBlockEntity targetVault) {
                    long moved = sourceVault.transferTo(targetVault, sourceTank, skyContainerTransferLimit);
                    if (moved > 0) {
                        targetEndpoint.recordFluidSuccess();
                        return new MoveResult(true, operations);
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

    private static int transferChemicals(CachedEndpoint sourceEndpoint, List<CachedEndpoint> targets, int budget,
            long gameTime) {
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
            int tank = nextChemicalTank(sourceEndpoint, sourceNode, tanks, gameTime, firstTriedTank, secondTriedTank);
            if (tank < 0) {
                sourceTanksExhausted = true;
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
                return operations;
            }
        }
        if (!foundCandidate) {
            sourceEndpoint.recordChemicalSourceMiss(sourceTanksExhausted ? tanks : operations, tanks, gameTime);
        }
        return operations;
    }

    private static int nextChemicalTank(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode, int tanks,
            long gameTime, int firstTriedTank, int secondTriedTank) {
        if (sourceEndpoint.isChemicalTankDiscoveryActive()) {
            int discoveryTank = nextSequentialChemicalTank(sourceEndpoint, sourceNode, tanks, gameTime,
                    firstTriedTank, secondTriedTank, true);
            if (discoveryTank >= 0) {
                sourceEndpoint.recordChemicalTankDiscoveryCheck();
                return discoveryTank;
            }
            sourceEndpoint.clearChemicalTankDiscovery();
        }
        int preferredTank = sourceEndpoint.nextPreferredChemicalTank(tanks, gameTime, firstTriedTank, secondTriedTank);
        if (preferredTank >= 0) {
            return preferredTank;
        }
        return nextSequentialChemicalTank(sourceEndpoint, sourceNode, tanks, gameTime,
                firstTriedTank, secondTriedTank, false);
    }

    private static int nextSequentialChemicalTank(CachedEndpoint sourceEndpoint, SkyNodeBlockEntity sourceNode,
            int tanks, long gameTime, int firstTriedTank, int secondTriedTank, boolean ignoreEmptyCooldown) {
        for (int attempts = 0; attempts < tanks; attempts++) {
            int tank = sourceNode.nextFluidStart(tanks);
            if (wasSlotTried(firstTriedTank, secondTriedTank, tank)
                    || (!ignoreEmptyCooldown && !sourceEndpoint.canTryChemicalTank(tank, gameTime))) {
                continue;
            }
            return tank;
        }
        return -1;
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
                                "Energy rollback failed after simulated target receive changed during transfer. Source node {} face {}, target node {} face {}, extracted {} FE, inserted {} FE, rollback remainder {} FE",
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

    private record MoveResult(boolean moved, int operations) {
    }

    private static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }
}
