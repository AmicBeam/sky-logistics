package com.skylogistics.compat.distributor;

import java.util.Arrays;

/** Adaptive per-target extraction probes for resources exposing one virtual entry per machine. */
public final class AdaptiveTargetProbeScheduler {
    private final AdaptiveProbeBackoff backoff = new AdaptiveProbeBackoff();
    private long[] initialProbeTicks = new long[0];
    private long[] lastProbeTicks = new long[0];
    private byte[] tiers = new byte[0];
    private int[] tierMisses = new int[0];
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

    public int dueProbeCount(int targetCount, long gameTime) {
        refresh(targetCount, gameTime);
        int due = 0;
        for (int target = 0; target < targetCount; target++) {
            if (isDue(target, gameTime)) due++;
        }
        return due;
    }

    public int nextDueTarget(int targetCount, long gameTime) {
        refresh(targetCount, gameTime);
        for (int offset = 0; offset < targetCount; offset++) {
            int target = Math.floorMod(targetCursor + offset, targetCount);
            if (isDue(target, gameTime)) return target;
        }
        return -1;
    }

    public void recordProbe(int targetCount, int target, long gameTime, boolean available) {
        refresh(targetCount, gameTime);
        if (target < 0 || target >= targetCount) return;
        targetCursor = (target + 1) % targetCount;
        lastProbeTicks[target] = gameTime;
        if (available) {
            tiers[target] = AdaptiveProbeBackoff.HOT;
            tierMisses[target] = 0;
        } else if (backoff.shouldDemote(++tierMisses[target])) {
            tiers[target] = backoff.demote(tiers[target]);
            tierMisses[target] = 0;
        }
    }

    public void clear() {
        initialProbeTicks = new long[0];
        lastProbeTicks = new long[0];
        tiers = new byte[0];
        tierMisses = new int[0];
        targetCursor = 0;
    }

    public void remapTargets(int[] oldIndexForNew, long gameTime) {
        if (oldIndexForNew == null) return;
        long[] oldInitial = initialProbeTicks;
        long[] oldLast = lastProbeTicks;
        byte[] oldTiers = tiers;
        int[] oldMisses = tierMisses;
        int oldCount = oldLast.length;
        initialProbeTicks = new long[oldIndexForNew.length];
        lastProbeTicks = new long[oldIndexForNew.length];
        Arrays.fill(lastProbeTicks, Long.MIN_VALUE);
        tiers = new byte[oldIndexForNew.length];
        Arrays.fill(tiers, AdaptiveProbeBackoff.FALLBACK);
        tierMisses = new int[oldIndexForNew.length];
        for (int target = 0; target < oldIndexForNew.length; target++) {
            int oldTarget = oldIndexForNew[target];
            if (oldTarget >= 0 && oldTarget < oldCount) {
                initialProbeTicks[target] = oldInitial[oldTarget];
                lastProbeTicks[target] = oldLast[oldTarget];
                tiers[target] = oldTiers[oldTarget];
                tierMisses[target] = oldMisses[oldTarget];
            } else {
                initialProbeTicks[target] = gameTime
                        + (long)target * backoff.interval(AdaptiveProbeBackoff.FALLBACK)
                                / Math.max(1, oldIndexForNew.length);
            }
        }
        targetCursor = remappedIndex(targetCursor, oldIndexForNew);
    }

    private static int remappedIndex(int oldIndex, int[] oldIndexForNew) {
        for (int target = 0; target < oldIndexForNew.length; target++) {
            if (oldIndexForNew[target] == oldIndex) return target;
        }
        return 0;
    }

    private boolean isDue(int target, long gameTime) {
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

    private void refresh(int targetCount, long gameTime) {
        targetCount = Math.max(0, targetCount);
        if (lastProbeTicks.length == targetCount) return;
        initialProbeTicks = new long[targetCount];
        lastProbeTicks = new long[targetCount];
        Arrays.fill(lastProbeTicks, Long.MIN_VALUE);
        tiers = new byte[targetCount];
        Arrays.fill(tiers, AdaptiveProbeBackoff.FALLBACK);
        tierMisses = new int[targetCount];
        for (int target = 0; target < targetCount; target++) {
            initialProbeTicks[target] = gameTime
                    + (long)target * backoff.interval(AdaptiveProbeBackoff.FALLBACK) / Math.max(1, targetCount);
        }
        targetCursor = 0;
    }
}
