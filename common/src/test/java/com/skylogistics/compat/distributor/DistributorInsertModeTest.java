package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DistributorInsertModeTest {
    @Test void balancedModeOffersAnEvenShare() {
        assertEquals(4, DistributorInsertMode.offer(10, 3, false));
        assertEquals(4L, DistributorInsertMode.offer(10L, 3, false));
    }

    @Test void sequentialModeOffersEverythingRemaining() {
        assertEquals(10, DistributorInsertMode.offer(10, 3, true));
        assertEquals(10L, DistributorInsertMode.offer(10L, 3, true));
    }
}
