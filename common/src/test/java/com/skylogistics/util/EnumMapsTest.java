package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnumMapsTest {
    @Test
    void copiesEmptyGenericMapWithExplicitEnumType() {
        var copy = assertDoesNotThrow(() -> EnumMaps.copyOf(Key.class, Map.of()));
        assertTrue(copy.isEmpty());
    }

    @Test
    void copiesNonEmptyMap() {
        assertEquals(Map.of(Key.VALUE, "value"), EnumMaps.copyOf(Key.class, Map.of(Key.VALUE, "value")));
    }

    private enum Key {
        VALUE
    }
}
