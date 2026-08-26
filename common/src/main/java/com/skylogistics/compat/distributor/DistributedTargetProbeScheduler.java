package com.skylogistics.compat.distributor;

import java.util.Arrays;

/** Maintains independent adaptive probe tiers and local hot slots for distributor target machines. */
public final class DistributedTargetProbeScheduler<T> {
    private static final int HOT = 0;
    private static final int FALLBACK = 3;

    private final int[] intervals = {1, 5, 20, 40};
    private int missesPerDemotion = 3;
    private DistributedSlotMap<T> slotMap;
    private long[] initialProbeTicks = new long[0];
    private long[] lastProbeTicks = new long[0];
    private long[] lastAvailableProbeTicks = new long[0];
    private byte[] tiers = new byte[0];
    private int[] tierMisses = new int[0];
    private int[] cycleChecks = new int[0];
    private int[] nextLocalSlots = new int[0];
    private int targetCursor;

    public void configure(int hotTicks, int warmTicks, int coolTicks, int fallbackTicks,
            int configuredMissesPerDemotion) {
        intervals[HOT] = Math.max(1, hotTicks);
        intervals[1] = Math.max(intervals[HOT], warmTicks);
        intervals[2] = Math.max(intervals[1], coolTicks);
        intervals[FALLBACK] = Math.max(intervals[2], fallbackTicks);
        missesPerDemotion = Math.max(1, configuredMissesPerDemotion);
    }

    public int dueProbeCount(DistributedSlotMap<T> current, long gameTime) {
        refreshMap(current, gameTime);
        int due = 0;
        for (int target = 0; target < current.targetCount(); target++) {
            if (isDue(target, gameTime)) due++;
        }
        return due;
    }

    public int nextDueSlot(DistributedSlotMap<T> current, long gameTime) {
        refreshMap(current, gameTime);
        int targets = current.targetCount();
        for (int offset = 0; offset < targets; offset++) {
            int target = Math.floorMod(targetCursor + offset, targets);
            if (isDue(target, gameTime)) return current.firstSlot(target) + nextLocalSlots[target];
        }
        return -1;
    }

    public void recordProbe(DistributedSlotMap<T> current, int virtualSlot, long gameTime, boolean available) {
        refreshMap(current, gameTime);
        int target = current.targetIndex(virtualSlot);
        if (target < 0) return;
        int localSlot = virtualSlot - current.firstSlot(target);
        targetCursor = (target + 1) % current.targetCount();
        if (available) {
            lastAvailableProbeTicks[target] = gameTime;
            lastProbeTicks[target] = gameTime;
            tiers[target] = HOT;
            tierMisses[target] = 0;
            cycleChecks[target] = 0;
            nextLocalSlots[target] = localSlot;
        } else if (lastAvailableProbeTicks[target] != gameTime) {
            nextLocalSlots[target] = (localSlot + 1) % current.slotCount(target);
            if (++cycleChecks[target] >= current.slotCount(target)) {
                lastProbeTicks[target] = gameTime;
                cycleChecks[target] = 0;
                if (++tierMisses[target] >= missesPerDemotion) {
                    tiers[target] = (byte)Math.min(FALLBACK, tiers[target] + 1);
                    tierMisses[target] = 0;
                }
            }
        }
    }

    private boolean isDue(int target, long gameTime) {
        long lastProbe = lastProbeTicks[target];
        return lastProbe == Long.MIN_VALUE
                ? gameTime >= initialProbeTicks[target]
                : gameTime - lastProbe >= intervals[tiers[target]];
    }

    private void refreshMap(DistributedSlotMap<T> current, long gameTime) {
        if (current.equals(slotMap)) return;
        slotMap = current;
        int targets = current.targetCount();
        initialProbeTicks = new long[targets];
        lastProbeTicks = new long[targets];
        Arrays.fill(lastProbeTicks, Long.MIN_VALUE);
        lastAvailableProbeTicks = new long[targets];
        Arrays.fill(lastAvailableProbeTicks, Long.MIN_VALUE);
        tiers = new byte[targets];
        Arrays.fill(tiers, (byte)FALLBACK);
        tierMisses = new int[targets];
        cycleChecks = new int[targets];
        nextLocalSlots = new int[targets];
        for (int target = 0; target < targets; target++) {
            initialProbeTicks[target] = gameTime + (long)target * intervals[FALLBACK] / Math.max(1, targets);
        }
        targetCursor = 0;
    }
}
