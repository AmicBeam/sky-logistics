package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DistributedTargetProbeSchedulerTest {
    @Test void maintainedDemandCapsFallbackMachineInterval() {
        DistributedSlotMap<String> slots = slots(Map.of("machine", 1));
        DistributedTargetProbeScheduler<String> scheduler = new DistributedTargetProbeScheduler<>();
        scheduler.configure(1, 5, 20, 40, 1);
        scheduler.recordProbe(slots, 0, 0, false);
        scheduler.setMaximumInterval(5);
        assertEquals(-1, scheduler.nextDueSlot(slots, 4));
        assertEquals(0, scheduler.nextDueSlot(slots, 5));
    }

    @Test void maintainedDemandCapsInitialStaggerAndKeepsSuccessfulLocalSlotHot() {
        DistributedSlotMap<String> staggered = slots(Map.of("a", 1, "b", 1, "c", 1, "d", 1));
        DistributedTargetProbeScheduler<String> scheduler = new DistributedTargetProbeScheduler<>();
        scheduler.configure(1, 5, 20, 40, 1);
        scheduler.dueProbeCount(staggered, 0);
        scheduler.setMaximumInterval(5, 0);
        assertEquals(4, scheduler.dueProbeCount(staggered, 4));

        DistributedSlotMap<String> localSlots = slots(Map.of("machine", 3));
        scheduler = new DistributedTargetProbeScheduler<>();
        scheduler.configure(1, 5, 20, 40, 1);
        scheduler.recordProbe(localSlots, 2, 0, true);
        scheduler.setMaximumInterval(5, 0);
        assertEquals(2, scheduler.nextDueSlot(localSlots, 5));
        scheduler.recordProbe(localSlots, 2, 5, false);
        assertEquals(2, scheduler.nextDueSlot(localSlots, 5));
        scheduler.recordProbe(localSlots, 2, 5, false);
        assertEquals(2, scheduler.nextDueSlot(localSlots, 10));
    }

    @Test void coldTargetsAreStaggeredAcrossTheFallbackWindowWithoutTierLimits() {
        DistributedSlotMap<String> slots = slots(Map.of("a", 1, "b", 1, "c", 1, "d", 1));
        DistributedTargetProbeScheduler<String> scheduler = new DistributedTargetProbeScheduler<>();

        assertEquals(1, scheduler.dueProbeCount(slots, 0));
        assertEquals(0, scheduler.nextDueSlot(slots, 0));
        scheduler.recordProbe(slots, 0, 0, false);
        assertEquals(-1, scheduler.nextDueSlot(slots, 9));
        assertEquals(1, scheduler.nextDueSlot(slots, 10));
        assertEquals(3, scheduler.dueProbeCount(slots, 39));
    }

    @Test void allSixtyFourTargetsReceiveTheirInitialProbeWithinFortyTicks() {
        List<Integer> targets = IntStream.range(0, 64).boxed().toList();
        DistributedSlotMap<Integer> slots = DistributedSlotMap.create(targets, ignored -> 1);
        DistributedTargetProbeScheduler<Integer> scheduler = new DistributedTargetProbeScheduler<>();
        Set<Integer> probed = new HashSet<>();

        for (long tick = 0; tick < 40; tick++) {
            int due = scheduler.dueProbeCount(slots, tick);
            for (int check = 0; check < due; check++) {
                int slot = scheduler.nextDueSlot(slots, tick);
                probed.add(slot);
                scheduler.recordProbe(slots, slot, tick, false);
            }
        }

        assertEquals(Set.copyOf(targets), probed);
    }

    @Test void successPromotesImmediatelyAndMissesDemoteOneTierAtATime() {
        DistributedSlotMap<String> slots = slots(Map.of("machine", 1));
        DistributedTargetProbeScheduler<String> scheduler = new DistributedTargetProbeScheduler<>();
        scheduler.configure(1, 5, 20, 40, 2);

        scheduler.recordProbe(slots, 0, 0, true);
        assertEquals(0, scheduler.nextDueSlot(slots, 1));
        scheduler.recordProbe(slots, 0, 1, false);
        assertEquals(0, scheduler.nextDueSlot(slots, 2));
        scheduler.recordProbe(slots, 0, 2, false);
        assertEquals(-1, scheduler.nextDueSlot(slots, 6));
        assertEquals(0, scheduler.nextDueSlot(slots, 7));
        scheduler.recordProbe(slots, 0, 7, false);
        scheduler.recordProbe(slots, 0, 12, false);
        assertEquals(-1, scheduler.nextDueSlot(slots, 31));
        assertEquals(0, scheduler.nextDueSlot(slots, 32));
    }

    @Test void emptyTargetsRotateLocalSlotsWhileSuccessKeepsTheMachineLocalHotSlot() {
        DistributedSlotMap<String> slots = slots(Map.of("machine", 3));
        DistributedTargetProbeScheduler<String> scheduler = new DistributedTargetProbeScheduler<>();
        scheduler.configure(1, 2, 3, 4, 1);

        assertEquals(0, scheduler.nextDueSlot(slots, 0));
        scheduler.recordProbe(slots, 0, 0, false);
        assertEquals(1, scheduler.nextDueSlot(slots, 0));
        scheduler.recordProbe(slots, 1, 0, false);
        assertEquals(2, scheduler.nextDueSlot(slots, 0));
        scheduler.recordProbe(slots, 2, 0, true);
        scheduler.recordProbe(slots, 2, 0, false);
        assertEquals(2, scheduler.nextDueSlot(slots, 1));
    }

    @Test void simulatedCandidateRejectedByTheTargetDoesNotPinTheSourceSlot() {
        DistributedSlotMap<String> slots = slots(Map.of("machine", 4));
        DistributedTargetProbeScheduler<String> scheduler = new DistributedTargetProbeScheduler<>();

        assertEquals(0, scheduler.nextDueSlot(slots, 0));
        scheduler.recordSimulatedProbe(slots, 0, 0, true);
        assertEquals(1, scheduler.nextDueSlot(slots, 0));
        scheduler.recordProbe(slots, 0, 0, false);
        assertEquals(1, scheduler.nextDueSlot(slots, 0));

        scheduler.recordSimulatedProbe(slots, 1, 0, false);
        assertEquals(2, scheduler.nextDueSlot(slots, 0));

        scheduler.recordSimulatedProbe(slots, 2, 0, true);
        scheduler.recordProbe(slots, 2, 0, true);
        assertEquals(2, scheduler.nextDueSlot(slots, 1));
    }

    @Test void policyIntervalsAreNormalizedAndCanChangeAtRuntime() {
        DistributedSlotMap<String> slots = slots(Map.of("machine", 1));
        DistributedTargetProbeScheduler<String> scheduler = new DistributedTargetProbeScheduler<>();
        scheduler.recordProbe(slots, 0, 0, true);

        scheduler.configure(4, 2, 1, 3, 3);
        assertEquals(-1, scheduler.nextDueSlot(slots, 3));
        assertEquals(0, scheduler.nextDueSlot(slots, 4));
    }

    @Test void explicitTopologyIdentityPreservesStateWhenUnrelatedMetadataChanges() {
        DistributedSlotMap<Machine> oldSlots = DistributedSlotMap.create(List.of(
                new Machine("a", false), new Machine("b", false), new Machine("c", false)), ignored -> 1);
        DistributedTargetProbeScheduler<Machine> scheduler = new DistributedTargetProbeScheduler<>();
        scheduler.configure(1, 5, 20, 40, 3);
        for (int target = 0; target < 3; target++) scheduler.recordProbe(oldSlots, target, 0, false);
        scheduler.recordProbe(oldSlots, 1, 0, true);

        DistributedSlotMap<Machine> newSlots = DistributedSlotMap.create(List.of(
                new Machine("c", true), new Machine("a", true), new Machine("b", true)), ignored -> 1);
        scheduler.remapTargets(newSlots, new int[] {2, 0, 1}, 0);

        assertEquals(2, scheduler.nextDueSlot(newSlots, 1));
    }

    private static DistributedSlotMap<String> slots(Map<String, Integer> counts) {
        List<String> targets = counts.keySet().stream().sorted().toList();
        return DistributedSlotMap.create(targets, counts::get);
    }

    private record Machine(String id, boolean unrelatedCapability) {}
}
