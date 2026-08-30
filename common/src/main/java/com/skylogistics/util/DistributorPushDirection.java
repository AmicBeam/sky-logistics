package com.skylogistics.util;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/** The six directional distributor states plus the default all-sides state. */
public enum DistributorPushDirection implements StringRepresentable {
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST),
    ALL(null);

    private final Direction direction;

    DistributorPushDirection(Direction direction) {
        this.direction = direction;
    }

    public Direction direction() {
        return direction;
    }

    public boolean directional() {
        return direction != null;
    }

    public Direction[] initialScanDirections() {
        return direction == null ? Direction.values() : new Direction[] {direction};
    }

    /** Mirrors the ME Pattern Provider wrench interaction exactly. */
    public DistributorPushDirection afterWrenchClick(Direction clickedFace) {
        if (direction == clickedFace.getOpposite()) {
            return fromDirection(clickedFace);
        }
        if (direction == clickedFace) {
            return ALL;
        }
        if (direction == null) {
            return fromDirection(clickedFace.getOpposite());
        }
        return fromDirection(rotateAround(direction, clickedFace));
    }

    private static Direction rotateAround(Direction forward, Direction axis) {
        if (forward.getAxis() == axis.getAxis()) {
            return forward;
        }
        int x = forward.getStepY() * axis.getStepZ() - forward.getStepZ() * axis.getStepY();
        int y = forward.getStepZ() * axis.getStepX() - forward.getStepX() * axis.getStepZ();
        int z = forward.getStepX() * axis.getStepY() - forward.getStepY() * axis.getStepX();
        for (Direction candidate : Direction.values()) {
            if (candidate.getStepX() == x && candidate.getStepY() == y && candidate.getStepZ() == z) {
                return candidate;
            }
        }
        throw new IllegalStateException("Invalid distributor rotation vector: " + x + "," + y + "," + z);
    }

    public static DistributorPushDirection fromDirection(Direction direction) {
        return direction == null ? ALL : values()[direction.ordinal()];
    }

    @Override
    public String getSerializedName() {
        return direction == null ? "all" : direction.getSerializedName();
    }
}
