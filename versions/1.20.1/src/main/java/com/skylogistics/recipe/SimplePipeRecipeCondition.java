package com.skylogistics.recipe;

import com.google.gson.JsonObject;
import com.skylogistics.SkyLogistics;
import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

public record SimplePipeRecipeCondition(boolean diamond) implements ICondition {
    private static final ResourceLocation ID = new ResourceLocation(SkyLogistics.MOD_ID, "simple_pipe_recipe");

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return SkyLogisticsConfig.useDiamondForSimplePipeRecipes() == diamond;
    }

    public static final class Serializer implements IConditionSerializer<SimplePipeRecipeCondition> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public void write(JsonObject json, SimplePipeRecipeCondition value) {
            json.addProperty("diamond", value.diamond());
        }

        @Override
        public SimplePipeRecipeCondition read(JsonObject json) {
            return new SimplePipeRecipeCondition(GsonHelper.getAsBoolean(json, "diamond"));
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
