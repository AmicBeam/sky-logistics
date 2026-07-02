package com.skylogistics.compat.jei;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientOfferingRecipes;
import com.skylogistics.compat.ae2.AppliedEnergisticsCompat;
import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import com.skylogistics.compat.refinedstorage.RefinedStorageCompat;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public class SkyLogisticsJeiPlugin implements IModPlugin {
    private static final Supplier<IRecipeHolderType<OfferingRecipe>> SKY_OFFERING =
            IRecipeHolderType.createDeferred(ModRecipes.SKY_OFFERING_TYPE);
    private static IJeiRuntime runtime;
    private static List<RecipeHolder<OfferingRecipe>> registeredRuntimeRecipes = List.of();

    static IRecipeHolderType<OfferingRecipe> skyOfferingType() {
        return SKY_OFFERING.get();
    }

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
        List<RecipeHolder<OfferingRecipe>> recipes = ClientOfferingRecipes.recipes();
        registeredRuntimeRecipes = recipes;
        SkyLogistics.LOGGER.info("Registering {} sky offering recipes with JEI.", recipes.size());
        registration.addRecipes(skyOfferingType(), recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(skyOfferingType(), ModItems.OFFERING_ALTAR.get(),
                ModItems.OFFERING_TABLE.get());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(com.skylogistics.client.FilterListScreen.class,
                new SkyFilterGhostIngredientHandler());
        registration.addGhostIngredientHandler(com.skylogistics.client.TagFilterListScreen.class,
                new SkyTagFilterGhostIngredientHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        ClientOfferingRecipes.setChangeListener(SkyLogisticsJeiPlugin::syncRecipesAtRuntime);
        syncRecipesAtRuntime();
        ModNetworking.requestSkyOfferingRecipes();
        hideMissingIntegrationItems(jeiRuntime.getIngredientManager());
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        registeredRuntimeRecipes = List.of();
        ClientOfferingRecipes.setChangeListener(null);
    }

    private static void syncRecipesAtRuntime() {
        if (runtime == null) {
            return;
        }
        List<RecipeHolder<OfferingRecipe>> recipes = ClientOfferingRecipes.recipes();
        if (recipes.equals(registeredRuntimeRecipes)) {
            return;
        }
        if (!registeredRuntimeRecipes.isEmpty()) {
            runtime.getRecipeManager().hideRecipes(skyOfferingType(), registeredRuntimeRecipes);
        }
        if (!recipes.isEmpty()) {
            SkyLogistics.LOGGER.info("Adding {} synced sky offering recipes to active JEI runtime.", recipes.size());
            runtime.getRecipeManager().addRecipes(skyOfferingType(), recipes);
        }
        registeredRuntimeRecipes = recipes;
    }

    private static void hideMissingIntegrationItems(IIngredientManager ingredientManager) {
        List<ItemStack> hidden = new ArrayList<>();
        if (!AppliedEnergisticsCompat.isLoaded()) {
            hidden.add(ModItems.SKY_ME_INTERFACE.get().getDefaultInstance());
        }
        if (!RefinedStorageCompat.isLoaded()) {
            hidden.add(ModItems.SKY_RS_INTERFACE.get().getDefaultInstance());
        }
        if (!BeyondDimensionsCompat.isLoaded()) {
            hidden.add(ModItems.SKY_DIMENSION_INTERFACE.get().getDefaultInstance());
        }
        if (!hidden.isEmpty()) {
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }
}
