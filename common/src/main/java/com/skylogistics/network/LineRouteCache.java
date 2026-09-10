package com.skylogistics.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Immutable route snapshots, invalidated per line. The owning registry serializes access. */
public final class LineRouteCache<L, Q, E> {
    private final Map<L, Map<Q, List<E>>> lines = new HashMap<>();

    public List<E> get(L line, Q query, Supplier<List<E>> collect) {
        return lines.computeIfAbsent(line, ignored -> new HashMap<>())
                .computeIfAbsent(query, ignored -> List.copyOf(collect.get()));
    }

    public void invalidate(L line) {
        // Drop obsolete endpoint references as soon as topology changes, even if
        // this line is never queried again (for example after its upgrade is removed).
        lines.remove(line);
    }

    public void clear() {
        lines.clear();
    }
}
