package com.skylogistics.client;

import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.AmountFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

final class ConfigPanel {
    static final int BG = 0xF010233D;
    static final int BORDER = 0xFF2F5E82;
    static final int BORDER_ACTIVE = 0xFF6FB9FF;
    static final int TEXT = 0xFFEAF6FF;
    static final int MUTED = 0xFF6F8796;
    static final int ACCENT = 0xFFF7C66A;
    static final int PANEL = 0xFF07111F;
    static final int PANEL_SOFT = 0x99172B49;
    static final int BUTTON = 0xFF132B48;
    static final int BUTTON_DISABLED = 0xFF0A1626;
    static final int BUTTON_SELECTED = 0xFF214C72;
    static final int BUTTON_SELECTED_SOFT = 0xFF18385E;
    static final int BORDER_DIM = 0xFF244966;
    static final int SLOT_DIVIDER = 0xFF4C83A9;
    static final int SLOT_SHADOW = 0xFF091625;
    static final int SLOT_FILL = 0xFF1E3855;
    private static final int SKY_TOP = 0xFF172B49;
    private static final int SKY_HORIZON = 0xFF1F456C;
    private static final int CLOUD_EDGE = 0x338ECFFF;
    private static final int STAR = 0x55EAF6FF;
    private static final int SLOT_LOCKED_DIVIDER = 0xFF1C3147;
    private static final int SLOT_LOCKED_SHADOW = 0xFF050B14;
    private static final int SLOT_LOCKED_FILL = 0xFF0D1928;
    private static final int SLOT_HIGHLIGHT = 0x336FB9FF;
    private static final int SLOT_LOCKED_HIGHLIGHT = 0x225C7890;

    private ConfigPanel() {
    }

    static AbstractButton actionButton(int x, int y, int width, Component label, int action) {
        return button(x, y, width, 20, label, () -> ModNetworking.sendMenuAction(action));
    }

    static AbstractButton button(int x, int y, int width, int height, Component label, Runnable pressAction) {
        return new StyledButton(x, y, width, height, label, pressAction);
    }

    static void styleEditBox(EditBox editBox) {
        editBox.setBordered(false);
        editBox.setTextColor(TEXT);
        editBox.setTextColorUneditable(MUTED);
    }

    static void drawInputBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean active) {
        int border = active ? BORDER : BORDER_DIM;
        int fill = active ? 0xDD0B1B30 : 0xBB07111F;
        drawBox(graphics, x, y, width, height, fill, border);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, 0x226FB9FF);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0x4407111F);
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 14, SKY_TOP);
        graphics.fill(x + 1, y + 14, x + width - 1, y + height - 1, PANEL_SOFT);
        graphics.fill(x + 1, y + height - 13, x + width - 1, y + height - 1, 0x661F456C);
        drawSkyPixels(graphics, x, y, width, height);
        graphics.fill(x, y, x + width, y + 1, BORDER_ACTIVE);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        graphics.fill(x, y, x + 1, y + height, BORDER_ACTIVE);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, CLOUD_EDGE);
        graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, 0x66172B49);
    }

    static void drawContentPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BORDER_DIM);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 9, 0x66172B49);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, 0x446FB9FF);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0x66050B14);
    }

    static void drawButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = selected ? BUTTON_SELECTED : (active ? BUTTON : BUTTON_DISABLED);
        int border = selected ? BORDER_ACTIVE : (active ? BORDER : BORDER_DIM);
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, active ? 0x556FB9FF : 0x224C83A9);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0x8807111F);
        if (selected) {
            graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, ACCENT);
            graphics.fill(x + 2, y + 2, x + 3, y + height - 3, 0x66EAF6FF);
        }
    }

    static void drawBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DIVIDER);
        graphics.fill(x, y, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
        graphics.fill(x, y, x + 16, y + 1, SLOT_HIGHLIGHT);
        graphics.fill(x, y, x + 1, y + 16, 0x224D8FC1);
        graphics.fill(x + 15, y + 1, x + 16, y + 16, 0x66050B14);
    }

    static void drawLockedSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_LOCKED_DIVIDER);
        graphics.fill(x, y, x + 17, y + 17, SLOT_LOCKED_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_LOCKED_FILL);
        graphics.fill(x, y, x + 16, y + 1, SLOT_LOCKED_HIGHLIGHT);
        graphics.fill(x + 4, y + 8, x + 12, y + 9, 0x665C7890);
    }

    static String yesNo(boolean value) {
        return value ? "ON" : "OFF";
    }

    static String amount(long value) {
        return AmountFormatter.compact(value);
    }

    private static void drawSkyPixels(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (width < 24 || height < 24) {
            return;
        }
        for (int i = 0; i < 7; i++) {
            int px = x + 8 + (i * 31) % (width - 16);
            int py = y + 6 + (i * 17) % Math.max(1, height - 18);
            graphics.fill(px, py, px + 1, py + 1, STAR);
        }
        graphics.fill(x + 6, y + height - 9, x + 18, y + height - 8, SKY_HORIZON);
        graphics.fill(x + width - 26, y + height - 10, x + width - 9, y + height - 9, SKY_HORIZON);
    }

    private static final class StyledButton extends AbstractButton {
        private final Runnable pressAction;

        private StyledButton(int x, int y, int width, int height, Component label, Runnable pressAction) {
            super(x, y, width, height, label);
            this.pressAction = pressAction;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (active) {
                pressAction.run();
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
