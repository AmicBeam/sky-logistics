package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test void backgroundIndexingKeepsAUsablePreviousIndexOnline() {
        assertFalse(DistributorWorkDefer.indexUnavailable(true, 1));
        assertTrue(DistributorWorkDefer.indexUnavailable(true, 0));
        assertFalse(DistributorWorkDefer.indexUnavailable(false, 0));
    }

    @Test void independentItemProbeMissDoesNotBackoffTheWholeEndpoint() {
        assertFalse(DistributorWorkDefer.shouldBackoffItemExtractionFailure(independentProbeHandler()));
        assertTrue(DistributorWorkDefer.shouldBackoffItemExtractionFailure(handler(false, false)));
        assertTrue(DistributorWorkDefer.shouldBackoffItemExtractionFailure(new Object()));
    }

    private static BudgetedDistributorHandler handler(boolean indexing, boolean exhausted) {
        return new BudgetedDistributorHandler() {
            @Override public boolean distributorBudgetExhausted() { return exhausted; }
            @Override public boolean distributorScanPending() { return indexing; }
        };
    }

    private static BudgetedDistributorHandler independentProbeHandler() {
        return new BudgetedDistributorHandler() {
            @Override public boolean distributorBudgetExhausted() { return false; }
            @Override public boolean usesIndependentExtractionProbes() { return true; }
        };
    }
}
