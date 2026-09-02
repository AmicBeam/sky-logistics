package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class KleisChunkIndexTest {
    @Test
    void indexesEndpointsByDimensionAndChunk() {
        KleisChunkIndex<String, String> index = new KleisChunkIndex<>();
        index.add("overworld", 2, -3, "north-face");
        index.add("overworld", 2, -3, "south-face");
        index.add("the_nether", 2, -3, "nether-face");

        assertEquals(2, index.entries("overworld", 2, -3).size());
        assertEquals(1, index.entries("the_nether", 2, -3).size());
        assertTrue(index.entries("overworld", 3, -3).isEmpty());
    }

    @Test
    void removingLastEndpointClearsOnlyItsBucket() {
        KleisChunkIndex<String, String> index = new KleisChunkIndex<>();
        index.add("overworld", 0, 0, "a");
        index.add("overworld", 1, 0, "b");

        index.remove("overworld", 0, 0, "a");

        assertTrue(index.entries("overworld", 0, 0).isEmpty());
        assertEquals(Set.of("b"), index.entries("overworld", 1, 0));
    }
}
