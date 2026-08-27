package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedstonePulseLatchTest {
    @Test
    void firstSampleOnlyEstablishesTheChunkLoadBaseline() {
        RedstonePulseLatch latch = new RedstonePulseLatch();

        assertFalse(latch.sample(true));
        assertFalse(latch.isArmed());
    }

    @Test
    void risingEdgeArmsUntilOneSuccessConsumesIt() {
        RedstonePulseLatch latch = new RedstonePulseLatch();
        latch.sample(false);

        assertTrue(latch.sample(true));
        assertTrue(latch.isArmed());
        assertTrue(latch.consume());
        assertFalse(latch.isArmed());
        assertFalse(latch.sample(true));
        assertFalse(latch.isArmed());
    }

    @Test
    void fallingThenRisingArmsAgain() {
        RedstonePulseLatch latch = new RedstonePulseLatch();
        latch.reset(true);
        latch.sample(false);

        assertTrue(latch.sample(true));
        assertTrue(latch.isArmed());
    }

    @Test
    void pendingPulseSurvivesReloadWithoutCreatingAnotherEdge() {
        RedstonePulseLatch latch = new RedstonePulseLatch();
        latch.restoreArmed(true);

        assertFalse(latch.sample(true));
        assertTrue(latch.isArmed());
    }
}
