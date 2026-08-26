package com.skylogistics.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skylogistics.config.SkyLogisticsConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

public record SimplePipeRecipeCondition(boolean diamond) implements ICondition {
    public static final MapCodec<SimplePipeRecipeCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.BOOL.fieldOf("diamond").forGetter(SimplePipeRecipeCondition::diamond))
            .apply(instance, SimplePipeRecipeCondition::new));

    @Override
    public boolean test(IContext context) {
        return SkyLogisticsConfig.useDiamondForSimplePipeRecipes() == diamond;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
