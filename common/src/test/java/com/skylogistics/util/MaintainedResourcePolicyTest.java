package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MaintainedResourcePolicyTest {
    @Test
    void amountModeUsesNativeStoredAmount() {
        assertEquals(1, MaintainedResourcePolicy.remainingAmount(999, 1000));
        assertEquals(0, MaintainedResourcePolicy.remainingAmount(1000, 1000));
    }

    @Test
    void slotModeCountsStorageUnitsAndCanRefillExistingUnits() {
        assertEquals(25, MaintainedResourcePolicy.insertionAllowance(true, 100, 975, 1, 1000, true, 0));
        assertEquals(30, MaintainedResourcePolicy.insertionAllowance(false, 100, 1000, 2, 2, true, 30));
        assertEquals(0, MaintainedResourcePolicy.insertionAllowance(false, 100, 1000, 2, 2, false, 30));
    }

    @Test
    void maintainedPathCapsTheOriginalRetry() {
        assertEquals(105, MaintainedResourcePolicy.shortenedRetry(140, 100, true, true, 5));
        assertEquals(140, MaintainedResourcePolicy.shortenedRetry(140, 100, false, true, 5));
        assertEquals(140, MaintainedResourcePolicy.shortenedRetry(140, 100, true, false, 5));
    }

    @Test
    void sourceOrTargetMaintainActivatesTheSameBackoffRule() {
        assertEquals(true, MaintainedResourcePolicy.pathUsesMaintainedBackoff(true, 1, false));
        assertEquals(true, MaintainedResourcePolicy.pathUsesMaintainedBackoff(true, 0, true));
        assertEquals(false, MaintainedResourcePolicy.pathUsesMaintainedBackoff(true, 0, false));
        assertEquals(false, MaintainedResourcePolicy.pathUsesMaintainedBackoff(false, 1, true));
    }
}
