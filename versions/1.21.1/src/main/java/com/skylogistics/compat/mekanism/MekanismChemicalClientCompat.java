package com.skylogistics.compat.mekanism;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.gui.GuiUtils;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client-only Mekanism rendering, isolated so the optional mod is never linked when absent. */
public final class MekanismChemicalClientCompat {
    private static final int ICON_SIZE = 16;

    private MekanismChemicalClientCompat() {
    }

    public static boolean render(GuiGraphics graphics, String encodedChemical, int x, int y) {
        ChemicalStack stack = resolve(encodedChemical);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        try {
            MekanismRenderer.color(graphics, stack);
            GuiUtils.drawTiledSprite(graphics, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                    MekanismRenderer.getChemicalTexture(stack), ICON_SIZE, ICON_SIZE, 100,
                    GuiUtils.TilingDirection.UP_RIGHT);
        } finally {
            MekanismRenderer.resetColor(graphics);
            graphics.pose().popPose();
        }
        return true;
    }

    public static Component displayName(String encodedChemical) {
        ChemicalStack stack = resolve(encodedChemical);
        return stack == null || stack.isEmpty() ? null : stack.getTextComponent();
    }

    private static ChemicalStack resolve(String encodedChemical) {
        ResourceLocation id = ResourceLocation.tryParse(encodedChemical);
        if (id == null) {
            return null;
        }
        var chemical = MekanismAPI.CHEMICAL_REGISTRY.getHolder(id).orElse(null);
        return chemical == null || chemical.is(MekanismAPI.EMPTY_CHEMICAL_KEY)
                ? null
                : new ChemicalStack(chemical, 1_000);
    }
}
