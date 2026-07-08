package com.skylogistics.client;

import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.AmountFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

final class ConfigPanel {
    static final int BG = 0xF0101D24;
    static final int BORDER = 0xFF3E8B99;
    static final int BORDER_ACTIVE = 0xFF68D7E5;
    static final int TEXT = 0xFFE8FBFF;
    static final int MUTED = 0xFF8FB7C1;
    static final int ACCENT = 0xFFFFE59A;
    static final int PANEL = 0xFF0B151B;
    static final int PANEL_SOFT = 0x99101B22;
    static final int BUTTON = 0xFF0D1D25;
    static final int BUTTON_DISABLED = 0xFF101820;
    static final int BUTTON_SELECTED = 0xFF12343C;
    static final int BUTTON_SELECTED_SOFT = 0xFF122930;
    static final int BORDER_DIM = 0xFF24454F;
    static final int SLOT_DIVIDER = 0xFF2B4C57;
    static final int SLOT_SHADOW = 0xFF142A33;
    static final int SLOT_FILL = 0xFF24424C;
    private static final int SLOT_LOCKED_DIVIDER = 0xFF172830;
    private static final int SLOT_LOCKED_SHADOW = 0xFF071016;
    private static final int SLOT_LOCKED_FILL = 0xFF0A141A;
    private static final int SLOT_HIGHLIGHT = 0x444D6D78;
    private static final int SLOT_LOCKED_HIGHLIGHT = 0x221A3038;

    private ConfigPanel() {
    }

    static AbstractButton actionButton(int x, int y, int width, Component label, int action) {
        return new ActionButton(x, y, width, label, action);
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x, y, x + width, y + 1, BORDER_ACTIVE);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        graphics.fill(x, y, x + 1, y + height, BORDER_ACTIVE);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x223DE6F5);
    }

    static void drawContentPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.fill(x, y, x + width, y + 1, BORDER_DIM);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF142C35);
        graphics.fill(x, y, x + 1, y + height, BORDER_DIM);
        graphics.fill(x + width - 1, y, x + width, y + height, 0xFF142C35);
    }

    static void drawButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = selected ? BUTTON_SELECTED : (active ? BUTTON : BUTTON_DISABLED);
        int border = selected ? BORDER_ACTIVE : (active ? BORDER : BORDER_DIM);
        drawBox(graphics, x, y, width, height, fill, border);
        if (selected) {
            graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, ACCENT);
        }
    }

    static void drawBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, SLOT_DIVIDER);
        graphics.fill(x, y, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
        graphics.fill(x, y, x + 16, y + 1, SLOT_HIGHLIGHT);
    }

    static void drawLockedSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, SLOT_LOCKED_DIVIDER);
        graphics.fill(x, y, x + 17, y + 17, SLOT_LOCKED_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_LOCKED_FILL);
        graphics.fill(x, y, x + 16, y + 1, SLOT_LOCKED_HIGHLIGHT);
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
