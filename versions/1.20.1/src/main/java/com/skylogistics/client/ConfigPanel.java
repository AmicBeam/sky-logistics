package com.skylogistics.client;

import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.AmountFormatter;
import com.skylogistics.util.RedstoneControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class ConfigPanel {
    static final int BG = 0xFFC6C6C6;
    static final int BORDER = 0xFF373737;
    static final int BORDER_ACTIVE = 0xFF3A8D99;
    static final int TEXT = 0xFF404040;
    static final int FIELD_TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFF707070;
    static final int ACCENT = 0xFF9C711C;
    static final int RESOURCE_ACCENT = 0xFFB58A2E;
    static final int EXTRACT_ACCENT = 0xFFB87524;
    static final int INSERT_ACCENT = 0xFF2F7F8C;
    static final int MAINTAIN_ACCENT = 0xFF75658F;
    static final int PANEL = 0xFFB6B6B6;
    static final int PANEL_SOFT = 0x99B6B6B6;
    static final int BUTTON = 0xFF9E9E9E;
    static final int BUTTON_DISABLED = 0xFF858585;
    static final int BUTTON_SELECTED = 0xFF8FB8BE;
    static final int BUTTON_SELECTED_SOFT = 0xFFA9BFC2;
    static final int BORDER_DIM = 0xFF777777;
    static final int SLOT_SHADOW = 0xFF373737;
    static final int SLOT_FILL = 0xFF8B8B8B;
    static final int FIELDSET_HEIGHT = 31;
    static final int STEPPER_HEIGHT = 17;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int BUTTON_OUTLINE = 0xFF202020;
    private static final int BUTTON_EDGE = 0xFFD8D8D8;
    private static final int BUTTON_HIGHLIGHT = 0xFFF0F0F0;
    private static final int BUTTON_SHADOW = 0xFF555555;
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
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
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
            boolean active, boolean selected, int selectedBorder) {
        int fill = selected ? BUTTON_SELECTED : (active ? BUTTON : BUTTON_DISABLED);
        drawBeveledButton(graphics, x, y, width, height, fill, selected);
        if (selected) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, selectedBorder);
            graphics.fill(x + 1, y + 1, x + 2, y + height - 1, selectedBorder);
        }
    }

    static void drawResourceIcon(GuiGraphics graphics, int x, int y, String name, boolean selected) {
        ResourceLocation texture = new ResourceLocation("skylogistics", "textures/gui/configurator/resource_"
                + name + (selected ? "" : "_off") + "_small.png");
        graphics.blit(texture, x, y, 0, 0, 18, 17, 18, 17);
    }

    static void drawButtonChrome(GuiGraphics graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = selected ? BUTTON_SELECTED : (active ? BUTTON : BUTTON_DISABLED);
        drawBeveledButton(graphics, x, y, width, height, fill, selected);
        if (selected) {
            graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, BORDER_ACTIVE);
        }
    }

    private static void drawBeveledButton(GuiGraphics graphics, int x, int y, int width, int height,
            int fill, boolean emphasized) {
        // Vanilla-style stone button: dark outline, bright raised top/left edge,
        // medium face, then a deep bottom/right edge. Selected buttons keep the
        // same raised silhouette and communicate state through their face color.
        graphics.fill(x, y, x + width, y + height, BUTTON_OUTLINE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                emphasized ? BUTTON_HIGHLIGHT : BUTTON_EDGE);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, BUTTON_SHADOW);
        graphics.fill(x + width - 3, y + 2, x + width - 2, y + height - 2, BUTTON_SHADOW);
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
            drawButtonChrome(graphics, getX(), getY(), width, height, active, isHoveredOrFocused());
            drawCenteredText(graphics, Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 6, active ? TEXT : MUTED);
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
            drawButtonChrome(graphics, getX(), getY(), width, height, active, isHoveredOrFocused());
            drawCenteredText(graphics, Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, active ? TEXT : MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
