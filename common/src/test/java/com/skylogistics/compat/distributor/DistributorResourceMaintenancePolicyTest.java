package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class DistributorResourceMaintenancePolicyTest {
    @Test void balancedAmountTargetIsAppliedPerDevice() {
        assertArrayEquals(new long[] {50, 50}, DistributorResourceMaintenancePolicy.assignments(
                100, new long[] {950, 900}, new long[] {100, 100}, new int[] {1, 1},
                new long[] {50, 100}, true, 1000, true, false));
        assertArrayEquals(new long[] {0, 100}, DistributorResourceMaintenancePolicy.assignments(
                100, new long[] {1000, 500}, new long[] {100, 100}, new int[] {1, 1},
                new long[] {0, 500}, true, 1000, true, false));
    }

    @Test void balancedSlotTargetIsAppliedPerDevice() {
        assertArrayEquals(new long[] {50, 50, 50}, DistributorResourceMaintenancePolicy.assignments(
                150, new long[] {10, 0, 0}, new long[] {100, 100, 100}, new int[] {1, 0, 0},
                new long[] {90, 0, 0}, false, 1, true, false));
        assertArrayEquals(new long[] {0, 75, 75}, DistributorResourceMaintenancePolicy.assignments(
                150, new long[] {10, 0, 0}, new long[] {100, 100, 100}, new int[] {1, 0, 0},
                new long[] {90, 0, 0}, false, 1, false, false));
    }

    @Test void sequentialModeUsesOneAggregateTarget() {
        assertArrayEquals(new long[] {50, 0}, DistributorResourceMaintenancePolicy.assignments(
                100, new long[] {400, 550}, new long[] {100, 100}, new int[] {1, 1},
                new long[] {100, 100}, true, 1000, true, true));
    }
}
