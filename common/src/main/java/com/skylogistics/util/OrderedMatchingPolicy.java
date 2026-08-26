package com.skylogistics.util;

public final class OrderedMatchingPolicy {
    private OrderedMatchingPolicy() {
    }

    public static int targetIndex(int sourceSlot, int targetCount, boolean wrapTargets) {
        // targetCount is the number of sorted endpoint entries, not the number of distinct priorities.
        if (sourceSlot < 0 || targetCount <= 0) return -1;
        if (sourceSlot < targetCount) return sourceSlot;
        return wrapTargets ? Math.floorMod(sourceSlot, targetCount) : -1;
    }

    public static boolean stopAfterAttempt(boolean moved, boolean continueAfterTargetFailure) {
        return moved || !continueAfterTargetFailure;
    }
}
