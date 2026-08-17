package com.skylogistics.util;

import net.minecraft.core.Direction;

/** Pure hit-position helper shared by pipe interaction and Jade integration. */
public final class SimplePipeEndpointTargeting {
    private SimplePipeEndpointTargeting() {}

    public static Direction endpointDirection(double x, double y, double z) {
        double min = 5.0D / 16.0D;
        double max = 11.0D / 16.0D;
        double best = 0.0D;
        Direction result = null;
        if (x < min && min - x > best) { best = min - x; result = Direction.WEST; }
        if (x > max && x - max > best) { best = x - max; result = Direction.EAST; }
        if (y < min && min - y > best) { best = min - y; result = Direction.DOWN; }
        if (y > max && y - max > best) { best = y - max; result = Direction.UP; }
        if (z < min && min - z > best) { best = min - z; result = Direction.NORTH; }
        if (z > max && z - max > best) result = Direction.SOUTH;
        return result;
    }
}
