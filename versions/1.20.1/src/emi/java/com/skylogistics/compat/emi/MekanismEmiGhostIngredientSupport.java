package com.skylogistics.compat.emi;

import dev.emi.emi.api.stack.EmiStack;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.slurry.Slurry;

/** Recognizes chemical stacks supplied by optional EMI addons. */
final class MekanismEmiGhostIngredientSupport {
    private MekanismEmiGhostIngredientSupport() {
    }

    static String chemicalKey(EmiStack stack) {
        Object key = stack.getKey();
        if (!(key instanceof Chemical<?> chemical) || chemical.isEmptyType()) {
            return "";
        }
        String kind = key instanceof Gas ? "gas" : key instanceof InfuseType ? "infusion"
                : key instanceof Pigment ? "pigment" : key instanceof Slurry ? "slurry" : "";
        return kind.isEmpty() || chemical.getRegistryName() == null
                ? "" : kind + ":" + chemical.getRegistryName();
    }
}
