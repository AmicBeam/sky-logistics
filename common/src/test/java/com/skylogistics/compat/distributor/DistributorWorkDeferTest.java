package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DistributorWorkDeferTest {
    @Test void indexingUsesConfiguredDelayWithoutAWatchdog() {
        assertEquals(20, DistributorWorkDefer.retryTicks(handler(true, false), 20));
        assertEquals(1, DistributorWorkDefer.retryTicks(handler(false, true), 20));
        assertEquals(0, DistributorWorkDefer.retryTicks(handler(false, false), 20));
    }

    @Test void ordinaryMachineHandlersAreNeverClassifiedAsDistributorIndexing() {
        assertEquals(0, DistributorWorkDefer.retryTicks(new Object(), 20));
    }

    private static BudgetedDistributorHandler handler(boolean indexing, boolean exhausted) {
        return new BudgetedDistributorHandler() {
            @Override public boolean distributorBudgetExhausted() { return exhausted; }
            @Override public boolean distributorScanPending() { return indexing; }
        };
    }
}
