package com.skylogistics.compat.emi;

import com.skylogistics.client.FilterListScreen;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.ModNetworking;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import mekanism.api.MekanismAPI;
import mekanism.client.recipe_viewer.emi.ChemicalEmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

@EmiEntrypoint
public final class SkyLogisticsEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        if (ModList.get().isLoaded("mekanism")) {
            registry.addDragDropHandler(FilterListScreen.class, new ChemicalFilterDragDropHandler());
        }
    }

    private static final class ChemicalFilterDragDropHandler implements EmiDragDropHandler<FilterListScreen> {
        @Override
        public boolean dropStack(FilterListScreen screen, EmiIngredient ingredient, int mouseX, int mouseY) {
            String chemical = chemicalKey(ingredient);
            if (chemical.isEmpty()) {
                return false;
            }
            for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
                if (screen.getFilterSlotArea(slot).contains(mouseX, mouseY)) {
                    screen.setGhostChemicalPreview(slot, chemical);
                    ModNetworking.sendChemicalFilter(slot, chemical);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void render(FilterListScreen screen, EmiIngredient ingredient, GuiGraphics graphics,
                int mouseX, int mouseY, float delta) {
            if (chemicalKey(ingredient).isEmpty()) {
                return;
            }
            for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
                var area = screen.getFilterSlotArea(slot);
                graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(),
                        area.getY() + area.getHeight(), 0x8822BB33);
            }
        }

        private static String chemicalKey(EmiIngredient ingredient) {
            if (ingredient == null || ingredient.isEmpty() || ingredient.getEmiStacks().isEmpty()) {
                return "";
            }
            EmiStack stack = ingredient.getEmiStacks().getFirst();
            if (!(stack instanceof ChemicalEmiStack chemicalStack) || chemicalStack.isEmpty()) {
                return "";
            }
            ResourceLocation key = MekanismAPI.CHEMICAL_REGISTRY.getKey(chemicalStack.getStack().getChemical());
            return key == null ? "" : key.toString();
        }
    }
}
