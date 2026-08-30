package com.skylogistics.compat.distributor;

import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DistributedChemicalHandler implements ChemicalHandlerBridge, BudgetedDistributorHandler {
    private static final int MAX_TARGETS = 64;
    private final DistributedHandlerLookup<ChemicalHandlerBridge> lookup;
    private final int[] visibleTanks = new int[MAX_TARGETS];
    private final int[] routeCandidates = new int[MAX_TARGETS];
    private final AdaptiveTargetProbeScheduler extractionProbes = new AdaptiveTargetProbeScheduler();
    private final HierarchicalTargetRouteCache<String> insertionRoutes = new HierarchicalTargetRouteCache<>();
    private ChemicalInsertPlan insertPlan;
    private boolean insertPlanAwaitingExecution;
    private int legacyInsertCursor;

    public DistributedChemicalHandler(DistributedHandlerLookup<ChemicalHandlerBridge> lookup) {
        this.lookup = lookup;
        Arrays.fill(visibleTanks, -1);
    }

    @Override public int getTanks() { return lookup.size(); }

    @Override public boolean distributorBudgetExhausted() { return lookup.budgetExhausted(); }

    @Override public boolean distributorScanPending() { return lookup.scanPending(); }

    @Override public boolean usesIndependentExtractionProbes() { return config().enabled(); }

    @Override public int nextFairExtractionSlot(long gameTime) {
        configureExtractionProbes();
        return extractionProbes.nextDueTarget(lookup.size(), gameTime);
    }

    @Override public int fairExtractionProbesDue(long gameTime) {
        configureExtractionProbes();
        return extractionProbes.dueProbeCount(lookup.size(), gameTime);
    }

    @Override public void setMaintainedExtractionPollTicks(int pollTicks) {
        extractionProbes.setMaximumInterval(pollTicks, lookup.gameTime());
        insertionRoutes.setMaximumInterval(pollTicks);
    }

    @Override
    public ChemicalStackView getChemicalInTank(int tank) {
        ChemicalHandlerBridge handler = handler(tank);
        if (handler == null) return EmptyChemicalStackView.INSTANCE;
        int tanks = handler.getTanks();
        if (tanks <= 0) {
            recordExtractionProbe(tank, false);
            return EmptyChemicalStackView.INSTANCE;
        }
        int preferred = visibleTanks[tank];
        if (preferred >= 0 && preferred < tanks) {
            if (!lookup.takeOperation()) return EmptyChemicalStackView.INSTANCE;
            ChemicalStackView stack = handler.getChemicalInTank(preferred);
            if (stack != null && !stack.isEmpty()) {
                recordExtractionProbe(tank, true);
                return stack;
            }
        }
        for (int sourceTank = 0; sourceTank < tanks; sourceTank++) {
            if (sourceTank == preferred) continue;
            if (!lookup.takeOperation()) return EmptyChemicalStackView.INSTANCE;
            ChemicalStackView stack = handler.getChemicalInTank(sourceTank);
            if (stack != null && !stack.isEmpty()) {
                visibleTanks[tank] = sourceTank;
                recordExtractionProbe(tank, true);
                return stack;
            }
        }
        visibleTanks[tank] = -1;
        recordExtractionProbe(tank, false);
        return EmptyChemicalStackView.INSTANCE;
    }

    @Override
    public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
        if (amount <= 0L) return EmptyChemicalStackView.INSTANCE;
        ChemicalHandlerBridge handler = handler(tank);
        if (handler == null) return EmptyChemicalStackView.INSTANCE;
        int sourceTank = visibleTanks[tank];
        if (sourceTank < 0 || sourceTank >= handler.getTanks()) {
            ChemicalStackView visible = getChemicalInTank(tank);
            if (visible.isEmpty()) return EmptyChemicalStackView.INSTANCE;
            sourceTank = visibleTanks[tank];
        }
        if (!lookup.takeOperation()) return EmptyChemicalStackView.INSTANCE;
        ChemicalStackView extracted = handler.extractChemical(sourceTank, amount, simulate);
        boolean available = extracted != null && !extracted.isEmpty();
        if (!simulate && !available) visibleTanks[tank] = -1;
        recordExtractionProbe(tank, available);
        return extracted == null ? EmptyChemicalStackView.INSTANCE : extracted;
    }

    @Override
    public long insertChemical(ChemicalStackView stack, boolean simulate) {
        if (stack == null || stack.isEmpty()) return 0L;
        ChemicalInsertPlan plan = matchingInsertPlan(stack, simulate);
        if (plan == null) plan = buildInsertPlan(stack);
        if (simulate) {
            insertPlanAwaitingExecution = true;
            return plan.accepted;
        }
        insertPlanAwaitingExecution = false;
        long inserted = executeInsertPlan(plan, stack);
        insertPlan = null;
        return inserted;
    }

    public void clearAdaptiveState() {
        insertionRoutes.clear();
        extractionProbes.clear();
        insertPlan = null;
        insertPlanAwaitingExecution = false;
        Arrays.fill(visibleTanks, -1);
    }

    public void remapAdaptiveState(int[] oldIndexForNew) {
        if (oldIndexForNew == null) return;
        insertionRoutes.remapTargets(oldIndexForNew);
        extractionProbes.remapTargets(oldIndexForNew, lookup.gameTime());
        int[] oldVisible = visibleTanks.clone();
        Arrays.fill(visibleTanks, -1);
        for (int target = 0; target < oldIndexForNew.length && target < visibleTanks.length; target++) {
            int oldTarget = oldIndexForNew[target];
            if (oldTarget >= 0 && oldTarget < oldVisible.length) visibleTanks[target] = oldVisible[oldTarget];
        }
        insertPlan = null;
        insertPlanAwaitingExecution = false;
    }

    private ChemicalInsertPlan matchingInsertPlan(ChemicalStackView stack, boolean simulate) {
        if (insertPlan == null || insertPlan.tick != lookup.gameTime()
                || !insertPlan.key.equals(stack.chemicalKey())) return null;
        if (simulate) return insertPlan.requested == stack.getAmount() ? insertPlan : null;
        return insertPlanAwaitingExecution && stack.getAmount() <= insertPlan.accepted ? insertPlan : null;
    }

    private ChemicalInsertPlan buildInsertPlan(ChemicalStackView stack) {
        int targets = lookup.size();
        List<ChemicalMove> moves = new ArrayList<>();
        AdaptiveRoutingConfig config = config();
        String key = stack.chemicalKey();
        if (targets > 0) {
            int candidateCount;
            if (config.enabled()) {
                configureRoutes(config);
                candidateCount = insertionRoutes.orderCandidates(
                        key, targets, lookup.gameTime(), routeCandidates);
            } else {
                int start = Math.floorMod(legacyInsertCursor, targets);
                candidateCount = Math.min(targets, routeCandidates.length);
                for (int offset = 0; offset < candidateCount; offset++) {
                    routeCandidates[offset] = (start + offset) % targets;
                }
            }
            boolean sequential = lookup.sequentialInsertion();
            int successfulTargets = config.enabled() ? insertionRoutes.successfulTargetCount(key, targets) : targets;
            int shareTargets = successfulTargets > 0 ? successfulTargets : targets;
            long share = DistributorInsertMode.offer(stack.getAmount(), shareTargets, sequential);
            long planned = 0L;
            int successfulTargetRank = 0;
            boolean discoveryProbeCompleted = false;
            for (int offset = 0; offset < candidateCount; offset++) {
                if (!config.enabled() && planned >= stack.getAmount()) break;
                int target = routeCandidates[offset];
                boolean knownRoute = config.enabled() && insertionRoutes.isSuccessful(key, target, targets);
                long targetShare = !sequential && knownRoute && successfulTargets > 0
                        ? DistributorInsertMode.balancedOffer(
                                stack.getAmount(), successfulTargets, successfulTargetRank++)
                        : share;
                boolean discoveryOnly = config.enabled() && planned >= stack.getAmount();
                if (discoveryOnly && (knownRoute || discoveryProbeCompleted)) continue;
                if (!discoveryOnly && targetShare <= 0L) continue;
                if (!lookup.takeOperation()) break;
                if (!config.enabled()) legacyInsertCursor = target + 1;
                ChemicalHandlerBridge handler = lookup.handler(target);
                if (handler == null) {
                    if (config.enabled()) insertionRoutes.recordMiss(key, target, targets, lookup.gameTime());
                    continue;
                }
                long requested = discoveryOnly ? 1L
                        : Math.min(targetShare, stack.getAmount() - planned);
                long accepted = Math.max(0L,
                        handler.insertChemical(stack.copyWithAmount(requested), true));
                if (accepted > 0L) {
                    if (config.enabled()) insertionRoutes.recordSuccess(key, target, targets);
                    if (!discoveryOnly) {
                        moves.add(new ChemicalMove(target, Math.min(requested, accepted)));
                        planned += Math.min(requested, accepted);
                    }
                } else if (config.enabled()) {
                    insertionRoutes.recordMiss(key, target, targets, lookup.gameTime());
                }
                if (discoveryOnly) discoveryProbeCompleted = true;
            }
        }
        long accepted = Math.min(stack.getAmount(), plannedAmount(moves));
        insertPlan = new ChemicalInsertPlan(lookup.gameTime(), key, stack.getAmount(), List.copyOf(moves), accepted);
        return insertPlan;
    }

    private long executeInsertPlan(ChemicalInsertPlan plan, ChemicalStackView stack) {
        int targets = lookup.size();
        AdaptiveRoutingConfig config = config();
        long inserted = 0L;
        int lastSuccessfulTarget = -1;
        for (ChemicalMove move : plan.moves) {
            if (move.target >= targets || inserted >= stack.getAmount()) break;
            ChemicalHandlerBridge handler = lookup.handler(move.target);
            if (handler == null) continue;
            long requested = Math.min(move.amount, stack.getAmount() - inserted);
            long moved = Math.max(0L, handler.insertChemical(stack.copyWithAmount(requested), false));
            if (moved > 0L) {
                inserted += Math.min(requested, moved);
                lastSuccessfulTarget = move.target;
                visibleTanks[move.target] = -1;
                if (config.enabled()) insertionRoutes.recordSuccess(plan.key, move.target, targets);
            } else if (config.enabled()) {
                insertionRoutes.recordMiss(plan.key, move.target, targets, lookup.gameTime());
            }
        }
        if (config.enabled() && lastSuccessfulTarget >= 0) {
            insertionRoutes.advanceHotCursorAfter(plan.key, lastSuccessfulTarget, targets);
        }
        return Math.min(stack.getAmount(), inserted);
    }

    private void recordExtractionProbe(int target, boolean available) {
        if (!config().enabled() || lookup.budgetExhausted()) return;
        configureExtractionProbes();
        extractionProbes.recordProbe(lookup.size(), target, lookup.gameTime(), available);
    }

    private void configureExtractionProbes() {
        AdaptiveRoutingConfig config = config();
        extractionProbes.configure(config.hotTicks(), config.warmTicks(), config.coolTicks(),
                config.fallbackTicks(), config.missesPerDemotion());
    }

    private void configureRoutes(AdaptiveRoutingConfig config) {
        insertionRoutes.configure(config.routeCacheSize(), config.hotTicks(), config.warmTicks(),
                config.coolTicks(), config.fallbackTicks(), config.missesPerDemotion());
    }

    private AdaptiveRoutingConfig config() { return lookup.adaptiveRoutingConfig(); }

    private ChemicalHandlerBridge handler(int target) {
        return target < 0 || target >= lookup.size() || target >= MAX_TARGETS ? null : lookup.handler(target);
    }

    private static long plannedAmount(List<ChemicalMove> moves) {
        long amount = 0L;
        for (ChemicalMove move : moves) amount = Math.min(Long.MAX_VALUE, amount + move.amount);
        return amount;
    }

    private record ChemicalMove(int target, long amount) {}
    private record ChemicalInsertPlan(long tick, String key, long requested,
            List<ChemicalMove> moves, long accepted) {}
}
