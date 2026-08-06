package com.skylogistics.client;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.util.SimplePipeGeometry;
import com.skylogistics.util.SimplePipeModelData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
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
    private final Map<BlockStateModelPart, AtomicReferenceArray<ArmFilteredPart>> filteredParts =
            new ConcurrentHashMap<>();

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
                output.set(index, filteredPart(output.get(index), connectedExtractMask));
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
        return SimplePipeGeometry.sideMask(direction);
    }

    private static int connectedExtractMask(BlockState state, int extractMask) {
        int connected = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(SimplePipeBlock.connectionProperty(direction))) {
                connected |= sideMask(direction);
            }
        }
        return SimplePipeGeometry.connectedExtractMask(connected, extractMask);
    }

    private ArmFilteredPart filteredPart(BlockStateModelPart part, int extractMask) {
        AtomicReferenceArray<ArmFilteredPart> variants =
                filteredParts.computeIfAbsent(part, ignored -> new AtomicReferenceArray<>(64));
        ArmFilteredPart cached = variants.get(extractMask);
        if (cached != null) {
            return cached;
        }
        ArmFilteredPart created = new ArmFilteredPart(part, extractMask);
        if (variants.compareAndSet(extractMask, null, created)) {
            return created;
        }
        return variants.get(extractMask);
    }

    private static boolean isCoveredArm(BakedQuad quad, int extractMask) {
        for (Direction direction : Direction.values()) {
            if ((extractMask & sideMask(direction)) != 0 && isArmQuad(quad, direction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isArmQuad(BakedQuad quad, Direction direction) {
        for (int vertex = 0; vertex < 4; vertex++) {
            float x = quad.position(vertex).x();
            float y = quad.position(vertex).y();
            float z = quad.position(vertex).z();
            if (!SimplePipeGeometry.isArmVertex(direction, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static final class ArmFilteredPart implements BlockStateModelPart {
        private final BlockStateModelPart original;
        private final int extractMask;
        private final AtomicReferenceArray<List<BakedQuad>> quads = new AtomicReferenceArray<>(7);

        private ArmFilteredPart(BlockStateModelPart original, int extractMask) {
            this.original = original;
            this.extractMask = extractMask;
        }

        @Override
        public List<BakedQuad> getQuads(Direction side) {
            int index = side == null ? 6 : side.ordinal();
            List<BakedQuad> cached = quads.get(index);
            if (cached != null) {
                return cached;
            }
            List<BakedQuad> originalQuads = original.getQuads(side);
            ArrayList<BakedQuad> filtered = new ArrayList<>(originalQuads.size());
            for (BakedQuad quad : originalQuads) {
                if (!isCoveredArm(quad, extractMask)) {
                    filtered.add(quad);
                }
            }
            List<BakedQuad> created = List.copyOf(filtered);
            if (quads.compareAndSet(index, null, created)) {
                return created;
            }
            return quads.get(index);
        }

        @Override
        public net.minecraft.util.TriState ambientOcclusion() {
            return original.ambientOcclusion();
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean useAmbientOcclusion() {
            return original.ambientOcclusion() != net.minecraft.util.TriState.FALSE;
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
