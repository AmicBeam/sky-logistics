package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistributorIndexPolicyTest {
    @Test void firstPartialDiscoveryCannotTransfer() {
        assertTrue(DistributorIndexPolicy.transferBlocked(false, true, true));
        assertTrue(DistributorIndexPolicy.transferBlocked(false, false, true));
    }

    @Test void rescanKeepsServingThePreviousCompleteSnapshot() {
        assertFalse(DistributorIndexPolicy.transferBlocked(true, false, false));
        assertFalse(DistributorIndexPolicy.transferBlocked(true, true, false));
        assertFalse(DistributorIndexPolicy.transferBlocked(true, false, true));
    }
}
