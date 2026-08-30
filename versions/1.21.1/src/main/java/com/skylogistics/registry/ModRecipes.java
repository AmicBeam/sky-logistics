package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.recipe.SkyWrenchAvailableCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, SkyLogistics.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, SkyLogistics.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, SkyLogistics.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<SkyWrenchAvailableCondition>>
            SKY_WRENCH_AVAILABLE_CONDITION = CONDITION_CODECS.register("sky_wrench_available",
                    () -> SkyWrenchAvailableCondition.CODEC);

    public static final DeferredHolder<RecipeType<?>, RecipeType<OfferingRecipe>> SKY_OFFERING_TYPE = TYPES.register("sky_offering",
            () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return SkyLogistics.MOD_ID + ":sky_offering";
                }
            });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<OfferingRecipe>> SKY_OFFERING_SERIALIZER =
            SERIALIZERS.register("sky_offering", OfferingRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus bus) {
        CONDITION_CODECS.register(bus);
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }
}
