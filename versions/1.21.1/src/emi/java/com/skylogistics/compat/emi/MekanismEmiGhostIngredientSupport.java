package com.skylogistics.compat.emi;

import dev.emi.emi.api.stack.EmiStack;
import mekanism.api.MekanismAPI;
import mekanism.client.recipe_viewer.emi.ChemicalEmiStack;

/** Optional Mekanism classes are resolved only when the mod is loaded. */
final class MekanismEmiGhostIngredientSupport {
    private MekanismEmiGhostIngredientSupport() {
    }

    static String chemicalKey(EmiStack stack) {
        if (!(stack instanceof ChemicalEmiStack chemicalStack) || chemicalStack.isEmpty()) {
            return "";
        }
        var key = MekanismAPI.CHEMICAL_REGISTRY.getKey(chemicalStack.getStack().getChemical());
        return key == null ? "" : key.toString();
    }
}
