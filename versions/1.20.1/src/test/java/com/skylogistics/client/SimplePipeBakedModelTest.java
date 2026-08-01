package com.skylogistics.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class SimplePipeBakedModelTest {
    @Test
    @SuppressWarnings("deprecation")
    void readsAdditionalModelGeometryFromForgeTwentyVanillaOverload() {
        AtomicBoolean vanillaOverloadCalled = new AtomicBoolean();
        List<BakedQuad> expected = new ArrayList<>();
        BakedModel model = new BakedModel() {
            @Override
            public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
                vanillaOverloadCalled.set(true);
                return expected;
            }

            @Override
            public boolean useAmbientOcclusion() {
                return false;
            }

            @Override
            public boolean isGui3d() {
                return false;
            }

            @Override
            public boolean usesBlockLight() {
                return false;
            }

            @Override
            public boolean isCustomRenderer() {
                return false;
            }

            @Override
            public TextureAtlasSprite getParticleIcon() {
                return null;
            }

            @Override
            public ItemOverrides getOverrides() {
                return null;
            }
        };

        assertSame(expected, SimplePipeBakedModel.extractModelQuads(model));
        assertTrue(vanillaOverloadCalled.get());
    }
}
