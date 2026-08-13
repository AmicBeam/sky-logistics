package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RedstoneControlTest {
    @Test
    void cyclesOnlyThroughTheThreeUserFacingModes() {
        assertEquals(RedstoneControl.HIGH, RedstoneControl.IGNORE.next());
        assertEquals(RedstoneControl.LOW, RedstoneControl.HIGH.next());
        assertEquals(RedstoneControl.IGNORE, RedstoneControl.LOW.next());
    }

    @Test
    void migratesLegacyDisabledValuesToIgnore() {
        assertEquals(RedstoneControl.IGNORE, RedstoneControl.byName("disabled"));
        assertEquals(RedstoneControl.HIGH, RedstoneControl.DISABLED.next());
    }
}
