package com.skylogistics.util;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

public enum SimplePipeExtractSide implements StringRepresentable {
    NONE("none", null),
    DOWN("down", Direction.DOWN),
    UP("up", Direction.UP),
    NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST),
    EAST("east", Direction.EAST);

    private final String serializedName;
    private final Direction direction;

    SimplePipeExtractSide(String serializedName, Direction direction) {
        this.serializedName = serializedName;
        this.direction = direction;
    }

    public static SimplePipeExtractSide of(Direction direction) {
        return switch (direction) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }

    public boolean matches(Direction direction) {
        return this.direction == direction;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
