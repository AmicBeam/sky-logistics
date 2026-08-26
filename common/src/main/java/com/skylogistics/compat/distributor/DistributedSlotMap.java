package com.skylogistics.compat.distributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Compactly maps a flattened virtual slot index onto a target and its local slot. */
public final class DistributedSlotMap<T> {
    private final List<Span<T>> spans;
    private final int size;

    private DistributedSlotMap(List<Span<T>> spans, int size) {
        this.spans = spans;
        this.size = size;
    }

    public static <T> DistributedSlotMap<T> create(List<T> targets, ToIntFunction<T> slotCount) {
        List<Span<T>> spans = new ArrayList<>(targets.size());
        int firstSlot = 0;
        for (T target : targets) {
            int slots = Math.max(0, slotCount.applyAsInt(target));
            int available = Integer.MAX_VALUE - firstSlot;
            int mapped = Math.min(slots, available);
            if (mapped > 0) {
                spans.add(new Span<>(target, firstSlot, mapped));
                firstSlot += mapped;
            }
            if (firstSlot == Integer.MAX_VALUE) break;
        }
        return new DistributedSlotMap<>(List.copyOf(spans), firstSlot);
    }

    public int size() {
        return size;
    }

    public Slot<T> resolve(int slot) {
        if (slot < 0 || slot >= size) return null;
        for (Span<T> span : spans) {
            int localSlot = slot - span.firstSlot;
            if (localSlot >= 0 && localSlot < span.slots) return new Slot<>(span.target, localSlot);
        }
        return null;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof DistributedSlotMap<?> map
                && size == map.size && spans.equals(map.spans);
    }

    @Override
    public int hashCode() {
        return 31 * spans.hashCode() + size;
    }

    public record Slot<T>(T target, int localSlot) {}

    private record Span<T>(T target, int firstSlot, int slots) {}
}
