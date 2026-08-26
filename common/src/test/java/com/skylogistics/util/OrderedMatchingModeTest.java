package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderedMatchingModeTest {
    @Test
    void defaultsToPerSlotForMissingOrUnknownData() {
        assertEquals(OrderedMatchingMode.PER_SLOT, OrderedMatchingMode.byName(null));
        assertEquals(OrderedMatchingMode.PER_SLOT, OrderedMatchingMode.byName("unknown"));
    }

    @Test
    void cyclesBetweenPerSlotAndPerItem() {
        assertEquals(OrderedMatchingMode.PER_ITEM, OrderedMatchingMode.PER_SLOT.next());
        assertEquals(OrderedMatchingMode.PER_SLOT, OrderedMatchingMode.PER_ITEM.next());
    }
}
