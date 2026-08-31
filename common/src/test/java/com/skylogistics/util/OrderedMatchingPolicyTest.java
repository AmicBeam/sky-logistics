package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void offsetSkipsHighestPriorityTargets() {
        assertEquals(2, OrderedMatchingPolicy.offsetTargetIndex(0, 5, 2, true));
        assertEquals(4, OrderedMatchingPolicy.offsetTargetIndex(2, 5, 2, true));
        assertEquals(2, OrderedMatchingPolicy.offsetTargetIndex(3, 5, 2, true));
        assertEquals(-1, OrderedMatchingPolicy.offsetTargetIndex(0, 2, 2, true));
    }

    @Test
    void offsetMapsSourcePriorityToReceivingSlot() {
        assertEquals(-1, OrderedMatchingPolicy.offsetPosition(0, 1, 4));
        assertEquals(0, OrderedMatchingPolicy.offsetPosition(1, 1, 4));
        assertEquals(3, OrderedMatchingPolicy.offsetPosition(4, 1, 4));
        assertEquals(-1, OrderedMatchingPolicy.offsetPosition(5, 1, 4));
    }

    @Test
    void negativeOffsetSkipsLeadingSourceSlots() {
        assertEquals(-1, OrderedMatchingPolicy.offsetTargetIndex(0, 5, -2, true));
        assertEquals(-1, OrderedMatchingPolicy.offsetTargetIndex(1, 5, -2, true));
        assertEquals(0, OrderedMatchingPolicy.offsetTargetIndex(2, 5, -2, true));
        assertEquals(1, OrderedMatchingPolicy.offsetTargetIndex(3, 5, -2, true));
        assertEquals(0, OrderedMatchingPolicy.offsetTargetIndex(7, 5, -2, true));
    }

    @Test
    void negativeFourContinuesPastFourSkippedSourceSlots() {
        assertTrue(OrderedMatchingPolicy.isSourceSlotSkippedByOffset(0, -4));
        assertTrue(OrderedMatchingPolicy.isSourceSlotSkippedByOffset(3, -4));
        assertFalse(OrderedMatchingPolicy.isSourceSlotSkippedByOffset(4, -4));
        assertFalse(OrderedMatchingPolicy.isSourceSlotSkippedByOffset(0, 4));
        assertEquals(0, OrderedMatchingPolicy.offsetTargetIndex(4, 5, -4, true));
    }

    @Test
    void negativeOffsetSkipsLeadingReceivingSlots() {
        assertEquals(2, OrderedMatchingPolicy.offsetPosition(0, -2, 5));
        assertEquals(3, OrderedMatchingPolicy.offsetPosition(1, -2, 5));
        assertEquals(-1, OrderedMatchingPolicy.offsetPosition(3, -2, 5));
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

    @Test
    void detainedItemsAreExcludedFromFreshDispatch() {
        assertEquals(9, OrderedMatchingPolicy.availableAfterDetention(10, 1));
        assertEquals(0, OrderedMatchingPolicy.availableAfterDetention(1, 1));
        assertEquals(0, OrderedMatchingPolicy.availableAfterDetention(1, 3));
    }

    @Test
    void detentionQueueAcceptsReplacementAtCapacity() {
        assertTrue(OrderedMatchingPolicy.canEnqueueDetention(0, 1));
        assertTrue(OrderedMatchingPolicy.canEnqueueDetention(1, 1));
        assertTrue(OrderedMatchingPolicy.canEnqueueDetention(4, 4));
        assertFalse(OrderedMatchingPolicy.canEnqueueDetention(0, 0));
        assertEquals(0, OrderedMatchingPolicy.detentionEvictionsForEnqueue(3, 4));
        assertEquals(1, OrderedMatchingPolicy.detentionEvictionsForEnqueue(4, 4));
        assertEquals(2, OrderedMatchingPolicy.detentionEvictionsForEnqueue(5, 4));
        assertEquals(0, OrderedMatchingPolicy.detentionEvictionsForEnqueue(1, 0));
    }

    @Test
    void idleSourceAddsCursorResetWhenNoDetentionRemains() {
        assertTrue(OrderedMatchingPolicy.shouldResetPerItemCursor(false, 0, false));
        assertFalse(OrderedMatchingPolicy.shouldResetPerItemCursor(true, 0, false));
        assertFalse(OrderedMatchingPolicy.shouldResetPerItemCursor(false, 1, false));
        assertFalse(OrderedMatchingPolicy.shouldResetPerItemCursor(false, 0, true));
    }

    @Test
    void perItemBatchPlanSurvivesAFourTargetTickBudget() {
        OrderedMatchingPolicy.PerItemBatchPlan plan =
                new OrderedMatchingPolicy.PerItemBatchPlan(64, 5, 0);
        assertEquals(13, plan.amount());
        for (int target = 0; target < 4; target++) {
            assertEquals(target, plan.targetIndex());
            assertEquals(13, plan.amount());
            plan.advance();
        }
        assertEquals(4, plan.targetIndex());
        assertEquals(12, plan.amount());
        assertEquals(12, plan.remainingAmount());
        plan.advance();
        assertTrue(plan.complete());
    }

    @Test
    void sixtyFourItemsKeepTheirFiveTargetResultAcrossTicks() {
        OrderedMatchingPolicy.PerItemBatchPlan plan =
                new OrderedMatchingPolicy.PerItemBatchPlan(64, 5, 0);
        int[] received = new int[5];
        while (!plan.complete()) {
            for (int visits = 0; visits < 4 && !plan.complete(); visits++) {
                received[plan.targetIndex()] += plan.amount();
                plan.advance();
            }
        }
        assertArrayEquals(new int[] {13, 13, 13, 13, 12}, received);
    }
}
