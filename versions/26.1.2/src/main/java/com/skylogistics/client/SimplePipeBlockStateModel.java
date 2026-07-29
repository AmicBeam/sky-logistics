package com.skylogistics.client;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.util.SimplePipeModelData;
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
        original.collectParts(level, pos, state, random, output);
        int extractMask = extractMask(level, pos);
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
        int connectedExtractMask = extractMask(level, pos);
        for (Direction direction : Direction.values()) {
            if (!state.getValue(SimplePipeBlock.connectionProperty(direction))) {
                connectedExtractMask &= ~sideMask(direction);
            }
        }
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

    private record GeometryKey(Object original, int extractMask) {
    }
}
