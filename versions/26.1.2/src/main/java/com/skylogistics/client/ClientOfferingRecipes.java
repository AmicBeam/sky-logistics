package com.skylogistics.client;

import com.skylogistics.recipe.OfferingRecipe;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class ClientOfferingRecipes {
    private static final Runnable NOOP = () -> {
    };
    private static List<RecipeHolder<OfferingRecipe>> recipes = List.of();
    private static Runnable changeListener = NOOP;

    private ClientOfferingRecipes() {
    }

    public static void apply(List<RecipeHolder<OfferingRecipe>> syncedRecipes) {
        recipes = List.copyOf(syncedRecipes);
        changeListener.run();
    }

    public static void clear() {
        apply(List.of());
    }

    public static List<RecipeHolder<OfferingRecipe>> recipes() {
        return recipes;
    }

    public static void setChangeListener(Runnable listener) {
        changeListener = listener == null ? NOOP : listener;
    }
}
