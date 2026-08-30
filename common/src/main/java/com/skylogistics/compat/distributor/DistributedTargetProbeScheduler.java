package com.skylogistics.compat.distributor;

import java.util.Arrays;

/** Maintains independent adaptive probe tiers and local hot slots for distributor target machines. */
public final class DistributedTargetProbeScheduler<T> {
    private final AdaptiveProbeBackoff backoff = new AdaptiveProbeBackoff();
    private DistributedSlotMap<T> slotMap;
    private long[] initialProbeTicks = new long[0];
    private long[] lastProbeTicks = new long[0];
    private long[] lastAvailableProbeTicks = new long[0];
    private byte[] tiers = new byte[0];
    private int[] tierMisses = new int[0];
    private int[] cycleChecks = new int[0];
    private int[] nextLocalSlots = new int[0];
    private int[] successfulLocalSlots = new int[0];
    private long[] lastMaintenanceProbeTicks = new long[0];
    private int targetCursor;
    private int maximumInterval;
    private long maximumIntervalStartedAt = Long.MIN_VALUE;

    public void setMaximumInterval(int ticks) {
        setMaximumInterval(ticks, Long.MIN_VALUE);
    }

    public void setMaximumInterval(int ticks, long gameTime) {
        int normalized = Math.max(0, ticks);
        if (normalized > 0 && (maximumInterval <= 0 || maximumInterval != normalized)) {
            maximumIntervalStartedAt = gameTime;
        } else if (normalized <= 0) {
            maximumIntervalStartedAt = Long.MIN_VALUE;
        }
        maximumInterval = normalized;
    }

    public void configure(int hotTicks, int warmTicks, int coolTicks, int fallbackTicks,
            int configuredMissesPerDemotion) {
        backoff.configure(hotTicks, warmTicks, coolTicks, fallbackTicks, configuredMissesPerDemotion);
    }

    public int dueProbeCount(DistributedSlotMap<T> current, long gameTime) {
        refreshMap(current, gameTime);
        int due = 0;
        for (int target = 0; target < current.targetCount(); target++) {
            if (isMaintenanceDue(target, gameTime)) due++;
            if (isAdaptiveDue(target, gameTime)) due++;
        }
        return due;
    }

    public int nextDueSlot(DistributedSlotMap<T> current, long gameTime) {
        refreshMap(current, gameTime);
        int targets = current.targetCount();
        for (int offset = 0; offset < targets; offset++) {
            int target = Math.floorMod(targetCursor + offset, targets);
            if (isMaintenanceDue(target, gameTime)) {
                return current.firstSlot(target) + successfulLocalSlots[target];
            }
        }
        for (int offset = 0; offset < targets; offset++) {
            int target = Math.floorMod(targetCursor + offset, targets);
            if (isAdaptiveDue(target, gameTime)) return current.firstSlot(target) + nextLocalSlots[target];
        }
        return -1;
    }

    /**
     * Records a non-definitive inventory observation or simulated extraction. A simulated item is
     * only a candidate: some handlers expose input contents during simulation but reject the real
     * extraction, and a source filter or receiving endpoint may reject it before execution.
     * Advance provisionally so the machine cannot pin the scan to that slot; a successful
     * execution promotes the slot back to hot through {@link #recordProbe}.
     */
    public void recordSimulatedProbe(DistributedSlotMap<T> current, int virtualSlot, long gameTime,
            boolean available) {
        if (!available) {
            recordProbe(current, virtualSlot, gameTime, false);
            return;
        }
        refreshMap(current, gameTime);
        int target = current.targetIndex(virtualSlot);
        if (target < 0) return;
        int localSlot = virtualSlot - current.firstSlot(target);
        targetCursor = (target + 1) % current.targetCount();
        nextLocalSlots[target] = (localSlot + 1) % current.slotCount(target);
    }

    public void recordProbe(DistributedSlotMap<T> current, int virtualSlot, long gameTime, boolean available) {
        refreshMap(current, gameTime);
        int target = current.targetIndex(virtualSlot);
        if (target < 0) return;
        int localSlot = virtualSlot - current.firstSlot(target);
        targetCursor = (target + 1) % current.targetCount();
        if (isMaintenanceDue(target, gameTime) && successfulLocalSlots[target] == localSlot) {
            lastMaintenanceProbeTicks[target] = gameTime;
            if (!available) return;
        }
        if (available) {
            lastAvailableProbeTicks[target] = gameTime;
            lastMaintenanceProbeTicks[target] = gameTime;
            lastProbeTicks[target] = gameTime;
            tiers[target] = AdaptiveProbeBackoff.HOT;
            tierMisses[target] = 0;
            cycleChecks[target] = 0;
            nextLocalSlots[target] = localSlot;
            successfulLocalSlots[target] = localSlot;
        } else if (lastAvailableProbeTicks[target] != gameTime) {
            nextLocalSlots[target] = (localSlot + 1) % current.slotCount(target);
            if (++cycleChecks[target] >= current.slotCount(target)) {
                lastProbeTicks[target] = gameTime;
                cycleChecks[target] = 0;
                if (backoff.shouldDemote(++tierMisses[target])) {
                    tiers[target] = backoff.demote(tiers[target]);
                    tierMisses[target] = 0;
                }
            }
        }
    }

    public void remapTargets(DistributedSlotMap<T> current, long gameTime) {
        remapTargets(current, null, gameTime);
    }

    public void remapTargets(DistributedSlotMap<T> current, int[] oldIndexForNew, long gameTime) {
        if (current == null || current.equals(slotMap)) return;
        if (slotMap == null) {
            refreshMap(current, gameTime);
            return;
        }
        DistributedSlotMap<T> oldMap = slotMap;
        long[] oldInitial = initialProbeTicks;
        long[] oldLast = lastProbeTicks;
        long[] oldAvailable = lastAvailableProbeTicks;
        byte[] oldTiers = tiers;
        int[] oldMisses = tierMisses;
        int[] oldCycleChecks = cycleChecks;
        int[] oldLocalSlots = nextLocalSlots;
        int[] oldSuccessfulSlots = successfulLocalSlots;
        long[] oldMaintenanceTicks = lastMaintenanceProbeTicks;
        int oldCursor = targetCursor;
        slotMap = current;
        int targets = current.targetCount();
        initialProbeTicks = new long[targets];
        lastProbeTicks = new long[targets];
        Arrays.fill(lastProbeTicks, Long.MIN_VALUE);
        lastAvailableProbeTicks = new long[targets];
        Arrays.fill(lastAvailableProbeTicks, Long.MIN_VALUE);
        tiers = new byte[targets];
        Arrays.fill(tiers, AdaptiveProbeBackoff.FALLBACK);
        tierMisses = new int[targets];
        cycleChecks = new int[targets];
        nextLocalSlots = new int[targets];
        successfulLocalSlots = new int[targets];
        Arrays.fill(successfulLocalSlots, -1);
        lastMaintenanceProbeTicks = new long[targets];
        Arrays.fill(lastMaintenanceProbeTicks, Long.MIN_VALUE);
        targetCursor = 0;
        for (int target = 0; target < targets; target++) {
            int oldTarget = oldIndexForNew != null && target < oldIndexForNew.length
                    ? oldIndexForNew[target] : findTarget(oldMap, current.target(target));
            if (oldTarget >= 0 && oldTarget < oldLast.length) {
                initialProbeTicks[target] = oldInitial[oldTarget];
                lastProbeTicks[target] = oldLast[oldTarget];
                lastAvailableProbeTicks[target] = oldAvailable[oldTarget];
                tiers[target] = oldTiers[oldTarget];
                tierMisses[target] = oldMisses[oldTarget];
                cycleChecks[target] = Math.min(oldCycleChecks[oldTarget], current.slotCount(target) - 1);
                nextLocalSlots[target] = Math.floorMod(oldLocalSlots[oldTarget], current.slotCount(target));
                successfulLocalSlots[target] = oldSuccessfulSlots[oldTarget] < 0 ? -1
                        : Math.floorMod(oldSuccessfulSlots[oldTarget], current.slotCount(target));
                lastMaintenanceProbeTicks[target] = oldMaintenanceTicks[oldTarget];
                if (oldTarget == oldCursor) targetCursor = target;
            } else {
                initialProbeTicks[target] = gameTime
                        + (long)target * backoff.interval(AdaptiveProbeBackoff.FALLBACK) / Math.max(1, targets);
            }
        }
    }

    private static <T> int findTarget(DistributedSlotMap<T> map, T target) {
        for (int index = 0; index < map.targetCount(); index++) {
            if (java.util.Objects.equals(map.target(index), target)) return index;
        }
        return -1;
    }

    private boolean isMaintenanceDue(int target, long gameTime) {
        return maximumInterval > 0 && successfulLocalSlots[target] >= 0
                && gameTime - lastMaintenanceProbeTicks[target] >= maximumInterval;
    }

    private boolean isAdaptiveDue(int target, long gameTime) {
        long lastProbe = lastProbeTicks[target];
        if (lastProbe != Long.MIN_VALUE) return gameTime - lastProbe >= interval(tiers[target]);
        long due = initialProbeTicks[target];
        if (maximumInterval > 0 && maximumIntervalStartedAt != Long.MIN_VALUE) {
            due = Math.min(due, maximumIntervalStartedAt
                    + (long)target * maximumInterval / Math.max(1, lastProbeTicks.length));
        }
        return gameTime >= due;
    }

    private int interval(byte tier) {
        int interval = backoff.interval(tier);
        return maximumInterval > 0 ? Math.min(interval, maximumInterval) : interval;
    }

    private void refreshMap(DistributedSlotMap<T> current, long gameTime) {
        if (current.equals(slotMap)) return;
        if (slotMap != null) {
            remapTargets(current, gameTime);
            return;
        }
        slotMap = current;
        int targets = current.targetCount();
        initialProbeTicks = new long[targets];
        lastProbeTicks = new long[targets];
        Arrays.fill(lastProbeTicks, Long.MIN_VALUE);
        lastAvailableProbeTicks = new long[targets];
        Arrays.fill(lastAvailableProbeTicks, Long.MIN_VALUE);
        tiers = new byte[targets];
        Arrays.fill(tiers, AdaptiveProbeBackoff.FALLBACK);
        tierMisses = new int[targets];
        cycleChecks = new int[targets];
        nextLocalSlots = new int[targets];
        successfulLocalSlots = new int[targets];
        Arrays.fill(successfulLocalSlots, -1);
        lastMaintenanceProbeTicks = new long[targets];
        Arrays.fill(lastMaintenanceProbeTicks, Long.MIN_VALUE);
        for (int target = 0; target < targets; target++) {
            initialProbeTicks[target] = gameTime
                    + (long)target * backoff.interval(AdaptiveProbeBackoff.FALLBACK) / Math.max(1, targets);
        }
        targetCursor = 0;
    }
}
