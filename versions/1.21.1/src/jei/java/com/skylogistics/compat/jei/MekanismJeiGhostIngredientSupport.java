package com.skylogistics.compat.jei;

import com.skylogistics.client.FilterListScreen;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.ModNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.recipe_viewer.jei.MekanismJEI;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;

/** Mekanism-only JEI references, loaded only after the optional mod is confirmed present. */
final class MekanismJeiGhostIngredientSupport {
    private MekanismJeiGhostIngredientSupport() {
    }

    static <I> List<Target<I>> getTargetsTyped(FilterListScreen gui, ITypedIngredient<I> ingredient) {
        Optional<ChemicalStack> chemical = ingredient.getIngredient(MekanismJEI.TYPE_CHEMICAL);
        if (chemical.isPresent() && !chemical.get().isEmpty()) {
            return chemicalTargets(gui);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> chemicalTargets(FilterListScreen gui) {
        List<Target<I>> targets = new ArrayList<>(FilterListItem.FILTER_SLOTS);
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            targets.add((Target<I>) new ChemicalTarget(gui, gui.getFilterSlotArea(slot), slot));
        }
        return targets;
    }

    private record ChemicalTarget(FilterListScreen gui, Rect2i area, int slot) implements Target<ChemicalStack> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(ChemicalStack ingredient) {
            String key = String.valueOf(MekanismAPI.CHEMICAL_REGISTRY.getKey(ingredient.getChemical()));
            gui.setGhostChemicalPreview(slot, key);
            ModNetworking.sendChemicalFilter(slot, key);
        }
    }
}
