package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderedMatchingPolicyTest {
    @Test
    void mapsSlotsDirectlyWhileTargetsRemain() {
        assertEquals(0, OrderedMatchingPolicy.targetIndex(0, 3, true));
        assertEquals(2, OrderedMatchingPolicy.targetIndex(2, 3, false));
    }

    @Test
    void wrapsExcessSlotsByDefaultPolicy() {
        assertEquals(0, OrderedMatchingPolicy.targetIndex(3, 3, true));
        assertEquals(1, OrderedMatchingPolicy.targetIndex(7, 3, true));
    }

    @Test
    void samePriorityEndpointsStillOccupyDistinctTargetPositions() {
        int endpointCountIncludingPriorityTies = 3;
        assertEquals(0, OrderedMatchingPolicy.targetIndex(0, endpointCountIncludingPriorityTies, true));
        assertEquals(1, OrderedMatchingPolicy.targetIndex(1, endpointCountIncludingPriorityTies, true));
        assertEquals(2, OrderedMatchingPolicy.targetIndex(2, endpointCountIncludingPriorityTies, true));
    }

    @Test
    void rejectsExcessSlotsWhenWrappingIsDisabled() {
        assertEquals(-1, OrderedMatchingPolicy.targetIndex(3, 3, false));
        assertEquals(-1, OrderedMatchingPolicy.targetIndex(0, 0, true));
    }

    @Test
    void targetFailureContinuationIsConfigurableButSuccessAlwaysStops() {
        assertTrue(OrderedMatchingPolicy.stopAfterAttempt(true, true));
        assertTrue(OrderedMatchingPolicy.stopAfterAttempt(false, false));
        assertFalse(OrderedMatchingPolicy.stopAfterAttempt(false, true));
    }

    @Test
    void perItemBatchDistributesCompleteRoundsAndRemainder() {
        assertEquals(3, OrderedMatchingPolicy.batchTargetCount(10, 3));
        assertEquals(4, OrderedMatchingPolicy.batchAmount(10, 3, 0));
        assertEquals(3, OrderedMatchingPolicy.batchAmount(10, 3, 1));
        assertEquals(3, OrderedMatchingPolicy.batchAmount(10, 3, 2));
    }

    @Test
    void perItemModeMovesOnlyOneItemUntilBatchThresholdIsExceeded() {
        assertEquals(1, OrderedMatchingPolicy.batchTargetCount(2, 4));
        assertEquals(1, OrderedMatchingPolicy.batchAmount(2, 4, 0));
        assertEquals(0, OrderedMatchingPolicy.batchAmount(2, 4, 1));
        assertEquals(0, OrderedMatchingPolicy.batchAmount(2, 4, 2));
        assertEquals(1, OrderedMatchingPolicy.batchTargetCount(4, 4));
        assertEquals(4, OrderedMatchingPolicy.batchTargetCount(5, 4));
        assertEquals(2, OrderedMatchingPolicy.batchAmount(5, 4, 0));
        assertEquals(1, OrderedMatchingPolicy.batchAmount(5, 4, 1));
    }

    @Test
    void perItemCursorWrapsAcrossTargetList() {
        assertEquals(2, OrderedMatchingPolicy.normalizeCursor(5, 3));
        assertEquals(0, OrderedMatchingPolicy.advanceCursor(2, 3));
        assertEquals(0, OrderedMatchingPolicy.normalizeCursor(5, 0));
    }

    @Test
    void batchConsumesOneBudgetUnitForEveryTargetItCanVisit() {
        assertEquals(4, OrderedMatchingPolicy.budgetedBatchTargetCount(64, 4, 4, 8));
        assertEquals(2, OrderedMatchingPolicy.budgetedBatchTargetCount(64, 4, 2, 8));
        assertEquals(1, OrderedMatchingPolicy.budgetedBatchTargetCount(64, 4, 8, 1));
        assertEquals(0, OrderedMatchingPolicy.budgetedBatchTargetCount(64, 4, 0, 8));
    }
}
