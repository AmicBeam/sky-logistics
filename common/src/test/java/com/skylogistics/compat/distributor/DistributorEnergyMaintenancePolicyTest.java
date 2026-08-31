package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class DistributorEnergyMaintenancePolicyTest {
    @Test void balancedAmountModeMaintainsEveryDeviceIndependently() {
        assertArrayEquals(new int[] {50, 50}, DistributorEnergyMaintenancePolicy.assignments(
                100, new int[] {950, 900}, new int[] {100, 100}, true, 1000, true, false));
        assertArrayEquals(new int[] {0, 100}, DistributorEnergyMaintenancePolicy.assignments(
                100, new int[] {1000, 500}, new int[] {100, 100}, true, 1000, true, false));
    }

    @Test void balancedSlotModeAppliesTheSlotTargetToEveryDevice() {
        assertArrayEquals(new int[] {50, 50, 50}, DistributorEnergyMaintenancePolicy.assignments(
                150, new int[] {10, 0, 0}, new int[] {100, 100, 100}, false, 1, true, false));
        assertArrayEquals(new int[] {0, 75, 75}, DistributorEnergyMaintenancePolicy.assignments(
                150, new int[] {10, 0, 0}, new int[] {100, 100, 100}, false, 1, false, false));
    }

    @Test void sequentialModeFillsInCursorOrder() {
        assertArrayEquals(new int[] {60, 40}, DistributorEnergyMaintenancePolicy.assignments(
                100, new int[] {0, 0}, new int[] {60, 60}, true, 100, true, true));
    }
}
