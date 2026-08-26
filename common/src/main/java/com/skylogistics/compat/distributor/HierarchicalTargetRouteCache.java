package com.skylogistics.compat.distributor;

import java.util.Arrays;
import java.util.LinkedHashMap;

/**
 * Bounded key-to-target routing state shared by hierarchical distributor insertion paths.
 * Successful targets stay hot, while misses back off independently for every key/target pair.
 */
public final class HierarchicalTargetRouteCache<K> {
    private final AdaptiveProbeBackoff backoff = new AdaptiveProbeBackoff();
    private final LinkedHashMap<K, RouteState> routes = new LinkedHashMap<>(16, 0.75F, true);
    private int maxRoutes = 64;

    public void configure(int configuredMaxRoutes, int hotTicks, int warmTicks, int coolTicks,
            int fallbackTicks, int configuredMissesPerDemotion) {
        maxRoutes = Math.max(1, configuredMaxRoutes);
        backoff.configure(hotTicks, warmTicks, coolTicks, fallbackTicks, configuredMissesPerDemotion);
        trimToCapacity();
    }

    /** Writes hot targets first, followed by key-specific targets whose retry is due. */
    public int orderCandidates(K key, int targetCount, long gameTime, int[] output) {
        if (key == null || targetCount <= 0 || output.length == 0) return 0;
        RouteState route = route(key, targetCount);
        int limit = Math.min(targetCount, output.length);
        int written = 0;
        for (int offset = 0; offset < targetCount && written < limit; offset++) {
            int target = Math.floorMod(route.hotCursor + offset, targetCount);
            if (route.successful[target]) output[written++] = target;
        }
        route.hotCursor = (route.hotCursor + 1) % targetCount;
        for (int offset = 0; offset < targetCount && written < limit; offset++) {
            int target = Math.floorMod(route.discoveryCursor + offset, targetCount);
            if (!route.successful[target] && gameTime >= route.retryAfter[target]) {
                output[written++] = target;
            }
        }
        route.discoveryCursor = (route.discoveryCursor + 1) % targetCount;
        return written;
    }

    public boolean isSuccessful(K key, int target, int targetCount) {
        RouteState route = routes.get(key);
        return route != null && route.targetCount == targetCount
                && target >= 0 && target < targetCount && route.successful[target];
    }

    public void recordSuccess(K key, int target, int targetCount) {
        if (key == null || target < 0 || target >= targetCount) return;
        RouteState route = route(key, targetCount);
        route.everSuccessful[target] = true;
        route.successful[target] = true;
        route.tiers[target] = AdaptiveProbeBackoff.HOT;
        route.tierMisses[target] = 0;
        route.retryAfter[target] = Long.MIN_VALUE;
        route.hotCursor = (target + 1) % targetCount;
    }

    public void recordMiss(K key, int target, int targetCount, long gameTime) {
        if (key == null || target < 0 || target >= targetCount) return;
        RouteState route = route(key, targetCount);
        route.successful[target] = false;
        if (!route.everSuccessful[target]) {
            route.tiers[target] = AdaptiveProbeBackoff.FALLBACK;
            route.tierMisses[target] = 0;
        } else if (backoff.shouldDemote(++route.tierMisses[target])) {
            route.tiers[target] = backoff.demote(route.tiers[target]);
            route.tierMisses[target] = 0;
        }
        route.retryAfter[target] = gameTime + backoff.interval(route.tiers[target]);
        route.discoveryCursor = (target + 1) % targetCount;
    }

    public void clear() {
        routes.clear();
    }

    int size() {
        return routes.size();
    }

    private RouteState route(K key, int targetCount) {
        RouteState route = routes.get(key);
        if (route == null || route.targetCount != targetCount) {
            route = new RouteState(targetCount);
            routes.put(key, route);
            trimToCapacity();
        }
        return route;
    }

    private void trimToCapacity() {
        while (routes.size() > maxRoutes) {
            K eldest = routes.keySet().iterator().next();
            routes.remove(eldest);
        }
    }

    private static final class RouteState {
        private final int targetCount;
        private final boolean[] successful;
        private final boolean[] everSuccessful;
        private final byte[] tiers;
        private final int[] tierMisses;
        private final long[] retryAfter;
        private int hotCursor;
        private int discoveryCursor;

        private RouteState(int targetCount) {
            this.targetCount = targetCount;
            successful = new boolean[targetCount];
            everSuccessful = new boolean[targetCount];
            tiers = new byte[targetCount];
            Arrays.fill(tiers, AdaptiveProbeBackoff.FALLBACK);
            tierMisses = new int[targetCount];
            retryAfter = new long[targetCount];
            Arrays.fill(retryAfter, Long.MIN_VALUE);
        }
    }
}
