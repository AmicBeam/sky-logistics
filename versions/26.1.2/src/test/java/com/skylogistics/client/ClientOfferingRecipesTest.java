package com.skylogistics.client;

import com.skylogistics.recipe.OfferingRecipe;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientOfferingRecipesTest
{
    @AfterEach
    void resetCache()
    {
        ClientOfferingRecipes.setChangeListener(null);
        ClientOfferingRecipes.clear();
    }

    @Test
    void applyAndClearNotifyRuntimeListener()
    {
        AtomicInteger changes = new AtomicInteger();
        ClientOfferingRecipes.setChangeListener(changes::incrementAndGet);
        List<RecipeHolder<OfferingRecipe>> recipes = List.of(recipe("test_offering"));

        ClientOfferingRecipes.apply(recipes);
        assertEquals(recipes, ClientOfferingRecipes.recipes());
        assertEquals(1, changes.get());

        ClientOfferingRecipes.clear();
        assertTrue(ClientOfferingRecipes.recipes().isEmpty());
        assertEquals(2, changes.get());
    }

    @Test
    void removingListenerStopsRuntimeCallbacks()
    {
        AtomicInteger changes = new AtomicInteger();
        ClientOfferingRecipes.setChangeListener(changes::incrementAndGet);
        ClientOfferingRecipes.setChangeListener(null);

        ClientOfferingRecipes.apply(List.of(recipe("replacement_offering")));

        assertEquals(0, changes.get());
    }

    private static RecipeHolder<OfferingRecipe> recipe(String path)
    {
        OfferingRecipe.CountedIngredient main = new OfferingRecipe.CountedIngredient(
                Ingredient.of(Items.STONE), 1, false);
        OfferingRecipe recipe = new OfferingRecipe(main, List.of(), new ItemStackTemplate(Items.DIAMOND), 20, 1);
        ResourceKey<Recipe<?>> id = ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("skylogistics", path));
        return new RecipeHolder<>(id, recipe);
    }
}
