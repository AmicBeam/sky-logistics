package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BudgetedScanCursorsTest {
    @Test
    void oneVisitBudgetEventuallyReachesTheThousandthTarget() {
        BudgetedScanCursors<String> cursors = new BudgetedScanCursors<>(2);

        for (int tick = 0; tick < 1_000; tick++) {
            assertEquals(tick, cursors.start("iron", 1_001));
            cursors.resumeAt("iron", tick + 1, 1_001);
        }

        assertEquals(1_000, cursors.start("iron", 1_001));
    }

    @Test
    void resumesEachCandidateAtItsOwnBudgetBoundary() {
        BudgetedScanCursors<String> cursors = new BudgetedScanCursors<>(2);

        cursors.resumeAt("iron", 1, 1_001);
        cursors.resumeAt("gold", 47, 1_001);

        assertEquals(1, cursors.start("iron", 1_001));
        assertEquals(47, cursors.start("gold", 1_001));
    }

    @Test
    void wrapsAndResetsAfterACompletedScan() {
        BudgetedScanCursors<String> cursors = new BudgetedScanCursors<>(1);

        cursors.resumeAt("iron", 1_001, 1_001);
        assertEquals(0, cursors.start("iron", 1_001));

        cursors.resumeAt("iron", 800, 1_001);
        cursors.reset("iron");
        assertEquals(0, cursors.start("iron", 1_001));
    }

    @Test
    void advancesAcrossAThousandBudgetBoundariesWithoutRestarting() {
        BudgetedScanCursors<String> cursors = new BudgetedScanCursors<>(4);

        for (int expected = 0; expected < 1_001; expected++) {
            assertEquals(expected, cursors.start("iron", 1_001));
            cursors.resumeAt("iron", expected + 1, 1_001);
        }

        assertEquals(0, cursors.start("iron", 1_001));
    }

    @Test
    void boundsRememberedCandidates() {
        BudgetedScanCursors<String> cursors = new BudgetedScanCursors<>(2);

        cursors.resumeAt("iron", 11, 100);
        cursors.resumeAt("gold", 22, 100);
        cursors.resumeAt("copper", 33, 100);

        assertEquals(0, cursors.start("iron", 100));
        assertEquals(22, cursors.start("gold", 100));
        assertEquals(33, cursors.start("copper", 100));
    }
}
