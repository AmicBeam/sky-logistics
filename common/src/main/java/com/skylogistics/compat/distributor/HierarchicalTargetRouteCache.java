package com.skylogistics.compat.distributor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded key-to-target routing state shared by hierarchical distributor insertion paths.
 * Successful targets stay hot, while misses back off independently for every key/target pair.
 */
public final class HierarchicalTargetRouteCache<K> {
    private final AdaptiveProbeBackoff backoff = new AdaptiveProbeBackoff();
    private final LinkedHashMap<K, RouteState> routes = new LinkedHashMap<>(16, 0.75F, true);
    private int maxRoutes = 64;
    private int maximumInterval;

    public void setMaximumInterval(int ticks) {
        maximumInterval = Math.max(0, ticks);
    }

    public void configure(int configuredMaxRoutes, int hotTicks, int warmTicks, int coolTicks,
            int fallbackTicks, int configuredMissesPerDemotion) {
        maxRoutes = Math.max(1, configuredMaxRoutes);
        backoff.configure(hotTicks, warmTicks, coolTicks, fallbackTicks, configuredMissesPerDemotion);
        trimToCapacity();
    }

    /**
     * Writes hot targets first, then newly added topology targets, followed by older
     * key-specific targets whose retry is due.
     */
    public int orderCandidates(K key, int targetCount, long gameTime, int[] output) {
        if (key == null || targetCount <= 0 || output.length == 0) return 0;
        RouteState route = route(key, targetCount);
        int limit = Math.min(targetCount, output.length);
        int written = 0;
        for (int offset = 0; offset < targetCount && written < limit; offset++) {
            int target = Math.floorMod(route.hotCursor + offset, targetCount);
            if (route.successful[target]) {
                output[written++] = target;
            }
        }
        for (int offset = 0; offset < targetCount && written < limit; offset++) {
            int target = Math.floorMod(route.discoveryCursor + offset, targetCount);
            if (!route.successful[target] && route.newTopologyTarget[target]
                    && gameTime >= route.retryAfter[target]) {
                output[written++] = target;
            }
        }
        for (int offset = 0; offset < targetCount && written < limit; offset++) {
            int target = Math.floorMod(route.discoveryCursor + offset, targetCount);
            if (!route.successful[target] && !route.newTopologyTarget[target]
                    && gameTime >= route.retryAfter[target]) {
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

    public int successfulTargetCount(K key, int targetCount) {
        RouteState route = routes.get(key);
        if (route == null || route.targetCount != targetCount) return 0;
        int count = 0;
        for (boolean successful : route.successful) {
            if (successful) count++;
        }
        return count;
    }

    /** Continues the next request after the last machine that actually received this key. */
    public void advanceHotCursorAfter(K key, int target, int targetCount) {
        RouteState route = routes.get(key);
        if (route == null || route.targetCount != targetCount || target < 0 || target >= targetCount) return;
        route.hotCursor = (target + 1) % targetCount;
    }

    public void recordSuccess(K key, int target, int targetCount) {
        if (key == null || target < 0 || target >= targetCount) return;
        RouteState route = route(key, targetCount);
        route.everSuccessful[target] = true;
        route.successful[target] = true;
        route.newTopologyTarget[target] = false;
        route.tiers[target] = AdaptiveProbeBackoff.HOT;
        route.tierMisses[target] = 0;
        route.retryAfter[target] = Long.MIN_VALUE;
    }

    public void recordMiss(K key, int target, int targetCount, long gameTime) {
        if (key == null || target < 0 || target >= targetCount) return;
        RouteState route = route(key, targetCount);
        route.successful[target] = false;
        route.newTopologyTarget[target] = false;
        if (!route.everSuccessful[target]) {
            route.tiers[target] = AdaptiveProbeBackoff.FALLBACK;
            route.tierMisses[target] = 0;
        } else if (backoff.shouldDemote(++route.tierMisses[target])) {
            route.tiers[target] = backoff.demote(route.tiers[target]);
            route.tierMisses[target] = 0;
        }
        int interval = backoff.interval(route.tiers[target]);
        route.retryAfter[target] = gameTime
                + (maximumInterval > 0 ? Math.min(interval, maximumInterval) : interval);
        route.discoveryCursor = (target + 1) % targetCount;
    }

    public void clear() {
        routes.clear();
    }

    /** Preserves per-machine state across stable-identity reorder, addition, and removal. */
    public void remapTargets(int[] oldIndexForNew) {
        if (oldIndexForNew == null) return;
        for (Map.Entry<K, RouteState> entry : routes.entrySet()) {
            RouteState old = entry.getValue();
            RouteState remapped = new RouteState(oldIndexForNew.length);
            for (int target = 0; target < oldIndexForNew.length; target++) {
                int oldTarget = oldIndexForNew[target];
                if (oldTarget < 0 || oldTarget >= old.targetCount) continue;
                remapped.successful[target] = old.successful[oldTarget];
                remapped.everSuccessful[target] = old.everSuccessful[oldTarget];
                remapped.newTopologyTarget[target] = old.newTopologyTarget[oldTarget];
                remapped.tiers[target] = old.tiers[oldTarget];
                remapped.tierMisses[target] = old.tierMisses[oldTarget];
                remapped.retryAfter[target] = old.retryAfter[oldTarget];
            }
            for (int target = 0; target < oldIndexForNew.length; target++) {
                if (oldIndexForNew[target] < 0) remapped.newTopologyTarget[target] = true;
            }
            remapped.hotCursor = remappedCursor(old, old.hotCursor, oldIndexForNew, true);
            remapped.discoveryCursor = remappedCursor(old, old.discoveryCursor, oldIndexForNew, false);
            entry.setValue(remapped);
        }
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

    private static int remappedCursor(RouteState old, int oldCursor, int[] oldIndexForNew, boolean successful) {
        if (oldIndexForNew.length == 0 || old.targetCount == 0) return 0;
        for (int offset = 0; offset < old.targetCount; offset++) {
            int candidate = Math.floorMod(oldCursor + offset, old.targetCount);
            if (old.successful[candidate] != successful) continue;
            for (int target = 0; target < oldIndexForNew.length; target++) {
                if (oldIndexForNew[target] == candidate) return target;
            }
        }
        return 0;
    }

    private static final class RouteState {
        private final int targetCount;
        private final boolean[] successful;
        private final boolean[] everSuccessful;
        private final boolean[] newTopologyTarget;
        private final byte[] tiers;
        private final int[] tierMisses;
        private final long[] retryAfter;
        private int hotCursor;
        private int discoveryCursor;

        private RouteState(int targetCount) {
            this.targetCount = targetCount;
            successful = new boolean[targetCount];
            everSuccessful = new boolean[targetCount];
            newTopologyTarget = new boolean[targetCount];
            tiers = new byte[targetCount];
            Arrays.fill(tiers, AdaptiveProbeBackoff.FALLBACK);
            tierMisses = new int[targetCount];
            retryAfter = new long[targetCount];
            Arrays.fill(retryAfter, Long.MIN_VALUE);
        }
    }
}
