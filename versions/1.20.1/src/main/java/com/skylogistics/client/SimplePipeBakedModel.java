package com.skylogistics.client;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.util.SimplePipeModelData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SimplePipeBakedModel extends BakedModelWrapper<BakedModel> {
    private final Map<Direction, List<BakedQuad>> extractQuads;

    SimplePipeBakedModel(BakedModel originalModel, Map<Direction, List<BakedQuad>> extractQuads) {
        super(originalModel);
        this.extractQuads = extractQuads;
    }

    static Map<Direction, List<BakedQuad>> rotatedExtractQuads(BakedModel northModel) {
        List<BakedQuad> northQuads = northModel.getQuads(null, null, RandomSource.create(0L));
        Map<Direction, List<BakedQuad>> result = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            List<BakedQuad> rotated = new ArrayList<>(northQuads.size());
            for (BakedQuad quad : northQuads) {
                rotated.add(rotateQuad(quad, direction));
            }
            result.put(direction, List.copyOf(rotated));
        }
        return Map.copyOf(result);
    }

    @NotNull
    @Override
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
            @NotNull BlockState state, @NotNull ModelData modelData) {
        ModelData base = originalModel.getModelData(level, pos, state, modelData);
        Integer storedMask = modelData.get(SimplePipeModelData.EXTRACT_SIDES);
        if (storedMask == null || base.get(SimplePipeModelData.EXTRACT_SIDES) != null) {
            return base;
        }
        return base.derive().with(SimplePipeModelData.EXTRACT_SIDES, storedMask).build();
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
            @NotNull RandomSource random, @NotNull ModelData modelData, @Nullable RenderType renderType) {
        List<BakedQuad> base = originalModel.getQuads(state, side, random, modelData, renderType);
        if (state == null || side != null) {
            return base;
        }
        Integer maskValue = modelData.get(SimplePipeModelData.EXTRACT_SIDES);
        int mask = maskValue == null ? 0 : maskValue;
        if (mask == 0) {
            return base;
        }
        ArrayList<BakedQuad> combined = new ArrayList<>(base.size());
        for (BakedQuad quad : base) {
            if (!isCoveredEndpoint(quad, state, mask)) {
                combined.add(quad);
            }
        }
        for (Direction direction : Direction.values()) {
            if ((mask & (1 << direction.ordinal())) != 0
                    && state.getValue(SimplePipeBlock.connectionProperty(direction))) {
                combined.addAll(extractQuads.get(direction));
            }
        }
        return combined;
    }

    private static boolean isCoveredEndpoint(BakedQuad quad, BlockState state, int extractMask) {
        Direction direction = quad.getDirection();
        return (extractMask & (1 << direction.ordinal())) != 0
                && state.getValue(SimplePipeBlock.connectionProperty(direction))
                && isEndpointQuad(quad, direction);
    }

    private static boolean isEndpointQuad(BakedQuad quad, Direction direction) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            float boundary = direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0.0F : 1.0F;
            float axis = axisCoordinate(direction.getAxis(), x, y, z);
            if (Math.abs(axis - boundary) > 0.0001F
                    || !insideEndpoint(direction.getAxis(), x, y, z)) {
                return false;
            }
        }
        return true;
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

    private static BakedQuad rotateQuad(BakedQuad quad, Direction target) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        Direction normal = rotateDirection(quad.getDirection(), target);
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            float[] rotated = rotate(x, y, z, target);
            vertices[offset] = Float.floatToRawIntBits(rotated[0]);
            vertices[offset + 1] = Float.floatToRawIntBits(rotated[1]);
            vertices[offset + 2] = Float.floatToRawIntBits(rotated[2]);
            vertices[offset + stride - 1] = packNormal(normal);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), normal, quad.getSprite(),
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static float[] rotate(float x, float y, float z, Direction target) {
        return switch (target) {
            case NORTH -> new float[] {x, y, z};
            case SOUTH -> new float[] {1.0F - x, y, 1.0F - z};
            case WEST -> new float[] {z, y, 1.0F - x};
            case EAST -> new float[] {1.0F - z, y, x};
            case UP -> new float[] {x, 1.0F - z, y};
            case DOWN -> new float[] {x, z, 1.0F - y};
        };
    }

    private static Direction rotateDirection(Direction direction, Direction target) {
        int[] rotated = rotateVector(direction.getStepX(), direction.getStepY(), direction.getStepZ(), target);
        return Direction.getNearest(rotated[0], rotated[1], rotated[2]);
    }

    private static int[] rotateVector(int x, int y, int z, Direction target) {
        return switch (target) {
            case NORTH -> new int[] {x, y, z};
            case SOUTH -> new int[] {-x, y, -z};
            case WEST -> new int[] {z, y, -x};
            case EAST -> new int[] {-z, y, x};
            case UP -> new int[] {x, -z, y};
            case DOWN -> new int[] {x, z, -y};
        };
    }

    private static int packNormal(Direction direction) {
        int x = direction.getStepX() * 127 & 0xFF;
        int y = direction.getStepY() * 127 & 0xFF;
        int z = direction.getStepZ() * 127 & 0xFF;
        return x | y << 8 | z << 16;
    }
}
