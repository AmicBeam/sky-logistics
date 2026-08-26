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

    public static int offsetTargetIndex(int sourceSlot, int targetCount, int offset, boolean wrapTargets) {
        if (offset >= 0) {
            int availableTargets = targetCount - offset;
            int relative = targetIndex(sourceSlot, availableTargets, wrapTargets);
            return relative < 0 ? -1 : offset + relative;
        }
        long relativeSourceSlot = (long) sourceSlot + offset;
        return relativeSourceSlot < 0 || relativeSourceSlot > Integer.MAX_VALUE
                ? -1 : targetIndex((int) relativeSourceSlot, targetCount, wrapTargets);
    }

    public static int offsetPosition(int priorityIndex, int offset, int positionCount) {
        long position = (long) priorityIndex - offset;
        return position >= 0 && position < positionCount ? (int) position : -1;
    }

    public static boolean stopAfterAttempt(boolean moved, boolean continueAfterTargetFailure) {
        return moved || !continueAfterTargetFailure;
    }

    public static int normalizeCursor(int cursor, int targetCount) {
        return targetCount <= 0 ? 0 : Math.floorMod(cursor, targetCount);
    }

    public static int advanceCursor(int cursor, int targetCount) {
        return targetCount <= 0 ? 0 : Math.floorMod(cursor + 1, targetCount);
    }

    public static int batchTargetCount(int itemCount, int targetCount) {
        if (itemCount <= 0 || targetCount <= 0) return 0;
        return itemCount > targetCount ? targetCount : 1;
    }

    public static int batchAmount(int itemCount, int targetCount, int targetOffset) {
        if (itemCount <= 0 || targetCount <= 0 || targetOffset < 0 || targetOffset >= targetCount) return 0;
        if (itemCount <= targetCount) return targetOffset == 0 ? 1 : 0;
        int base = itemCount / targetCount;
        int remainder = itemCount % targetCount;
        return base + (targetOffset < remainder ? 1 : 0);
    }

    public static int budgetedBatchTargetCount(int itemCount, int targetCount, int targetBudget,
            int targetAttemptLimit) {
        return Math.min(batchTargetCount(itemCount, targetCount),
                Math.min(Math.max(0, targetBudget), Math.max(0, targetAttemptLimit)));
    }

    public static int availableAfterDetention(int sourceCount, int reservedCount) {
        return Math.max(0, sourceCount - Math.max(0, reservedCount));
    }

    public static boolean canEnqueueDetention(int queueSize, int capacity) {
        return capacity > 0 && queueSize >= 0 && queueSize < capacity;
    }

    public static boolean shouldResetPerItemCursor(boolean hasTransferableResource, int detentionCount) {
        return !hasTransferableResource && detentionCount <= 0;
    }
}
