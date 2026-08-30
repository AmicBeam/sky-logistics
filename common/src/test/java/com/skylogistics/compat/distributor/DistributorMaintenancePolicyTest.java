package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistributorMaintenancePolicyTest {
    @Test void itemMaintenanceReturnsOnlyTheDeficit() {
        assertEquals(6, DistributorMaintenancePolicy.remainingItems(10, 16));
        assertEquals(0, DistributorMaintenancePolicy.remainingItems(16, 16));
        assertEquals(0, DistributorMaintenancePolicy.remainingItems(20, 16));
    }

    @Test void slotMaintenanceLimitsNewSlotsButCanFillExistingAtTheTarget() {
        assertEquals(2, DistributorMaintenancePolicy.remainingSlots(1, 3));
        assertEquals(0, DistributorMaintenancePolicy.remainingSlots(3, 3));
        assertFalse(DistributorMaintenancePolicy.blocksSlotInsertion(3, 3, true));
        assertTrue(DistributorMaintenancePolicy.blocksSlotInsertion(3, 3, false));
        assertTrue(DistributorMaintenancePolicy.blocksSlotInsertion(4, 3, true));
    }
}
