package com.skylogistics.compat.jei;

import com.skylogistics.client.TagFilterListScreen;
import com.skylogistics.network.ModNetworking;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

public class SkyTagFilterGhostIngredientHandler implements IGhostIngredientHandler<TagFilterListScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(TagFilterListScreen gui, ITypedIngredient<I> ingredient,
            boolean doStart) {
        Optional<ItemStack> item = ingredient.getItemStack();
        if (item.isEmpty() || item.get().isEmpty()) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        Target<I> target = (Target<I>) new SampleTarget(gui, gui.getSampleSlotArea());
        return List.of(target);
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    private record SampleTarget(TagFilterListScreen gui, Rect2i area) implements Target<ItemStack> {
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
            gui.setGhostSamplePreview(ghost);
            ModNetworking.sendFilterGhostItem(0, ghost);
        }
    }
}
