package com.skylogistics.client;

import com.skylogistics.compat.mekanism.MekanismChemicalClientCompat;
import com.skylogistics.compat.mekanism.MekanismCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class ChemicalFilterClientCompat {
    private ChemicalFilterClientCompat() {
    }

    static boolean render(GuiGraphics graphics, String chemical, int x, int y) {
        return MekanismCompat.isLoaded() && MekanismChemicalClientCompat.render(graphics, chemical, x, y);
    }

    static Component displayName(String chemical) {
        if (MekanismCompat.isLoaded()) {
            Component name = MekanismChemicalClientCompat.displayName(chemical);
            if (name != null) {
                return name;
            }
        }
        return Component.literal(chemical);
    }
}
