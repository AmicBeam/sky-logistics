package com.skylogistics.recipe;

import com.mojang.serialization.MapCodec;
import com.skylogistics.config.SkyLogisticsConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

public final class SkyWrenchAvailableCondition implements ICondition {
    public static final SkyWrenchAvailableCondition INSTANCE = new SkyWrenchAvailableCondition();
    public static final MapCodec<SkyWrenchAvailableCondition> CODEC = MapCodec.unit(INSTANCE).stable();

    private SkyWrenchAvailableCondition() {
    }

    @Override
    public boolean test(IContext context) {
        return SkyLogisticsConfig.skyWrenchAvailable();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
