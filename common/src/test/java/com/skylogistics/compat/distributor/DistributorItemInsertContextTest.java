package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistributorItemInsertContextTest {
    @Test void zeroAmountDisablesMaintenance() {
        assertFalse(new DistributorItemInsertContext(
                DistributorItemInsertContext.MaintainUnit.SLOTS, 0, true, false, -1, 0).maintained());
        assertTrue(new DistributorItemInsertContext(
                DistributorItemInsertContext.MaintainUnit.ITEMS, 16, false, false, -1, 0).maintained());
    }

    @Test void balancedModeCanResolveTheSameLocalSlotForEveryDevice() {
        DistributorItemInsertContext context = new DistributorItemInsertContext(
                DistributorItemInsertContext.MaintainUnit.NONE, 0, false, true, 0, -2);
        assertEquals(2, context.orderedPosition(9));
        assertEquals(2, context.orderedPosition(5));
    }

    @Test void sequentialModeCanResolveThePositionAsABoundDeviceIndex() {
        DistributorItemInsertContext context = new DistributorItemInsertContext(
                DistributorItemInsertContext.MaintainUnit.NONE, 0, false, true, 1, -2);
        assertEquals(3, context.orderedPosition(4));
        assertEquals(-1, context.orderedPosition(3));
    }
}
