package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DistributedSlotMapTest {
    @Test
    void identicalRefreshLayoutsCompareEqual() {
        DistributedSlotMap<String> first = DistributedSlotMap.create(List.of("first", "second"), ignored -> 3);
        DistributedSlotMap<String> refreshed = DistributedSlotMap.create(List.of("first", "second"), ignored -> 3);
        DistributedSlotMap<String> changed = DistributedSlotMap.create(List.of("first", "second"), ignored -> 4);

        assertEquals(first, refreshed);
        assertEquals(first.hashCode(), refreshed.hashCode());
        assertNotEquals(first, changed);
    }

    @Test
    void flattensSixteenSeventeenSlotTargetsWithoutScanningTheirContents() {
        List<Integer> targets = IntStream.range(0, 16).boxed().toList();
        DistributedSlotMap<Integer> slots = DistributedSlotMap.create(targets, ignored -> 17);

        assertEquals(272, slots.size());
        assertEquals(new DistributedSlotMap.Slot<>(0, 0), slots.resolve(0));
        assertEquals(new DistributedSlotMap.Slot<>(0, 16), slots.resolve(16));
        assertEquals(new DistributedSlotMap.Slot<>(1, 0), slots.resolve(17));
        assertEquals(new DistributedSlotMap.Slot<>(15, 16), slots.resolve(271));
        assertNull(slots.resolve(272));
    }

    @Test
    void ignoresTargetsWithoutExposedSlots() {
        DistributedSlotMap<Integer> slots = DistributedSlotMap.create(List.of(0, 3, -1, 2), value -> value);

        assertEquals(5, slots.size());
        assertEquals(new DistributedSlotMap.Slot<>(3, 2), slots.resolve(2));
        assertEquals(new DistributedSlotMap.Slot<>(2, 0), slots.resolve(3));
    }

    @Test
    void resolvingSlotsUsesOnlyTheCachedLayout() {
        AtomicInteger slotCountReads = new AtomicInteger();
        DistributedSlotMap<Integer> slots = DistributedSlotMap.create(List.of(17, 17), value -> {
            slotCountReads.incrementAndGet();
            return value;
        });

        assertEquals(2, slotCountReads.get());
        assertEquals(new DistributedSlotMap.Slot<>(17, 16), slots.resolve(16));
        assertEquals(new DistributedSlotMap.Slot<>(17, 0), slots.resolve(17));
        assertEquals(2, slotCountReads.get());
    }
}
