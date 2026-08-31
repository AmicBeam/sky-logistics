package com.skylogistics.compat.jei;

import com.skylogistics.client.FilterListScreen;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.ModNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.jei.MekanismJEI;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler.Target;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;

/** Mekanism-only JEI references, loaded only after the optional mod is confirmed present. */
final class MekanismJeiGhostIngredientSupport {
    private MekanismJeiGhostIngredientSupport() {
    }

    static <I> List<Target<I>> getTargetsTyped(FilterListScreen gui, ITypedIngredient<I> ingredient) {
        Optional<?> chemical = ingredient.getIngredient(MekanismJEI.TYPE_GAS);
        String kind = "gas";
        if (chemical.isEmpty()) {
            chemical = ingredient.getIngredient(MekanismJEI.TYPE_INFUSION);
            kind = "infusion";
        }
        if (chemical.isEmpty()) {
            chemical = ingredient.getIngredient(MekanismJEI.TYPE_PIGMENT);
            kind = "pigment";
        }
        if (chemical.isEmpty()) {
            chemical = ingredient.getIngredient(MekanismJEI.TYPE_SLURRY);
            kind = "slurry";
        }
        if (chemical.isPresent() && chemical.get() instanceof ChemicalStack<?> stack && !stack.isEmpty()) {
            return chemicalTargets(gui, kind);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> chemicalTargets(FilterListScreen gui, String kind) {
        List<Target<I>> targets = new ArrayList<>(FilterListItem.FILTER_SLOTS);
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            targets.add((Target<I>) new ChemicalTarget(gui, gui.getFilterSlotArea(slot), slot, kind));
        }
        return targets;
    }

    private record ChemicalTarget(FilterListScreen gui, Rect2i area, int slot, String kind)
            implements Target<ChemicalStack<?>> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(ChemicalStack<?> ingredient) {
            String chemical = kind + ":" + ingredient.getTypeRegistryName();
            gui.setGhostChemicalPreview(slot, chemical);
            ModNetworking.sendChemicalFilter(slot, chemical);
        }
    }
}
