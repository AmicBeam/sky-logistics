package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RedstoneControlTest {
    @Test
    void cyclesThroughAllFourUserFacingModes() {
        assertEquals(RedstoneControl.HIGH, RedstoneControl.IGNORE.next());
        assertEquals(RedstoneControl.LOW, RedstoneControl.HIGH.next());
        assertEquals(RedstoneControl.PULSE, RedstoneControl.LOW.next());
        assertEquals(RedstoneControl.IGNORE, RedstoneControl.PULSE.next());
    }
}
