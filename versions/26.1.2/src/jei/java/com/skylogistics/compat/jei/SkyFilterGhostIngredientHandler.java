package com.skylogistics.compat.jei;

import com.skylogistics.client.FilterListScreen;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.network.ModNetworking;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
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
        Optional<FluidStack> fluid = fluidStack(ingredient);
        if (fluid.isPresent() && !fluid.get().isEmpty()) {
            return fluidTargets(gui);
        }
        return List.of();
    }

    private static Optional<FluidStack> fluidStack(ITypedIngredient<?> ingredient) {
        Object value = ingredient.getIngredient();
        try {
            Method fluidVariantMethod = value.getClass().getMethod("getFluidVariant");
            Object fluidVariant = fluidVariantMethod.invoke(value);
            Method fluidMethod = fluidVariant.getClass().getMethod("getFluid");
            Object fluid = fluidMethod.invoke(fluidVariant);
            if (!(fluid instanceof Fluid actualFluid)) {
                return Optional.empty();
            }
            return Optional.of(new FluidStack(actualFluid, 1));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
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
