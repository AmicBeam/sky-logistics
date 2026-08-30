package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaintainedSlotPolicyTest {
    @Test
    void slotMaintenanceKeepsInsertingAtTargetOnlyWhenFillIsEnabled() {
        assertTrue(MaintainedSlotPolicy.shouldInsert(false, 2, 2, true));
        assertFalse(MaintainedSlotPolicy.shouldInsert(false, 2, 2, false));
        assertFalse(MaintainedSlotPolicy.shouldInsert(true, 2, 2, true));
    }

    @Test
    void shortagesAlwaysInsertAndExcessNeverInserts() {
        assertTrue(MaintainedSlotPolicy.shouldInsert(false, 1, 2, false));
        assertTrue(MaintainedSlotPolicy.shouldInsert(true, 1, 2, false));
        assertFalse(MaintainedSlotPolicy.shouldInsert(false, 3, 2, true));
    }

    @Test
    void fillModeRefillsExistingSlotsWithoutOpeningAnotherSlot() {
        assertTrue(MaintainedSlotPolicy.tracksRefillCandidate(true));
        assertFalse(MaintainedSlotPolicy.blocksInsertionAtSlotLimit(true, 2, 2, false));
        assertTrue(MaintainedSlotPolicy.blocksInsertionAtSlotLimit(true, 2, 2, true));
    }

    @Test
    void disabledFillModeBlocksAllInsertionAtTheSlotLimit() {
        assertFalse(MaintainedSlotPolicy.tracksRefillCandidate(false));
        assertTrue(MaintainedSlotPolicy.blocksInsertionAtSlotLimit(false, 2, 2, false));
    }
}
