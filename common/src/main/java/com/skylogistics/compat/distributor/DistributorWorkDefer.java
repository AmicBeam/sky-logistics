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
}
