package com.skylogistics.client;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.util.SimplePipeGeometry;
import com.skylogistics.util.SimplePipeModelData;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<QuadKey, List<BakedQuad>> combinedQuads = new ConcurrentHashMap<>();

    SimplePipeBakedModel(BakedModel originalModel, Map<Direction, List<BakedQuad>> extractQuads) {
        super(originalModel);
        this.extractQuads = extractQuads;
    }

    @SuppressWarnings("deprecation")
    static Map<Direction, List<BakedQuad>> rotatedExtractQuads(BakedModel northModel) {
        // Forge 1.20 additional standalone models expose their baked geometry through the
        // vanilla overload. The ModelData overload can return no quads here, which made the
        // normal pipe arm disappear without adding the extraction collar.
        List<BakedQuad> northQuads = extractModelQuads(northModel);
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

    @SuppressWarnings("deprecation")
    static List<BakedQuad> extractModelQuads(BakedModel model) {
        return model.getQuads(null, null, RandomSource.create(0L));
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
        if (state == null || side != null) {
            return originalModel.getQuads(state, side, random, modelData, renderType);
        }
        Integer maskValue = modelData.get(SimplePipeModelData.EXTRACT_SIDES);
        int extractMask = maskValue == null ? 0 : maskValue;
        int connectionMask = connectionMask(state);
        int connectedExtractMask = SimplePipeGeometry.connectedExtractMask(connectionMask, extractMask);
        if (connectedExtractMask == 0) {
            return originalModel.getQuads(state, side, random, modelData, renderType);
        }
        QuadKey key = new QuadKey(state, connectedExtractMask, renderType);
        return combinedQuads.computeIfAbsent(key, ignored ->
                buildCombinedQuads(state, random, modelData, renderType, connectedExtractMask));
    }

    private List<BakedQuad> buildCombinedQuads(BlockState state, RandomSource random, ModelData modelData,
            @Nullable RenderType renderType, int extractMask) {
        List<BakedQuad> base = originalModel.getQuads(state, null, random, modelData, renderType);
        ArrayList<BakedQuad> combined = new ArrayList<>(base.size());
        for (BakedQuad quad : base) {
            if (!isCoveredArm(quad, extractMask)) {
                combined.add(quad);
            }
        }
        for (Direction direction : Direction.values()) {
            if ((extractMask & SimplePipeGeometry.sideMask(direction)) != 0) {
                combined.addAll(extractQuads.get(direction));
            }
        }
        return List.copyOf(combined);
    }

    private static int connectionMask(BlockState state) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(SimplePipeBlock.connectionProperty(direction))) {
                mask |= SimplePipeGeometry.sideMask(direction);
            }
        }
        return mask;
    }

    private static boolean isCoveredArm(BakedQuad quad, int extractMask) {
        for (Direction direction : Direction.values()) {
            if ((extractMask & SimplePipeGeometry.sideMask(direction)) != 0
                    && isArmQuad(quad, direction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isArmQuad(BakedQuad quad, Direction direction) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            float x = Float.intBitsToFloat(vertices[offset]);
            float y = Float.intBitsToFloat(vertices[offset + 1]);
            float z = Float.intBitsToFloat(vertices[offset + 2]);
            if (!SimplePipeGeometry.isArmVertex(direction, x, y, z)) {
                return false;
            }
        }
        return true;
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

    private record QuadKey(BlockState state, int extractMask, @Nullable RenderType renderType) {
    }
}
