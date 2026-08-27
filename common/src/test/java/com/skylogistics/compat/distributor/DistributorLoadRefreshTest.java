package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistributorLoadRefreshTest {
    @Test void runsExactlyOnceAfterEachLoad() {
        DistributorLoadRefresh refresh = new DistributorLoadRefresh();

        assertFalse(refresh.consume());
        refresh.schedule();
        assertTrue(refresh.consume());
        assertFalse(refresh.consume());
        refresh.schedule();
        assertTrue(refresh.consume());
    }
}
