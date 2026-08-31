package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class DistributorInsertModeTest {
    @Test void balancedModeOffersAnEvenShare() {
        assertEquals(4, DistributorInsertMode.offer(10, 3, false));
        assertEquals(4L, DistributorInsertMode.offer(10L, 3, false));
    }

    @Test void balancedQuotaSpreadsSmallBatchesWithoutOverlappingShares() {
        assertEquals(1, DistributorInsertMode.balancedOffer(2, 4, 0));
        assertEquals(1, DistributorInsertMode.balancedOffer(2, 4, 1));
        assertEquals(0, DistributorInsertMode.balancedOffer(2, 4, 2));
        assertEquals(0, DistributorInsertMode.balancedOffer(2, 4, 3));

        assertEquals(2, DistributorInsertMode.balancedOffer(5, 4, 0));
        assertEquals(1, DistributorInsertMode.balancedOffer(5, 4, 1));
        assertEquals(1, DistributorInsertMode.balancedOffer(5, 4, 2));
        assertEquals(1, DistributorInsertMode.balancedOffer(5, 4, 3));
    }

    @Test void sequentialModeOffersEverythingRemaining() {
        assertEquals(10, DistributorInsertMode.offer(10, 3, true));
        assertEquals(10L, DistributorInsertMode.offer(10L, 3, true));
    }

    @Test void balancedAssignmentsRedistributeUnusedCapacity() {
        assertArrayEquals(new int[] {9, 1},
                DistributorInsertMode.balancedAssignments(10, new int[] {100, 1}));
        assertArrayEquals(new int[] {1, 9},
                DistributorInsertMode.balancedAssignments(10, new int[] {1, 100}));
        assertArrayEquals(new int[] {4, 3, 3},
                DistributorInsertMode.balancedAssignments(10, new int[] {64, 64, 64}));
    }
}
