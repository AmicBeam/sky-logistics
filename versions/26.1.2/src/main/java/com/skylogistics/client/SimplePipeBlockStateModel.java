package com.skylogistics.client;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.util.SimplePipeModelData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

final class SimplePipeBlockStateModel implements BlockStateModel {
    private final BlockStateModel original;
    private final Map<Direction, BlockStateModelPart> extractParts;

    SimplePipeBlockStateModel(BlockStateModel original, Map<Direction, BlockStateModelPart> extractParts) {
        this.original = original;
        this.extractParts = extractParts;
    }

    @Override
    @Deprecated
    public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
        original.collectParts(random, output);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
            RandomSource random, List<BlockStateModelPart> output) {
        int firstOriginalPart = output.size();
        original.collectParts(level, pos, state, random, output);
        int extractMask = extractMask(level, pos);
        int connectedExtractMask = connectedExtractMask(state, extractMask);
        if (connectedExtractMask != 0) {
            for (int index = firstOriginalPart; index < output.size(); index++) {
                output.set(index, new EndpointFilteredPart(output.get(index), connectedExtractMask));
            }
        }
        for (Direction direction : Direction.values()) {
            if ((extractMask & sideMask(direction)) != 0
                    && state.getValue(SimplePipeBlock.connectionProperty(direction))) {
                BlockStateModelPart part = extractParts.get(direction);
                if (part != null) {
                    output.add(part);
                }
            }
        }
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
            RandomSource random) {
        int connectedExtractMask = connectedExtractMask(state, extractMask(level, pos));
        Object originalKey = original.createGeometryKey(level, pos, state, random);
        return new GeometryKey(originalKey == null ? original : originalKey, connectedExtractMask);
    }

    @Override
    @Deprecated
    public Material.Baked particleMaterial() {
        return original.particleMaterial();
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return original.particleMaterial(level, pos, state);
    }

    @Override
    @Deprecated
    @BakedQuad.MaterialFlags
    public int materialFlags() {
        return original.materialFlags();
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return original.materialFlags(level, pos, state);
    }

    private static int extractMask(BlockAndTintGetter level, BlockPos pos) {
        Integer mask = level.getModelData(pos).get(SimplePipeModelData.EXTRACT_SIDES);
        return mask == null ? 0 : mask & 0x3F;
    }

    private static int sideMask(Direction direction) {
        return 1 << direction.ordinal();
    }

    private static int connectedExtractMask(BlockState state, int extractMask) {
        int connected = extractMask;
        for (Direction direction : Direction.values()) {
            if (!state.getValue(SimplePipeBlock.connectionProperty(direction))) {
                connected &= ~sideMask(direction);
            }
        }
        return connected;
    }

    private static boolean isEndpointQuad(BakedQuad quad, int extractMask) {
        Direction direction = quad.direction();
        if ((extractMask & sideMask(direction)) == 0) {
            return false;
        }
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.position(vertex).x();
            float y = quad.position(vertex).y();
            float z = quad.position(vertex).z();
            float boundary = direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? 0.0F : 1.0F;
            float coordinate = axisCoordinate(direction.getAxis(), x, y, z);
            if (Math.abs(coordinate - boundary) > 0.0001F
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

    private record EndpointFilteredPart(BlockStateModelPart original, int extractMask)
            implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(Direction side) {
            List<BakedQuad> quads = original.getQuads(side);
            ArrayList<BakedQuad> filtered = new ArrayList<>(quads.size());
            for (BakedQuad quad : quads) {
                if (!isEndpointQuad(quad, extractMask)) {
                    filtered.add(quad);
                }
            }
            return filtered;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return original.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return original.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return original.materialFlags();
        }
    }

    private record GeometryKey(Object original, int extractMask) {
    }
}
