package com.skylogistics.client;

import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.AmountFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

final class ConfigPanel {
    static final int BG = 0xF00C1419;
    static final int BORDER = 0xFF3A727A;
    static final int BORDER_ACTIVE = 0xFF65D9E6;
    static final int TEXT = 0xFFEAF7F6;
    static final int MUTED = 0xFF8FA7AB;
    static final int ACCENT = 0xFFF1C86B;
    static final int PANEL = 0xFF081015;
    static final int PANEL_SOFT = 0xAA101A20;
    static final int BUTTON = 0xFF101B21;
    static final int BUTTON_DISABLED = 0xFF0B1115;
    static final int BUTTON_SELECTED = 0xFF14323A;
    static final int BUTTON_SELECTED_SOFT = 0xFF12272D;
    static final int BORDER_DIM = 0xFF213941;
    static final int SLOT_DIVIDER = 0xFF263E46;
    static final int SLOT_SHADOW = 0xFF101A20;
    static final int SLOT_FILL = 0xFF243B43;
    private static final int INPUT = 0xFF05090C;
    private static final int PANEL_BOTTOM = 0xFF050B0F;
    private static final int PANEL_HIGHLIGHT = 0x332D5961;
    private static final int BUTTON_HIGHLIGHT = 0x332B5058;
    private static final int BUTTON_INSET = 0x66121B20;
    private static final int SLOT_LOCKED_DIVIDER = 0xFF17262D;
    private static final int SLOT_LOCKED_SHADOW = 0xFF050A0D;
    private static final int SLOT_LOCKED_FILL = 0xFF091115;
    private static final int SLOT_HIGHLIGHT = 0x6658737A;
    private static final int SLOT_LOCKED_HIGHLIGHT = 0x221B3138;

    private ConfigPanel() {
    }

    static AbstractButton actionButton(int x, int y, int width, Component label, int action) {
        return new ActionButton(x, y, width, label, action);
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x, y, x + width, y + 1, BORDER_ACTIVE);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER_DIM);
        graphics.fill(x, y, x + 1, y + height, BORDER_ACTIVE);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, PANEL_HIGHLIGHT);
        graphics.fill(x + 5, y + 6, x + width - 5, y + 7, 0x221B3037);
    }

    static void drawContentPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 1, BORDER_DIM);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_BOTTOM);
        graphics.fill(x, y, x + 1, y + height, BORDER_DIM);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF142A31);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x221E3941);
    }

    static void drawButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = selected ? BUTTON_SELECTED : (active ? BUTTON : BUTTON_DISABLED);
        int border = selected ? BORDER_ACTIVE : (active ? BORDER : BORDER_DIM);
        drawBox(graphics, x, y, width, height, fill, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, active ? BUTTON_HIGHLIGHT : BUTTON_INSET);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, BUTTON_INSET);
        if (selected) {
            graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3, ACCENT);
        }
    }

    static void drawInputBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean focused) {
        drawBox(graphics, x, y, width, height, INPUT, focused ? BORDER_ACTIVE : BORDER_DIM);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x221E3941);
        if (focused) {
            graphics.fill(x + 3, y + height - 3, x + width - 3, y + height - 2, ACCENT);
        }
    }

    static void drawBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, SLOT_DIVIDER);
        graphics.fill(x, y, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, SLOT_FILL);
        graphics.fill(x + 1, y + 1, x + 16, y + 2, SLOT_HIGHLIGHT);
        graphics.fill(x + 1, y + 16, x + 16, y + 17, 0x6610181D);
    }

    static void drawLockedSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, SLOT_LOCKED_DIVIDER);
        graphics.fill(x, y, x + 17, y + 17, SLOT_LOCKED_SHADOW);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, SLOT_LOCKED_FILL);
        graphics.fill(x + 1, y + 1, x + 16, y + 2, SLOT_LOCKED_HIGHLIGHT);
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
            drawButtonChrome(graphics, getX(), getY(), width, height, active, isHoveredOrFocused());
            graphics.centeredText(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 6, active ? TEXT : MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
