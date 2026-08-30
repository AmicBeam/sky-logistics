package com.skylogistics.recipe;

import com.google.gson.JsonObject;
import com.skylogistics.SkyLogistics;
import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public final class SkyWrenchAvailableCondition implements ICondition {
    public static final SkyWrenchAvailableCondition INSTANCE = new SkyWrenchAvailableCondition();
    public static final Serializer SERIALIZER = new Serializer();
    private static final ResourceLocation ID = new ResourceLocation(SkyLogistics.MOD_ID, "sky_wrench_available");

    private SkyWrenchAvailableCondition() {
    }

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return SkyLogisticsConfig.skyWrenchAvailable();
    }

    public static final class Serializer implements IConditionSerializer<SkyWrenchAvailableCondition> {
        @Override
        public void write(JsonObject json, SkyWrenchAvailableCondition value) {
        }

        @Override
        public SkyWrenchAvailableCondition read(JsonObject json) {
            return INSTANCE;
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
