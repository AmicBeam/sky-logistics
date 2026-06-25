package com.skylogistics.compat.jei;

import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SkyOfferingCategory implements IRecipeCategory<OfferingRecipe> {
    private static final int WIDTH = 128;
    private static final int HEIGHT = 70;
    private final IDrawable background;
    private final IDrawable icon;

    public SkyOfferingCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemLike(ModItems.OFFERING_ALTAR.get());
    }

    @Override
    public RecipeType<OfferingRecipe> getRecipeType() {
        return SkyLogisticsJeiPlugin.SKY_OFFERING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.skylogistics.sky_offering");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, OfferingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 54, 21)
                .setStandardSlotBackground()
                .addIngredients(VanillaTypes.ITEM_STACK, displayStacks(recipe.main()));

        int[][] offeringSlots = {
                {54, 1},
                {28, 21},
                {80, 21},
                {54, 41}
        };
        for (int i = 0; i < recipe.offerings().size(); i++) {
            builder.addInputSlot(offeringSlots[i][0], offeringSlots[i][1])
                    .setStandardSlotBackground()
                    .addIngredients(VanillaTypes.ITEM_STACK, displayStacks(recipe.offerings().get(i)));
        }

        builder.addOutputSlot(105, 21)
                .setOutputSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(OfferingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX,
            double mouseY) {
        guiGraphics.fill(0, 0, WIDTH, HEIGHT, 0x44071124);
        guiGraphics.fill(17, 10, 95, 58, 0x3300D5FF);
        guiGraphics.fill(19, 12, 93, 56, 0x331D5D99);
        guiGraphics.fill(61, 9, 66, 57, 0xAA9BE8FF);
        guiGraphics.fill(38, 33, 89, 38, 0xAA9BE8FF);
        guiGraphics.fill(88, 29, 102, 42, 0xAAE8D27A);
        guiGraphics.fill(102, 33, 104, 38, 0xAAE8D27A);
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.skylogistics.sky_offering.duration", recipe.duration()), 6, 60,
                0xFFBDEBFF, false);
    }

    private static List<ItemStack> displayStacks(OfferingRecipe.CountedIngredient ingredient) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : ingredient.ingredient().getItems()) {
            ItemStack copy = stack.copy();
            copy.setCount(ingredient.count());
            if (ingredient.requireChargedCrystal() && copy.is(ModItems.EULOGIA_CRYSTAL.get())) {
                copy.setDamageValue(1);
            }
            stacks.add(copy);
        }
        return stacks;
    }
}
