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
    static final int CYAN = 0xFF71E6FF;
    static final int PANEL = 0xFF07111F;
    static final int PANEL_SOFT = 0x99172B49;
    static final int BUTTON = 0xFF17212D;
    static final int BUTTON_DISABLED = 0xFF0A1626;
    static final int BUTTON_SELECTED = 0xFF3A2B16;
    static final int BUTTON_SELECTED_SOFT = 0xFF172B49;
    static final int BORDER_DIM = 0xFF244966;
    static final int SLOT_DIVIDER = 0xFFEAF6FF;
    static final int SLOT_SHADOW = 0xFF262C30;
    static final int SLOT_FILL = 0xFF6F7678;
    private static final int SKY_TOP = 0xFF172B49;
    private static final int SKY_HORIZON = 0xFF1F456C;
    private static final int CLOUD_EDGE = 0x338ECFFF;
    private static final int STAR = 0x55EAF6FF;
    private static final int FRAME_LIGHT = 0xFFEAF6FF;
    private static final int FRAME = 0xFF637984;
    private static final int FRAME_DARK = 0xFF182733;
    private static final int FRAME_SHADOW = 0xFF050B12;
    private static final int GOLD_DARK = 0xFF8B681F;
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
        int border = active ? BORDER_ACTIVE : FRAME;
        int fill = active ? 0xEE07111F : 0xDD07111F;
        drawCutBox(graphics, x, y, width, height, fill, border, active ? FRAME_LIGHT : FRAME, FRAME_DARK);
        graphics.fill(x + 4, y + 3, x + width - 4, y + 4, 0x335E8CB7);
        graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, 0x99050B12);
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x99000000);
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 16, SKY_TOP);
        graphics.fill(x + 2, y + 16, x + width - 2, y + height - 2, PANEL_SOFT);
        graphics.fill(x + 2, y + height - 18, x + width - 2, y + height - 2, 0x551F456C);
        drawSkyPixels(graphics, x, y, width, height);
        drawOuterFrame(graphics, x, y, width, height);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, CLOUD_EDGE);
        graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, 0x7707111F);
    }

    static void drawContentPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        drawCutBox(graphics, x, y, width, height, PANEL, BORDER, BORDER_ACTIVE, FRAME_DARK);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 10, 0x66172B49);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 4, 0x556FB9FF);
        graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3, 0x99050B14);
        drawCornerBolts(graphics, x, y, width, height, BORDER_ACTIVE);
    }

    static void drawButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = selected ? BUTTON_SELECTED : (active ? BUTTON : BUTTON_DISABLED);
        int border = selected ? ACCENT : (active ? FRAME : BORDER_DIM);
        int light = selected ? 0xFFFFD980 : (active ? 0xFF9FB4B8 : BORDER_DIM);
        int dark = selected ? GOLD_DARK : FRAME_DARK;
        drawCutBox(graphics, x, y, width, height, fill, border, light, dark);
        graphics.fill(x + 4, y + 3, x + width - 4, y + 4, selected ? 0x55FFE49A : 0x334F7180);
        graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, FRAME_SHADOW);
        if (selected) {
            graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 2, ACCENT);
        }
    }

    static void drawFaceButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = active ? 0xFF132839 : BUTTON_DISABLED;
        int border = selected ? ACCENT : (active ? FRAME : BORDER_DIM);
        int light = selected ? 0xFFFFD66B : (active ? FRAME_LIGHT : 0xFF4B5F68);
        int dark = selected ? GOLD_DARK : FRAME_DARK;
        if (selected) {
            drawSelectorTriangle(graphics, x + width / 2 - 4, y - 8);
        }
        drawCutBox(graphics, x, y, width, height, fill, border, light, dark);
        graphics.fill(x + 4, y + 3, x + width - 4, y + 4, active ? 0x334F7180 : 0x223A5260);
        graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, FRAME_SHADOW);
    }

    static void drawStatusStrip(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, FRAME_SHADOW);
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x, y, x + width, y + 1, 0x99FFFFFF);
    }

    static void drawListRow(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean selected, int accent) {
        int fill = selected ? 0xEE07111F : 0xCC07111F;
        int border = selected ? ACCENT : BORDER_DIM;
        int light = selected ? 0xFFFFD980 : FRAME;
        int dark = selected ? GOLD_DARK : FRAME_DARK;
        drawCutBox(graphics, x, y, width, height, fill, border, light, dark);
        graphics.fill(x + 2, y + 2, x + 5, y + height - 2, accent);
        graphics.fill(x + 6, y + 3, x + width - 3, y + 4, 0x223F6A86);
        if (selected) {
            graphics.fill(x + 6, y + height - 4, x + width - 4, y + height - 3, ACCENT);
        }
    }

    static void drawScrollbar(GuiGraphicsExtractor graphics, int x, int y, int height, int thumbY, int thumbHeight,
            boolean active) {
        drawCutBox(graphics, x, y, 7, height, 0xDD07111F, BORDER_DIM, FRAME, FRAME_DARK);
        if (!active) {
            graphics.fill(x + 2, y + 2, x + 5, y + height - 2, 0x77425058);
            return;
        }
        graphics.fill(x + 1, thumbY, x + 6, thumbY + thumbHeight, FRAME_SHADOW);
        graphics.fill(x + 2, thumbY + 1, x + 5, thumbY + thumbHeight - 1, CYAN);
        graphics.fill(x + 2, thumbY + 1, x + 5, thumbY + 2, FRAME_LIGHT);
        graphics.fill(x + 4, thumbY + 2, x + 5, thumbY + thumbHeight - 1, BORDER);
    }

    static void drawInventoryPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        drawCutBox(graphics, x, y, width, height, 0xBB07111F, BORDER_DIM, FRAME, FRAME_DARK);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 4, 0x335E8CB7);
        graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3, 0x88050B12);
    }

    static void drawBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, 0xAA050B12);
        graphics.fill(x, y, x + 18, y + 18, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, SLOT_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + 17, 0x88EAF6FF);
        graphics.fill(x + 2, y + 16, x + 17, y + 17, 0xCC15191D);
        graphics.fill(x + 16, y + 2, x + 17, y + 17, 0xCC15191D);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF8B9090);
    }

    static void drawLockedSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, 0xAA050B12);
        graphics.fill(x, y, x + 18, y + 18, SLOT_LOCKED_SHADOW);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_LOCKED_FILL);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, SLOT_LOCKED_HIGHLIGHT);
        graphics.fill(x + 4, y + 8, x + 14, y + 9, 0x665C7890);
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

    private static void drawOuterFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        drawCutBox(graphics, x, y, width, height, 0x00000000, FRAME_LIGHT, FRAME_LIGHT, FRAME_SHADOW);
        drawCutBox(graphics, x + 2, y + 2, width - 4, height - 4, 0x00000000, BORDER_ACTIVE, BORDER_ACTIVE,
                FRAME_DARK);
        drawCutBox(graphics, x + 4, y + 4, width - 8, height - 8, 0x00000000, BORDER_DIM, BORDER, FRAME_SHADOW);
        drawCornerBolts(graphics, x + 4, y + 4, width - 8, height - 8, BORDER_ACTIVE);
    }

    private static void drawCutBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border,
            int light, int dark) {
        graphics.fill(x + 3, y, x + width - 3, y + 1, light);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, border);
        graphics.fill(x, y + 3, x + 1, y + height - 3, light);
        graphics.fill(x + 1, y + 2, x + 2, y + height - 2, border);
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, dark);
        graphics.fill(x + width - 1, y + 3, x + width, y + height - 3, FRAME_SHADOW);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, dark);
        graphics.fill(x + 3, y + height - 1, x + width - 3, y + height, FRAME_SHADOW);

        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
        graphics.fill(x + 3, y + 1, x + width - 3, y + 2, fill);
        graphics.fill(x + 1, y + 3, x + 2, y + height - 3, fill);
        graphics.fill(x + width - 2, y + 3, x + width - 1, y + height - 3, fill);
        graphics.fill(x + 3, y + height - 2, x + width - 3, y + height - 1, fill);

        graphics.fill(x + 1, y + 2, x + 3, y + 3, light);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + 2, dark);
        graphics.fill(x + 1, y + height - 3, x + 3, y + height - 2, dark);
        graphics.fill(x + width - 3, y + height - 2, x + width - 1, y + height - 1, FRAME_SHADOW);
    }

    private static void drawSelectorTriangle(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 8, y + 2, GOLD_DARK);
        graphics.fill(x + 1, y + 2, x + 7, y + 4, ACCENT);
        graphics.fill(x + 2, y + 4, x + 6, y + 6, ACCENT);
        graphics.fill(x + 3, y + 6, x + 5, y + 8, GOLD_DARK);
    }

    private static void drawCornerBolts(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x + 3, y + 3, x + 8, y + 4, color);
        graphics.fill(x + 3, y + 3, x + 4, y + 8, color);
        graphics.fill(x + width - 8, y + 3, x + width - 3, y + 4, color);
        graphics.fill(x + width - 4, y + 3, x + width - 3, y + 8, color);
        graphics.fill(x + 3, y + height - 4, x + 8, y + height - 3, color);
        graphics.fill(x + 3, y + height - 8, x + 4, y + height - 3, color);
        graphics.fill(x + width - 8, y + height - 4, x + width - 3, y + height - 3, color);
        graphics.fill(x + width - 4, y + height - 8, x + width - 3, y + height - 3, color);
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
