package com.skylogistics.util;

import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared simple-pipe geometry and mask helpers.
 *
 * <p>The shape cache is indexed by the six connected-side bits followed by the
 * six extracting-side bits. Entries are built lazily so normal worlds only pay
 * for combinations that are actually queried.</p>
 */
public final class SimplePipeGeometry {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int SIDE_COUNT = DIRECTIONS.length;
    private static final int SIDE_MASK = (1 << SIDE_COUNT) - 1;
    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape[] NORMAL_ARMS = makeArmShapes(false);
    private static final VoxelShape[] EXTRACT_ARMS = makeArmShapes(true);
    private static final AtomicReferenceArray<VoxelShape> SHAPES =
            new AtomicReferenceArray<>(1 << (SIDE_COUNT * 2));

    static {
        SHAPES.set(0, CORE);
    }

    private SimplePipeGeometry() {
    }

    public static int sideMask(Direction direction) {
        return 1 << direction.ordinal();
    }

    public static int connectedExtractMask(int connectionMask, int extractMask) {
        return connectionMask & extractMask & SIDE_MASK;
    }

    public static VoxelShape shape(int connectionMask, int extractMask) {
        int connected = connectionMask & SIDE_MASK;
        int extracting = connectedExtractMask(connected, extractMask);
        int key = connected | extracting << SIDE_COUNT;
        VoxelShape cached = SHAPES.get(key);
        if (cached != null) {
            return cached;
        }

        VoxelShape built = CORE;
        for (Direction direction : DIRECTIONS) {
            int side = sideMask(direction);
            if ((connected & side) == 0) {
                continue;
            }
            VoxelShape arm = (extracting & side) != 0
                    ? EXTRACT_ARMS[direction.ordinal()]
                    : NORMAL_ARMS[direction.ordinal()];
            built = Shapes.or(built, arm);
        }
        built = built.optimize();
        if (SHAPES.compareAndSet(key, null, built)) {
            return built;
        }
        return SHAPES.get(key);
    }

    public static boolean isArmVertex(Direction direction, float x, float y, float z) {
        float axis = axisCoordinate(direction.getAxis(), x, y, z);
        return insideArmAxis(direction, axis)
                && insideEndpoint(direction.getAxis(), x, y, z);
    }

    private static boolean insideArmAxis(Direction direction, float coordinate) {
        return direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                ? coordinate >= -0.0001F && coordinate <= 4.0F / 16.0F + 0.0001F
                : coordinate >= 12.0F / 16.0F - 0.0001F && coordinate <= 1.0001F;
    }

    private static boolean insideEndpoint(Direction.Axis axis, float x, float y, float z) {
        float minimum = 5.0F / 16.0F - 0.0001F;
        float maximum = 11.0F / 16.0F + 0.0001F;
        return (axis == Direction.Axis.X || x >= minimum && x <= maximum)
                && (axis == Direction.Axis.Y || y >= minimum && y <= maximum)
                && (axis == Direction.Axis.Z || z >= minimum && z <= maximum);
    }

    private static float axisCoordinate(Direction.Axis axis, float x, float y, float z) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    private static VoxelShape[] makeArmShapes(boolean extract) {
        VoxelShape[] result = new VoxelShape[DIRECTIONS.length];
        for (Direction direction : DIRECTIONS) {
            if (extract) {
                VoxelShape shape = orientedBox(direction, 5.5D, 5.5D, 2.0D, 10.5D, 10.5D, 5.0D);
                shape = Shapes.or(shape,
                        orientedBox(direction, 3.0D, 3.0D, 0.0D, 13.0D, 5.5D, 2.0D),
                        orientedBox(direction, 3.0D, 10.5D, 0.0D, 13.0D, 13.0D, 2.0D),
                        orientedBox(direction, 3.0D, 5.5D, 0.0D, 5.5D, 10.5D, 2.0D),
                        orientedBox(direction, 10.5D, 5.5D, 0.0D, 13.0D, 10.5D, 2.0D));
                result[direction.ordinal()] = shape.optimize();
            } else {
                result[direction.ordinal()] =
                        orientedBox(direction, 5.5D, 5.5D, 0.0D, 10.5D, 10.5D, 5.0D);
            }
        }
        return result;
    }

    private static VoxelShape orientedBox(Direction direction, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        return switch (direction) {
            case NORTH -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
            case SOUTH -> Block.box(16.0D - maxX, minY, 16.0D - maxZ, 16.0D - minX, maxY, 16.0D - minZ);
            case WEST -> Block.box(minZ, minY, minX, maxZ, maxY, maxX);
            case EAST -> Block.box(16.0D - maxZ, minY, 16.0D - maxX, 16.0D - minZ, maxY, 16.0D - minX);
            case DOWN -> Block.box(minX, minZ, minY, maxX, maxZ, maxY);
            case UP -> Block.box(minX, 16.0D - maxZ, 16.0D - maxY, maxX, 16.0D - minZ, 16.0D - minY);
        };
    }
}
