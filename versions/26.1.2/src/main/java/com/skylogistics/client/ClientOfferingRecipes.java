package com.skylogistics.client;

import com.skylogistics.recipe.OfferingRecipe;
import java.util.List;

public final class ClientOfferingRecipes {
    private static final Runnable NOOP = () -> {
    };
    private static List<OfferingRecipe> recipes = List.of();
    private static Runnable changeListener = NOOP;

    private ClientOfferingRecipes() {
    }

    public static void apply(List<OfferingRecipe> syncedRecipes) {
        recipes = List.copyOf(syncedRecipes);
        changeListener.run();
    }

    public static void clear() {
        apply(List.of());
    }

    public static List<OfferingRecipe> recipes() {
        return recipes;
    }

    public static void setChangeListener(Runnable listener) {
        changeListener = listener == null ? NOOP : listener;
    }
}
