package com.skylogistics.compat.mekanism;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
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
        ChemicalStack<?> stack = resolve(encodedChemical);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        try {
            MekanismRenderer.color(graphics, stack);
            GuiUtils.drawTiledSprite(graphics, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                    MekanismRenderer.getChemicalTexture(stack.getType()), ICON_SIZE, ICON_SIZE, 100,
                    GuiUtils.TilingDirection.UP_RIGHT);
        } finally {
            MekanismRenderer.resetColor(graphics);
            graphics.pose().popPose();
        }
        return true;
    }

    public static Component displayName(String encodedChemical) {
        ChemicalStack<?> stack = resolve(encodedChemical);
        return stack == null || stack.isEmpty() ? null : stack.getTextComponent();
    }

    private static ChemicalStack<?> resolve(String encodedChemical) {
        int separator = encodedChemical.indexOf(':');
        if (separator <= 0 || separator == encodedChemical.length() - 1) {
            return null;
        }
        String kind = encodedChemical.substring(0, separator);
        ResourceLocation id = ResourceLocation.tryParse(encodedChemical.substring(separator + 1));
        if (id == null) {
            return null;
        }
        return switch (kind) {
            case "gas" -> gasStack(id);
            case "infusion" -> infusionStack(id);
            case "pigment" -> pigmentStack(id);
            case "slurry" -> slurryStack(id);
            default -> null;
        };
    }

    private static ChemicalStack<?> gasStack(ResourceLocation id) {
        Gas chemical = MekanismAPI.gasRegistry().getValue(id);
        return chemical == null || chemical.isEmptyType() ? null : new GasStack(chemical, 1_000);
    }

    private static ChemicalStack<?> infusionStack(ResourceLocation id) {
        InfuseType chemical = MekanismAPI.infuseTypeRegistry().getValue(id);
        return chemical == null || chemical.isEmptyType() ? null : new InfusionStack(chemical, 1_000);
    }

    private static ChemicalStack<?> pigmentStack(ResourceLocation id) {
        Pigment chemical = MekanismAPI.pigmentRegistry().getValue(id);
        return chemical == null || chemical.isEmptyType() ? null : new PigmentStack(chemical, 1_000);
    }

    private static ChemicalStack<?> slurryStack(ResourceLocation id) {
        Slurry chemical = MekanismAPI.slurryRegistry().getValue(id);
        return chemical == null || chemical.isEmptyType() ? null : new SlurryStack(chemical, 1_000);
    }
}
