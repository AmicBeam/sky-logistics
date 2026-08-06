package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class LineNamingTest {
    @Test
    void cleansPrefixesAndClampsIndexes() {
        assertEquals("Alice_Bob-2", LineNaming.indexedName("  Alice   Bob  ", 2));
        assertEquals("Line-0", LineNaming.indexedName(" ", -4));
        assertEquals("abcdefghijklmnopqrstuvwx-1",
                LineNaming.indexedName("abcdefghijklmnopqrstuvwxyz", 1));
    }

    @Test
    void normalizesDisplayNames() {
        assertEquals("Alpha Beta", LineNaming.validName("  Alpha   Beta  ", "Fallback"));
        assertEquals("Fallback Name", LineNaming.validName(" ", "  Fallback   Name "));
        assertEquals(48, LineNaming.validName("x".repeat(60), "Fallback").length());
    }

    @Test
    void derivesStableCaseSensitiveIds() {
        assertEquals(LineNaming.idForName(" Alpha   Beta "), LineNaming.idForName("Alpha Beta"));
        assertEquals(LineNaming.idForName(null), LineNaming.idForName("Line-0"));
        assertNotEquals(LineNaming.idForName("alpha"), LineNaming.idForName("Alpha"));
    }
}
