package com.skylogistics.client;

import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.AmountFormatter;
import com.skylogistics.util.RedstoneControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class ConfigPanel {
    static final int BG = 0xFFC6C6C6;
    static final int BORDER = 0xFF373737;
    static final int BORDER_ACTIVE = 0xFF3A8D99;
    static final int TEXT = 0xFF000000;
    static final int FIELD_TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFF707070;
    static final int ACCENT = 0xFF9C711C;
    static final int RESOURCE_ACCENT = 0xFFB58A2E;
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
    private static final WidgetSprites VANILLA_BUTTON_SPRITES = new WidgetSprites(
            Identifier.withDefaultNamespace("widget/button"),
            Identifier.withDefaultNamespace("widget/button_disabled"),
            Identifier.withDefaultNamespace("widget/button_highlighted"));
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

    static void drawCenteredText(GuiGraphicsExtractor graphics, Font font, Component text,
            int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static void drawCenteredText(GuiGraphicsExtractor graphics, Font font, String text,
            int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    static int buttonTextColor(boolean active) {
        return active ? 0xFFFFFFFF : 0xFFA0A0A0;
    }

    static void drawCenteredButtonText(GuiGraphicsExtractor graphics, Font font, Component text,
            int centerX, int y, boolean active) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, buttonTextColor(active), true);
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x, y, x + width, y + 1, PANEL_HIGHLIGHT);
        graphics.fill(x, y, x + 1, y + height, PANEL_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_SHADOW);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_SHADOW);
    }

    static void drawContentPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_HIGHLIGHT);
        graphics.fill(x, y, x + width - 1, y + height - 1, PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    static void drawFieldset(GuiGraphicsExtractor graphics, int x, int y, int width, int legendWidth) {
        drawFieldset(graphics, x, y, width, legendWidth, FIELDSET_HEIGHT);
    }

    static void drawFieldset(GuiGraphicsExtractor graphics, int x, int y, int width, int legendWidth, int height) {
        int gapLeft = x + (width - legendWidth) / 2 - 3;
        int gapRight = gapLeft + legendWidth + 6;
        graphics.fill(x, y, gapLeft, y + 1, PANEL_SHADOW);
        graphics.fill(gapRight, y, x + width, y + 1, PANEL_SHADOW);
        graphics.fill(x, y, x + 1, y + height, PANEL_SHADOW);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_HIGHLIGHT);
    }

    static void drawStepperValue(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + STEPPER_HEIGHT, PANEL_HIGHLIGHT);
        graphics.fill(x, y, x + width - 1, y + STEPPER_HEIGHT - 1, PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + STEPPER_HEIGHT - 1, 0xFF101010);
    }

    static void drawRedstoneIcon(GuiGraphicsExtractor graphics, int x, int y, RedstoneControl control) {
        Identifier texture = Identifier.fromNamespaceAndPath("skylogistics",
                "textures/gui/configurator/redstone_" + control.getSerializedName() + ".png");
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, 16, 16, 16, 16);
    }

    static void drawImageButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean highlighted, boolean selected, int selectedBorder) {
        drawVanillaButton(graphics, x, y, width, height, active, highlighted,
                selected ? selectedBorder : 0xFFFFFFFF);
    }

    static void drawResourceIcon(GuiGraphicsExtractor graphics, int x, int y, String name, boolean selected) {
        Identifier texture = Identifier.fromNamespaceAndPath("skylogistics", "textures/gui/configurator/resource_"
                + name + (selected ? "" : "_off") + "_small.png");
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, 18, 17, 18, 17);
    }

    static void drawButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean highlighted) {
        drawVanillaButton(graphics, x, y, width, height, active, highlighted, 0xFFFFFFFF);
    }

    private static void drawVanillaButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean highlighted, int tint) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA_BUTTON_SPRITES.get(active, highlighted),
                x, y, width, height, tint);
    }

    static void drawBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_HIGHLIGHT);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, SLOT_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    static void drawLockedSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
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
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                ModNetworking.sendMenuAction(action);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            Font font = Minecraft.getInstance().font;
            graphics.text(font, getMessage(), getX() + (width - font.width(getMessage())) / 2,
                    getY() + 6, buttonTextColor(active), true);
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
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                onPress.run();
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            drawButtonChrome(graphics, getX(), getY(), width, height, active, isHovered());
            Font font = Minecraft.getInstance().font;
            graphics.text(font, getMessage(), getX() + (width - font.width(getMessage())) / 2,
                    getY() + (height - 8) / 2, buttonTextColor(active), true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
