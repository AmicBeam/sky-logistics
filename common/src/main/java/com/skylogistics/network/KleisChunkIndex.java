package com.skylogistics.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Spatial index for remembered endpoints; lookup cost depends on nearby chunks, not total endpoints. */
public final class KleisChunkIndex<D, K> {
    private final Map<ChunkKey<D>, Set<K>> buckets = new HashMap<>();

    public void add(D dimension, int chunkX, int chunkZ, K key) {
        buckets.computeIfAbsent(new ChunkKey<>(dimension, chunkX, chunkZ), ignored -> new HashSet<>()).add(key);
    }

    public void remove(D dimension, int chunkX, int chunkZ, K key) {
        ChunkKey<D> chunk = new ChunkKey<>(dimension, chunkX, chunkZ);
        Set<K> keys = buckets.get(chunk);
        if (keys == null) return;
        keys.remove(key);
        if (keys.isEmpty()) buckets.remove(chunk);
    }

    public Set<K> entries(D dimension, int chunkX, int chunkZ) {
        return buckets.getOrDefault(new ChunkKey<>(dimension, chunkX, chunkZ), Set.of());
    }

    private record ChunkKey<D>(D dimension, int x, int z) {
    }
}
