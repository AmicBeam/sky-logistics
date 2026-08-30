package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdaptiveTargetProbeSchedulerTest {
    @Test void maintainedDemandCapsFallbackProbeInterval() {
        AdaptiveTargetProbeScheduler probes = new AdaptiveTargetProbeScheduler();
        probes.configure(1, 5, 20, 40, 1);
        probes.recordProbe(1, 0, 0, false);
        probes.setMaximumInterval(5);
        assertEquals(-1, probes.nextDueTarget(1, 4));
        assertEquals(0, probes.nextDueTarget(1, 5));
        probes.setMaximumInterval(0);
        assertEquals(-1, probes.nextDueTarget(1, 20));
        assertEquals(0, probes.nextDueTarget(1, 40));
    }

    @Test void topologyRemapPreservesTierAndNextFairMachine() {
        AdaptiveTargetProbeScheduler probes = new AdaptiveTargetProbeScheduler();
        probes.configure(1, 5, 20, 40, 3);
        probes.dueProbeCount(4, 0);
        for (int target = 0; target < 4; target++) {
            probes.recordProbe(4, target, 0, false);
        }
        probes.recordProbe(4, 1, 0, true);

        probes.remapTargets(new int[] {3, 2, 0, 1}, 0);

        assertEquals(3, probes.nextDueTarget(4, 1));
    }

    @Test void unchangedIdentityRemapDoesNotRestartFallbackSchedule() {
        AdaptiveTargetProbeScheduler probes = new AdaptiveTargetProbeScheduler();
        probes.configure(1, 5, 20, 40, 3);
        probes.dueProbeCount(4, 100);
        probes.recordProbe(4, 0, 100, false);
        probes.recordProbe(4, 1, 110, false);

        probes.remapTargets(new int[] {0, 1, 2, 3}, 120);

        assertEquals(2, probes.nextDueTarget(4, 120));
    }
}
