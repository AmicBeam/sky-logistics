package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemSourceSlotCachePolicyTest {
    @Test
    void singleSlotSourcesBypassHotSlots() {
        assertFalse(ItemSourceSlotCachePolicy.usesHotSlots(1));
        assertEquals(0, ItemSourceSlotCachePolicy.hotSlotCapacity(9, 1));
    }

    @Test
    void cacheCapacityIsCappedByActualSlotCount() {
        assertTrue(ItemSourceSlotCachePolicy.usesHotSlots(2));
        assertEquals(2, ItemSourceSlotCachePolicy.hotSlotCapacity(9, 2));
        assertEquals(9, ItemSourceSlotCachePolicy.hotSlotCapacity(9, 27));
    }
}
