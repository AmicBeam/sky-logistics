package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.recipe.OfferingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SkyLogistics.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, SkyLogistics.MOD_ID);

    public static final RegistryObject<RecipeType<OfferingRecipe>> SKY_OFFERING_TYPE = TYPES.register("sky_offering",
            () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return SkyLogistics.MOD_ID + ":sky_offering";
                }
            });

    public static final RegistryObject<RecipeSerializer<OfferingRecipe>> SKY_OFFERING_SERIALIZER =
            SERIALIZERS.register("sky_offering", OfferingRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }
}
