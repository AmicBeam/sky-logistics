package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class SimplePipeGeometryEndpointTest {
    @Test void identifiesArmsButNotTheCenterCube() {
        assertEquals(Direction.WEST, SimplePipeEndpointTargeting.endpointDirection(0.1, 0.5, 0.5));
        assertEquals(Direction.UP, SimplePipeEndpointTargeting.endpointDirection(0.5, 0.9, 0.5));
        assertEquals(Direction.SOUTH, SimplePipeEndpointTargeting.endpointDirection(0.5, 0.5, 0.95));
        assertNull(SimplePipeEndpointTargeting.endpointDirection(0.5, 0.5, 0.5));
    }
}
