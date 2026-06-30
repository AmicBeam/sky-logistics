package com.skylogistics.compat.jei;

import com.skylogistics.SkyLogistics;
import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModRecipes;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class SkyLogisticsJeiPlugin implements IModPlugin {
    static final RecipeType<OfferingRecipe> SKY_OFFERING =
            RecipeType.create(SkyLogistics.MOD_ID, "sky_offering", OfferingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(SkyLogistics.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new SkyOfferingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<OfferingRecipe> recipes = minecraft.level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.SKY_OFFERING_TYPE.get());
        registration.addRecipes(SKY_OFFERING, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModItems.OFFERING_ALTAR.get(), SKY_OFFERING);
        registration.addRecipeCatalyst(ModItems.OFFERING_TABLE.get(), SKY_OFFERING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(com.skylogistics.client.FilterListScreen.class,
                new SkyFilterGhostIngredientHandler());
        registration.addGhostIngredientHandler(com.skylogistics.client.TagFilterListScreen.class,
                new SkyTagFilterGhostIngredientHandler());
    }
}
