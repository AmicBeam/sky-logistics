package com.skylogistics.compat.jei;

import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.item.EulogiaCrystalItem;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SkyOfferingCategory implements IRecipeCategory<OfferingRecipe> {
    private static final int WIDTH = 136;
    private static final int HEIGHT = 64;
    private static final int MAIN_X = 58;
    private static final int MAIN_Y = 23;
    private static final int TOP_X = 58;
    private static final int TOP_Y = 3;
    private static final int LEFT_X = 32;
    private static final int LEFT_Y = 23;
    private static final int RIGHT_X = 84;
    private static final int RIGHT_Y = 23;
    private static final int BOTTOM_X = 58;
    private static final int BOTTOM_Y = 43;
    private static final int OUTPUT_X = 112;
    private static final int OUTPUT_Y = 23;
    private static final int ITEM_OFFSET = 1;
    private static final int PANEL = 0xFF9CA2A4;
    private static final int PANEL_DARK = 0xFF71797C;
    private static final int RITUAL_FILL = 0x3357C4D6;
    private static final int CELESTIAL = 0xFFBDEFF5;
    private static final int CELESTIAL_DARK = 0xFF667E84;
    private static final int GOLD = 0xFFFFE6A6;
    private static final int MUTED = 0xFFE0E5E7;
    private static final int INPUT_BORDER = 0xFFC8D0D2;
    private final IDrawable icon;

    public SkyOfferingCategory(IGuiHelper guiHelper) {
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
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, OfferingRecipe recipe, IFocusGroup focuses) {
        addInputSlot(builder, MAIN_X, MAIN_Y, recipe.main());

        int offeringCount = recipe.offerings().size();
        for (int i = 0; i < offeringCount; i++) {
            addInputSlot(builder, offeringX(i, offeringCount), offeringY(i, offeringCount),
                    recipe.offerings().get(i));
        }

        builder.addOutputSlot(OUTPUT_X + ITEM_OFFSET, OUTPUT_Y + ITEM_OFFSET)
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(OfferingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphicsExtractor guiGraphics, double mouseX,
            double mouseY) {
        drawPanel(guiGraphics);
        drawRitual(guiGraphics, recipe.offerings().size());
        drawInputSlot(guiGraphics, MAIN_X, MAIN_Y);
        int offeringCount = recipe.offerings().size();
        for (int i = 0; i < offeringCount; i++) {
            drawOfferingSlot(guiGraphics, offeringX(i, offeringCount), offeringY(i, offeringCount));
        }
        drawSlot(guiGraphics, OUTPUT_X, OUTPUT_Y, true, GOLD, false);

        var font = Minecraft.getInstance().font;
        guiGraphics.text(font, Component.literal(recipe.duration() + " TICK"), 9, 9,
                0xFFEAFBFF, false);
        Component tierLabel = Component.literal("T" + recipe.requiredTier());
        guiGraphics.text(font, tierLabel, 102 - font.width(tierLabel), 9,
                0xFFEAFBFF, false);
    }

    private static void drawPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, WIDTH, HEIGHT, 0x33000000);
        graphics.fill(1, 1, WIDTH - 1, HEIGHT - 1, 0xFF4F575A);
        graphics.fill(2, 2, WIDTH - 2, HEIGHT - 2, PANEL_DARK);
        graphics.fill(3, 3, WIDTH - 3, HEIGHT - 3, PANEL);
        drawFrame(graphics, 2, 2, WIDTH - 4, HEIGHT - 4, 0xFF5F686B, 0xFFD8DEE0);
        graphics.fill(5, 5, WIDTH - 5, 6, 0x66E9EEF0);
        graphics.fill(5, HEIGHT - 6, WIDTH - 5, HEIGHT - 5, 0x22515A5D);
    }

    private static void drawRitual(GuiGraphicsExtractor graphics, int offeringCount) {
        int centerX = MAIN_X + 9;
        int centerY = MAIN_Y + 9;
        graphics.fill(7, 6, 108, 58, 0x667D8588);
        graphics.fill(9, 8, 106, 56, 0x5571777A);
        drawFrame(graphics, 7, 6, 101, 52, 0xFF667074, 0xFFB8C1C4);
        graphics.fill(8, 8, 52, 19, 0x3371797C);
        drawDiamond(graphics, centerX, centerY, 25, RITUAL_FILL);
        drawDiamond(graphics, centerX, centerY, 15, 0x228FE9F2);
        for (int i = 0; i < offeringCount; i++) {
            drawConnection(graphics, centerX, centerY,
                    offeringX(i, offeringCount) + 9, offeringY(i, offeringCount) + 9);
        }
        drawGlowLineH(graphics, centerX + 9, OUTPUT_X, centerY, 0xCCF7E4A2);
        drawRunes(graphics);

        graphics.fill(104, 29, 113, 36, 0x88E9D78E);
        graphics.fill(108, 17, 133, 48, 0x44766E55);
        drawFrame(graphics, 108, 17, 25, 31, 0xFF756D57, 0xCCF7E4A2);
    }

    private static void drawOfferingSlot(GuiGraphicsExtractor graphics, int x, int y) {
        drawInputSlot(graphics, x, y);
    }

    private static void drawInputSlot(GuiGraphicsExtractor graphics, int x, int y) {
        drawSlot(graphics, x, y, true, INPUT_BORDER, false);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, boolean active, int accent) {
        drawSlot(graphics, x, y, active, accent, true);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, boolean active, int accent, boolean glow) {
        int border = active ? accent : 0xFF6F787C;
        int fill = active ? 0xFF757D81 : 0xFF83898C;
        graphics.fill(x - 3, y - 3, x + 21, y + 21, active && glow ? 0x338EE7F0 : 0x22717A7D);
        graphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF555E62);
        drawFrame(graphics, x - 1, y - 1, 20, 20, 0xFF4E5659, border);
        graphics.fill(x, y, x + 18, y + 18, fill);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, active && glow ? 0x66E7FCFF : 0x3371797C);
    }

    private static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int shadow, int light) {
        graphics.fill(x, y, x + width, y + 1, light);
        graphics.fill(x, y, x + 1, y + height, light);
        graphics.fill(x, y + height - 1, x + width, y + height, shadow);
        graphics.fill(x + width - 1, y, x + width, y + height, shadow);
    }

    private static void drawGlowLineH(GuiGraphicsExtractor graphics, int x1, int x2, int y, int color) {
        graphics.fill(x1, y - 2, x2, y + 3, 0x229EE5EE);
        graphics.fill(x1, y - 1, x2, y + 2, 0x669EE5EE);
        graphics.fill(x1, y, x2, y + 1, color);
    }

    private static void drawGlowLineV(GuiGraphicsExtractor graphics, int x, int y1, int y2, int color) {
        graphics.fill(x - 2, y1, x + 3, y2, 0x229EE5EE);
        graphics.fill(x - 1, y1, x + 2, y2, 0x669EE5EE);
        graphics.fill(x, y1, x + 1, y2, color);
    }

    private static void drawConnection(GuiGraphicsExtractor graphics, int centerX, int centerY, int targetX, int targetY) {
        if (targetX == centerX) {
            drawGlowLineV(graphics, centerX, Math.min(centerY, targetY), Math.max(centerY, targetY), 0xCCBDEFF5);
        } else if (targetY == centerY) {
            drawGlowLineH(graphics, Math.min(centerX, targetX), Math.max(centerX, targetX), centerY, 0xCCBDEFF5);
        }
    }

    private static void drawDiamond(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int offset = -radius; offset <= radius; offset++) {
            int halfWidth = radius - Math.abs(offset);
            graphics.fill(centerX - halfWidth, centerY + offset, centerX + halfWidth + 1, centerY + offset + 1, color);
        }
    }

    private static void drawRunes(GuiGraphicsExtractor graphics) {
        drawRune(graphics, 20, 17);
        drawRune(graphics, 99, 17);
        drawRune(graphics, 20, 49);
        drawRune(graphics, 99, 49);
        graphics.fill(65, 10, 70, 12, 0x88E0F4F7);
        graphics.fill(65, 53, 70, 55, 0x88E0F4F7);
        graphics.fill(23, 30, 25, 35, 0x88E0F4F7);
        graphics.fill(103, 30, 105, 35, 0x88E0F4F7);
    }

    private static void drawRune(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 2, y + 2, 0x88E0E5E7);
        graphics.fill(x + 3, y + 1, x + 5, y + 3, 0x889EE5EE);
        graphics.fill(x + 1, y + 4, x + 4, y + 5, 0x66F7E4A2);
    }

    private static void addInputSlot(IRecipeLayoutBuilder builder, int x, int y,
            OfferingRecipe.CountedIngredient ingredient) {
        List<ItemStack> stacks = displayStacks(ingredient);
        if (!stacks.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x + ITEM_OFFSET, y + ITEM_OFFSET)
                    .addIngredients(VanillaTypes.ITEM_STACK, stacks);
        }
    }

    private static int offeringX(int index, int count) {
        if (count == 1) {
            return LEFT_X;
        }
        if (count == 2) {
            return index == 0 ? LEFT_X : RIGHT_X;
        }
        return switch (index) {
            case 0 -> TOP_X;
            case 1 -> LEFT_X;
            case 2 -> RIGHT_X;
            default -> BOTTOM_X;
        };
    }

    private static int offeringY(int index, int count) {
        if (count == 1 || count == 2) {
            return LEFT_Y;
        }
        return switch (index) {
            case 0 -> TOP_Y;
            case 1 -> LEFT_Y;
            case 2 -> RIGHT_Y;
            default -> BOTTOM_Y;
        };
    }

    private static List<ItemStack> displayStacks(OfferingRecipe.CountedIngredient ingredient) {
        List<ItemStack> stacks = new ArrayList<>();
        ingredient.ingredient().items().forEach(item -> {
            ItemStack copy = item.value().getDefaultInstance();
            copy.setCount(ingredient.count());
            if (ingredient.requireChargedCrystal() && copy.is(ModItems.EULOGIA_CRYSTAL.get())) {
                copy = EulogiaCrystalItem.chargedStack(copy.getItem());
                copy.setCount(ingredient.count());
            }
            stacks.add(copy);
        });
        return stacks;
    }
}
