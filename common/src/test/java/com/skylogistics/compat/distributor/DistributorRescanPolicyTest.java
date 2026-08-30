package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistributorRescanPolicyTest {
    @Test void dueCleanSideStillRunsItsSafetyRescan() {
        assertFalse(DistributorRescanPolicy.shouldScan(true, false, false, 99, 100));
        assertTrue(DistributorRescanPolicy.shouldScan(true, false, false, 100, 100));
    }

    @Test void dirtyAndPendingSidesRunImmediatelyButInactiveSidesDoNot() {
        assertTrue(DistributorRescanPolicy.shouldScan(true, true, false, 0, 100));
        assertTrue(DistributorRescanPolicy.shouldScan(true, false, true, 0, 100));
        assertFalse(DistributorRescanPolicy.shouldScan(false, true, true, 100, 0));
    }
}
