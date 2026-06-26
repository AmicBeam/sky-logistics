package com.skylogistics.compat.jei;

import com.skylogistics.client.FilterListScreen;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.ModNetworking;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public class SkyFilterGhostIngredientHandler implements IGhostIngredientHandler<FilterListScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(FilterListScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        if (!gui.canAcceptGhostFilters()) {
            return List.of();
        }
        Optional<ItemStack> item = ingredient.getItemStack();
        if (item.isPresent() && !item.get().isEmpty()) {
            return itemTargets(gui);
        }
        Optional<FluidStack> fluid = ingredient.getIngredient(ForgeTypes.FLUID_STACK);
        if (fluid.isPresent() && !fluid.get().isEmpty()) {
            return fluidTargets(gui);
        }
        return List.of();
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> itemTargets(FilterListScreen gui) {
        List<Target<I>> targets = new ArrayList<>(FilterListItem.FILTER_SLOTS);
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            targets.add((Target<I>) new ItemTarget(gui, gui.getFilterSlotArea(slot), slot));
        }
        return targets;
    }

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> fluidTargets(FilterListScreen gui) {
        List<Target<I>> targets = new ArrayList<>(FilterListItem.FILTER_SLOTS);
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            targets.add((Target<I>) new FluidTarget(gui, gui.getFilterSlotArea(slot), slot));
        }
        return targets;
    }

    private record ItemTarget(FilterListScreen gui, Rect2i area, int slot) implements Target<ItemStack> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(ItemStack ingredient) {
            ItemStack ghost = ingredient.copy();
            if (!ghost.isEmpty()) {
                ghost.setCount(1);
            }
            gui.setGhostItemPreview(slot, ghost);
            ModNetworking.sendFilterGhostItem(slot, ghost);
        }
    }

    private record FluidTarget(FilterListScreen gui, Rect2i area, int slot) implements Target<FluidStack> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(FluidStack ingredient) {
            FluidStack ghost = ingredient.copy();
            if (!ghost.isEmpty()) {
                ghost.setAmount(1);
            }
            gui.setGhostFluidPreview(slot, ghost);
            ModNetworking.sendFilterGhostFluid(slot, ghost);
        }
    }
}
