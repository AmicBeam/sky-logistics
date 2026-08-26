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
}
