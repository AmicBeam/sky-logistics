package com.skylogistics.compat.distributor;

/** Chooses a local retry delay for transient distributor work without recording a transfer failure. */
public final class DistributorWorkDefer {
    private DistributorWorkDefer() {
    }

    public static int retryTicks(Object handler, int indexingRetryTicks) {
        if (!(handler instanceof BudgetedDistributorHandler distributor)) return 0;
        if (distributor.distributorScanPending()) return Math.max(1, indexingRetryTicks);
        return distributor.distributorBudgetExhausted() ? 1 : 0;
    }

    /**
     * A background rescan only blocks a resource when there is no previous index that can still
     * serve requests. Callers must continue validating every cached target while using it.
     */
    public static boolean indexUnavailable(boolean scanPending, int cachedEntries) {
        return scanPending && cachedEntries <= 0;
    }

    /** Independent distributor probes already advance away from a failed virtual source slot. */
    public static boolean shouldBackoffItemExtractionFailure(Object handler) {
        return !(handler instanceof BudgetedDistributorHandler distributor)
                || !distributor.usesIndependentExtractionProbes();
    }
}
