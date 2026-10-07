package com.skylogistics.compat.emi;

import com.skylogistics.client.FilterListScreen;
import com.skylogistics.client.TagFilterListScreen;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.ModNetworking;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

@EmiEntrypoint
public final class SkyLogisticsEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(FilterListScreen.class, new FilterDragDropHandler());
        registry.addDragDropHandler(TagFilterListScreen.class, new TagFilterDragDropHandler());
    }

    private static EmiStack firstStack(EmiIngredient ingredient) {
        return ingredient == null || ingredient.isEmpty() || ingredient.getEmiStacks().isEmpty()
                ? EmiStack.EMPTY : ingredient.getEmiStacks().get(0);
    }

    private static String chemicalKey(EmiStack stack) {
        return MekanismCompat.isLoaded() ? MekanismEmiGhostIngredientSupport.chemicalKey(stack) : "";
    }

    private static boolean contains(Rect2i area, int x, int y) {
        return x >= area.getX() && x < area.getX() + area.getWidth()
                && y >= area.getY() && y < area.getY() + area.getHeight();
    }

    private static void highlight(GuiGraphics graphics, Rect2i area) {
        graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(),
                area.getY() + area.getHeight(), 0x8822BB33);
    }

    private static final class FilterDragDropHandler implements EmiDragDropHandler<FilterListScreen> {
        @Override
        public boolean dropStack(FilterListScreen screen, EmiIngredient ingredient, int mouseX, int mouseY) {
            if (!screen.canAcceptGhostFilters()) {
                return false;
            }
            EmiStack stack = firstStack(ingredient);
            ItemStack item = stack.getItemStack().copy();
            String chemical = chemicalKey(stack);
            for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
                if (!contains(screen.getFilterSlotArea(slot), mouseX, mouseY)) {
                    continue;
                }
                if (!chemical.isEmpty()) {
                    screen.setGhostChemicalPreview(slot, chemical);
                    ModNetworking.sendChemicalFilter(slot, chemical);
                } else if (stack.getKey() instanceof Fluid type) {
                    FluidStack fluid = new FluidStack(type, 1);
                    fluid.applyComponents(stack.getComponentChanges());
                    if (fluid.isEmpty()) {
                        return false;
                    }
                    screen.setGhostFluidPreview(slot, fluid);
                    ModNetworking.sendFilterGhostFluid(slot, fluid);
                } else if (!item.isEmpty()) {
                    item.setCount(1);
                    screen.setGhostItemPreview(slot, item);
                    ModNetworking.sendFilterGhostItem(slot, item);
                } else {
                    return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public void render(FilterListScreen screen, EmiIngredient ingredient, GuiGraphics graphics,
                int mouseX, int mouseY, float delta) {
            EmiStack stack = firstStack(ingredient);
            if (!screen.canAcceptGhostFilters() || stack.isEmpty()
                    || (stack.getItemStack().isEmpty() && !(stack.getKey() instanceof Fluid)
                    && chemicalKey(stack).isEmpty())) {
                return;
            }
            for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
                highlight(graphics, screen.getFilterSlotArea(slot));
            }
        }
    }

    private static final class TagFilterDragDropHandler implements EmiDragDropHandler<TagFilterListScreen> {
        @Override
        public boolean dropStack(TagFilterListScreen screen, EmiIngredient ingredient, int mouseX, int mouseY) {
            ItemStack item = firstStack(ingredient).getItemStack().copy();
            if (item.isEmpty() || !contains(screen.getSampleSlotArea(), mouseX, mouseY)) {
                return false;
            }
            item.setCount(1);
            screen.setGhostSamplePreview(item);
            ModNetworking.sendFilterGhostItem(0, item);
            return true;
        }

        @Override
        public void render(TagFilterListScreen screen, EmiIngredient ingredient, GuiGraphics graphics,
                int mouseX, int mouseY, float delta) {
            if (!firstStack(ingredient).getItemStack().isEmpty()) {
                highlight(graphics, screen.getSampleSlotArea());
            }
        }
    }
}
