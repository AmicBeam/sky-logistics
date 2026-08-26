package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HierarchicalTargetRouteCacheTest {
    @Test void itemKeysKeepIndependentSuccessfulMachineRoutes() {
        HierarchicalTargetRouteCache<String> routes = routes();
        routes.recordSuccess("iron", 2, 4);
        routes.recordSuccess("gold", 1, 4);

        assertEquals(2, candidates(routes, "iron", 4, 0)[0]);
        assertEquals(1, candidates(routes, "gold", 4, 0)[0]);
        assertTrue(routes.isSuccessful("iron", 2, 4));
        assertFalse(routes.isSuccessful("iron", 1, 4));
    }

    @Test void neverAcceptedMachineUsesFallbackAfterItsFirstFullReject() {
        HierarchicalTargetRouteCache<String> routes = routes();
        routes.recordMiss("iron", 0, 2, 0);

        assertArrayEquals(new int[] {1}, candidates(routes, "iron", 2, 39));
        assertArrayEquals(new int[] {0, 1}, sorted(candidates(routes, "iron", 2, 40)));
    }

    @Test void formerlySuccessfulMachineDemotesThroughTheSameProbeTiers() {
        HierarchicalTargetRouteCache<String> routes = routes();
        routes.configure(8, 1, 5, 20, 40, 2);
        routes.recordSuccess("iron", 0, 1);
        routes.recordMiss("iron", 0, 1, 0);
        assertEquals(0, candidates(routes, "iron", 1, 1)[0]);
        routes.recordMiss("iron", 0, 1, 1);
        assertEquals(0, candidates(routes, "iron", 1, 5).length);
        assertEquals(0, candidates(routes, "iron", 1, 6)[0]);
    }

    @Test void hotMachinesAreOrderedBeforeDueDiscoveryTargets() {
        HierarchicalTargetRouteCache<String> routes = routes();
        routes.recordSuccess("iron", 3, 4);

        assertEquals(3, candidates(routes, "iron", 4, 0)[0]);
    }

    @Test void routeBankIsBoundedAndTargetCountChangesResetTheRoute() {
        HierarchicalTargetRouteCache<String> routes = routes();
        routes.configure(2, 1, 5, 20, 40, 3);
        routes.recordSuccess("a", 0, 2);
        routes.recordSuccess("b", 0, 2);
        routes.recordSuccess("c", 0, 2);
        assertEquals(2, routes.size());
        assertFalse(routes.isSuccessful("a", 0, 2));

        routes.recordSuccess("c", 1, 3);
        assertFalse(routes.isSuccessful("c", 0, 3));
        assertTrue(routes.isSuccessful("c", 1, 3));
    }

    @Test void twentyKeysConvergeFromFourHundredColdCandidatesToTwentyHotCandidates() {
        HierarchicalTargetRouteCache<String> routes = new HierarchicalTargetRouteCache<>();
        routes.configure(64, 1, 5, 20, 40, 3);
        int coldCandidates = 0;
        for (int key = 0; key < 20; key++) {
            String item = "item-" + key;
            int[] candidates = candidates(routes, item, 20, 0);
            coldCandidates += candidates.length;
            for (int target : candidates) {
                if (target == key) routes.recordSuccess(item, target, 20);
                else routes.recordMiss(item, target, 20, 0);
            }
        }
        assertEquals(400, coldCandidates);

        int steadyCandidates = IntStream.range(0, 20)
                .map(key -> candidates(routes, "item-" + key, 20, 1).length)
                .sum();
        assertEquals(20, steadyCandidates);
    }

    private static HierarchicalTargetRouteCache<String> routes() {
        HierarchicalTargetRouteCache<String> routes = new HierarchicalTargetRouteCache<>();
        routes.configure(8, 1, 5, 20, 40, 3);
        return routes;
    }

    private static int[] candidates(HierarchicalTargetRouteCache<String> routes, String key,
            int targets, long tick) {
        int[] output = new int[targets];
        return Arrays.copyOf(output, routes.orderCandidates(key, targets, tick, output));
    }

    private static int[] sorted(int[] values) {
        Arrays.sort(values);
        return values;
    }
}
