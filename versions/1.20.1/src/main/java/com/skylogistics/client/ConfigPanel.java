package com.skylogistics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.AmountFormatter;
import com.skylogistics.util.RedstoneControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class ConfigPanel {
    static final int BG = 0xFFC6C6C6;
    static final int BORDER = 0xFF373737;
    static final int BORDER_ACTIVE = 0xFF3A8D99;
    static final int TEXT = 0xFF000000;
    static final int FIELD_TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFF707070;
    static final int ACCENT = BORDER_ACTIVE;
    static final int RESOURCE_ACCENT = BORDER_ACTIVE;
    static final int EXTRACT_ACCENT = 0xFFB87524;
    static final int INSERT_ACCENT = 0xFF2F7F8C;
    static final int MAINTAIN_ACCENT = 0xFF75658F;
    static final int PANEL = 0xFFB6B6B6;
    static final int PANEL_SOFT = 0x99B6B6B6;
    static final int BORDER_DIM = 0xFF777777;
    static final int SLOT_SHADOW = 0xFF373737;
    static final int SLOT_FILL = 0xFF8B8B8B;
    static final int FIELDSET_HEIGHT = 31;
    static final int STEPPER_HEIGHT = 17;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int SLOT_LOCKED_SHADOW = 0xFF555555;
    private static final int SLOT_LOCKED_FILL = 0xFF707070;
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;
    private static final int SLOT_LOCKED_HIGHLIGHT = 0xFFB0B0B0;

    private ConfigPanel() {
    }

    static AbstractButton actionButton(int x, int y, int width, Component label, int action) {
        return new ActionButton(x, y, width, label, action);
    }

    static AbstractButton button(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new StyledButton(x, y, width, height, label, onPress);
    }

    static void drawCenteredText(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static void drawCenteredText(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static int buttonTextColor(boolean active) {
        return active ? 0xFFFFFFFF : 0xFFA0A0A0;
    }

    static void drawCenteredButtonText(GuiGraphics graphics, Font font, Component text,
            int centerX, int y, boolean active) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, buttonTextColor(active), true);
    }

    static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x, y, x + width, y + 1, PANEL_HIGHLIGHT);
        graphics.fill(x, y, x + 1, y + height, PANEL_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_SHADOW);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_SHADOW);
    }

    static void drawContentPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_HIGHLIGHT);
        graphics.fill(x, y, x + width - 1, y + height - 1, PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    static void drawFieldset(GuiGraphics graphics, int x, int y, int width, int legendWidth) {
        drawFieldset(graphics, x, y, width, legendWidth, FIELDSET_HEIGHT);
    }

    static void drawFieldset(GuiGraphics graphics, int x, int y, int width, int legendWidth, int height) {
        int gapLeft = x + (width - legendWidth) / 2 - 3;
        int gapRight = gapLeft + legendWidth + 6;
        graphics.fill(x, y, gapLeft, y + 1, PANEL_SHADOW);
        graphics.fill(gapRight, y, x + width, y + 1, PANEL_SHADOW);
        graphics.fill(x, y, x + 1, y + height, PANEL_SHADOW);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_HIGHLIGHT);
    }

    static void drawStepperValue(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + STEPPER_HEIGHT, PANEL_HIGHLIGHT);
        graphics.fill(x, y, x + width - 1, y + STEPPER_HEIGHT - 1, PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + STEPPER_HEIGHT - 1, 0xFF101010);
    }

    static void drawRedstoneIcon(GuiGraphics graphics, int x, int y, RedstoneControl control) {
        ResourceLocation texture = new ResourceLocation("skylogistics",
                "textures/gui/configurator/redstone_" + control.getSerializedName() + ".png");
        graphics.blit(texture, x, y, 0, 0, 16, 16, 16, 16);
    }

    static void drawImageButtonChrome(GuiGraphics graphics, int x, int y, int width, int height,
            boolean active, boolean highlighted, boolean selected, int selectedBorder) {
        drawVanillaButton(graphics, x, y, width, height, active, highlighted,
                selected ? selectedBorder : 0xFFFFFFFF);
    }

    static void drawResourceIcon(GuiGraphics graphics, int x, int y, String name, boolean selected) {
        boolean useColoredIcon = selected || name.equals("item") || name.equals("fluid") || name.equals("energy");
        ResourceLocation texture = new ResourceLocation("skylogistics", "textures/gui/configurator/resource_"
                + name + (useColoredIcon ? "" : "_off") + "_small.png");
        graphics.blit(texture, x, y, 0, 0, 18, 17, 18, 17);
    }

    static void drawButtonChrome(GuiGraphics graphics, int x, int y, int width, int height,
            boolean active, boolean highlighted) {
        drawVanillaButton(graphics, x, y, width, height, active, highlighted, 0xFFFFFFFF);
    }

    private static void drawVanillaButton(GuiGraphics graphics, int x, int y, int width, int height,
            boolean active, boolean highlighted, int tint) {
        int state = !active ? 0 : highlighted ? 2 : 1;
        graphics.setColor(((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F, ((tint >>> 24) & 0xFF) / 255.0F);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        graphics.blitNineSliced(AbstractWidget.WIDGETS_LOCATION, x, y, width, height,
                20, 4, 200, 20, 0, 46 + state * 20);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    static void drawBox(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_HIGHLIGHT);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    static void drawLockedSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_LOCKED_HIGHLIGHT);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_LOCKED_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_LOCKED_FILL);
    }

    static String yesNo(boolean value) {
        return value ? "ON" : "OFF";
    }

    static String amount(long value) {
        return AmountFormatter.compact(value);
    }

    private static final class ActionButton extends AbstractButton {
        private final int action;

        private ActionButton(int x, int y, int width, Component label, int action) {
            super(x, y, width, 20, label);
            this.action = action;
        }

        @Override
        public void onPress() {
            if (active) {
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 6, buttonTextColor(active));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static final class StyledButton extends AbstractButton {
        private final Runnable onPress;

        private StyledButton(int x, int y, int width, int height, Component label, Runnable onPress) {
            super(x, y, width, height, label);
            this.onPress = onPress;
        }

        @Override
        public void onPress() {
            if (active) {
                onPress.run();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, buttonTextColor(active));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
