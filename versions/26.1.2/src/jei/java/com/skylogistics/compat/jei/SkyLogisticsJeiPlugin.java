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
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public class SkyLogisticsJeiPlugin implements IModPlugin {
    static final RecipeType<OfferingRecipe> SKY_OFFERING =
            RecipeType.create(SkyLogistics.MOD_ID, "sky_offering", OfferingRecipe.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(SkyLogistics.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new SkyOfferingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }
        List<OfferingRecipe> recipes = server.getRecipeManager().getRecipes().stream()
                .map(RecipeHolder::value)
                .filter(ModRecipes.SKY_OFFERING_TYPE.get()::equals)
                .map(OfferingRecipe.class::cast)
                .toList();
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
