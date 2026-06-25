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
import net.minecraftforge.fluids.FluidStack;

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

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> itemTargets(FilterListScreen gui) {
        List<Target<I>> targets = new ArrayList<>(FilterListItem.FILTER_SLOTS);
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            targets.add((Target<I>) new ItemTarget(gui.getFilterSlotArea(slot), slot));
        }
        return targets;
    }

    @SuppressWarnings("unchecked")
    private static <I> List<Target<I>> fluidTargets(FilterListScreen gui) {
        List<Target<I>> targets = new ArrayList<>(FilterListItem.FILTER_SLOTS);
        for (int slot = 0; slot < FilterListItem.FILTER_SLOTS; slot++) {
            targets.add((Target<I>) new FluidTarget(gui.getFilterSlotArea(slot), slot));
        }
        return targets;
    }

    private record ItemTarget(Rect2i area, int slot) implements Target<ItemStack> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(ItemStack ingredient) {
            ModNetworking.sendFilterGhostItem(slot, ingredient);
        }
    }

    private record FluidTarget(Rect2i area, int slot) implements Target<FluidStack> {
        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(FluidStack ingredient) {
            ModNetworking.sendFilterGhostFluid(slot, ingredient);
        }
    }
}
