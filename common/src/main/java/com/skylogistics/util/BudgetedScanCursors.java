package com.skylogistics.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cursor bank for scans that may be suspended by a per-tick budget.
 * Each key retains its own next index so interleaved resource candidates cannot reset one another.
 */
public final class BudgetedScanCursors<K> {
    private final Map<K, Integer> nextIndices;

    public BudgetedScanCursors(int expectedCandidates) {
        int capacity = Math.max(1, expectedCandidates);
        nextIndices = new LinkedHashMap<>(capacity, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Integer> eldest) {
                return size() > capacity;
            }
        };
    }

    public int start(K key, int size) {
        if (size <= 0) return 0;
        return Math.floorMod(nextIndices.getOrDefault(key, 0), size);
    }

    public void resumeAt(K key, int nextIndex, int size) {
        if (size <= 0) {
            reset(key);
            return;
        }
        nextIndices.put(key, Math.floorMod(nextIndex, size));
    }

    public void reset(K key) {
        nextIndices.remove(key);
    }

    public void clear() {
        nextIndices.clear();
    }
}
