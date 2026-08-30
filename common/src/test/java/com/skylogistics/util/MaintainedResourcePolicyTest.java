package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaintainedResourcePolicyTest {
    @Test
    void amountModeUsesNativeStoredAmount() {
        assertTrue(MaintainedResourcePolicy.wantsMore(true, 999, 4, 1000, true, true, true));
        assertFalse(MaintainedResourcePolicy.wantsMore(true, 999, 4, 1000, true, false, false));
        assertFalse(MaintainedResourcePolicy.wantsMore(true, 1000, 0, 1000, true, true, true));
    }

    @Test
    void slotModeCountsStorageUnitsAndCanRefillExistingUnits() {
        assertTrue(MaintainedResourcePolicy.wantsMore(false, 1000, 1, 2, false, true, false));
        assertTrue(MaintainedResourcePolicy.wantsMore(false, 1000, 2, 2, true, true, true));
        assertFalse(MaintainedResourcePolicy.wantsMore(false, 1000, 2, 2, true, true, false));
        assertEquals(25, MaintainedResourcePolicy.insertionAllowance(true, 100, 975, 1, 1000, true, 0));
        assertEquals(30, MaintainedResourcePolicy.insertionAllowance(false, 100, 1000, 2, 2, true, 30));
        assertEquals(0, MaintainedResourcePolicy.insertionAllowance(false, 100, 1000, 2, 2, false, 30));
    }

    @Test
    void maintainedDemandShortensOnlyItsOwnRetry() {
        assertEquals(105, MaintainedResourcePolicy.shortenedRetry(140, 100, true, true, 5));
        assertEquals(140, MaintainedResourcePolicy.shortenedRetry(140, 100, false, true, 5));
        assertEquals(140, MaintainedResourcePolicy.shortenedRetry(140, 100, true, false, 5));
    }
}
