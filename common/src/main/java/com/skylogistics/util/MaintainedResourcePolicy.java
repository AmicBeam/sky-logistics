package com.skylogistics.util;

/** Pure arithmetic shared by maintained item and non-item transfer paths. */
public final class MaintainedResourcePolicy {
    private MaintainedResourcePolicy() {
    }

    public static boolean configured(long target) {
        return target > 0L;
    }

    public static long remainingAmount(long stored, long target) {
        return Math.max(0L, target - Math.max(0L, stored));
    }

    public static boolean pathUsesMaintainedBackoff(boolean enabled, long sourceMaintain,
            boolean anyTargetMaintained) {
        return enabled && (sourceMaintain > 0L || anyTargetMaintained);
    }

    public static long insertionAllowance(boolean byAmount, long requested, long stored, int occupiedUnits,
            long target, boolean fillMaintainedUnits, long existingUnitRefillCapacity) {
        requested = Math.max(0L, requested);
        if (!configured(target)) return requested;
        if (byAmount) return Math.min(requested, remainingAmount(stored, target));
        if (occupiedUnits < target) return requested;
        if (fillMaintainedUnits && occupiedUnits == target) {
            return Math.min(requested, Math.max(0L, existingUnitRefillCapacity));
        }
        return 0L;
    }

    public static long shortenedRetry(long normalRetryAfter, long gameTime, boolean enabled,
            boolean maintainedDemand, int pollTicks) {
        if (!enabled || !maintainedDemand) return normalRetryAfter;
        return Math.min(normalRetryAfter, gameTime + Math.max(1, pollTicks));
    }
}
