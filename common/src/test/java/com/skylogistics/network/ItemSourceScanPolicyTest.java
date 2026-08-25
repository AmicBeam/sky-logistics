package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemSourceScanPolicyTest {
    @Test
    void usesExhaustiveScanWhenOperationRateCoversEverySlot() {
        assertTrue(ItemSourceScanPolicy.scansEverySlot(17, 17));
        assertTrue(ItemSourceScanPolicy.scansEverySlot(17, 18));
    }

    @Test
    void keepsCachedScanWhenOperationRateCannotCoverEverySlot() {
        assertFalse(ItemSourceScanPolicy.scansEverySlot(17, 16));
        assertFalse(ItemSourceScanPolicy.scansEverySlot(0, 17));
    }
}
