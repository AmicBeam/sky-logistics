package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LineRouteCacheTest {
    @Test
    void reusesEmptyAndPopulatedSnapshotsUntilTheirLineChanges() {
        var cache = new LineRouteCache<String, String, String>();
        var calls = new AtomicInteger();
        var endpoints = new ArrayList<String>();
        var empty = cache.get("A", "out", () -> { calls.incrementAndGet(); return endpoints; });
        endpoints.add("chest");
        assertSame(empty, cache.get("A", "out", () -> fail("must reuse the cached empty route")));
        cache.invalidate("A");
        var populated = cache.get("A", "out", () -> { calls.incrementAndGet(); return endpoints; });
        assertEquals(List.of("chest"), populated);
        assertEquals(2, calls.get());
        endpoints.clear();
        assertEquals(List.of("chest"), populated);
        assertThrows(UnsupportedOperationException.class, () -> populated.add("other"));
    }

    @Test
    void invalidatesInputsAndAllOutputResourcesWithoutTouchingOtherLines() {
        var cache = new LineRouteCache<String, String, String>();
        for (String query : List.of("in", "item-out", "fluid-out")) {
            cache.get("A", query, () -> List.of("old"));
        }
        var unrelated = cache.get("B", "out", () -> List.of("keep"));
        cache.invalidate("A");
        for (String query : List.of("in", "item-out", "fluid-out")) {
            assertEquals(List.of("new"), cache.get("A", query, () -> List.of("new")));
        }
        assertSame(unrelated, cache.get("B", "out", () -> fail("unrelated line was invalidated")));
        cache.clear();
        assertTrue(cache.get("B", "out", List::of).isEmpty());
    }
}
