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
    static final int BUTTON = 0xFF132839;
    static final int BUTTON_DISABLED = 0xFF0A1626;
    static final int BUTTON_SELECTED = 0xFF19344A;
    static final int BUTTON_SELECTED_SOFT = 0xFF122838;
    static final int BORDER_DIM = 0xFF244966;
    static final int SLOT_DIVIDER = 0xFF4C83A9;
    static final int SLOT_SHADOW = 0xFF091625;
    static final int SLOT_FILL = 0xFF1E3855;
    private static final int SKY_TOP = 0xFF172B49;
    private static final int SKY_HORIZON = 0xFF1F456C;
    private static final int CLOUD_EDGE = 0x338ECFFF;
    private static final int STAR = 0x55EAF6FF;
    private static final int FRAME_LIGHT = 0xFF9FB4B8;
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
        int border = active ? FRAME : BORDER_DIM;
        int fill = active ? 0xDD0B1B30 : 0xBB07111F;
        drawCutBox(graphics, x, y, width, height, fill, border, active ? FRAME_LIGHT : BORDER_DIM, FRAME_DARK);
        graphics.fill(x + 4, y + 3, x + width - 4, y + 4, 0x226FB9FF);
        graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, 0x6607111F);
    }

    static void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 14, SKY_TOP);
        graphics.fill(x + 1, y + 14, x + width - 1, y + height - 1, PANEL_SOFT);
        graphics.fill(x + 1, y + height - 13, x + width - 1, y + height - 1, 0x661F456C);
        drawSkyPixels(graphics, x, y, width, height);
        drawFrame(graphics, x, y, width, height, BORDER_ACTIVE, BORDER, FRAME_SHADOW);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, CLOUD_EDGE);
        graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, 0x66172B49);
    }

    static void drawContentPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        drawCutBox(graphics, x, y, width, height, PANEL, FRAME, FRAME_LIGHT, FRAME_DARK);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 9, 0x66172B49);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, 0x446FB9FF);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0x66050B14);
    }

    static void drawButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = selected ? BUTTON_SELECTED : (active ? BUTTON : BUTTON_DISABLED);
        int border = selected ? ACCENT : (active ? FRAME : BORDER_DIM);
        int light = selected ? ACCENT : (active ? FRAME_LIGHT : BORDER_DIM);
        int dark = selected ? GOLD_DARK : FRAME_DARK;
        drawCutBox(graphics, x, y, width, height, fill, border, light, dark);
        graphics.fill(x + 4, y + 3, x + width - 4, y + 4, active ? 0x445F8BA3 : 0x223A5260);
        graphics.fill(x + 4, y + height - 4, x + width - 4, y + height - 3, FRAME_SHADOW);
    }

    static void drawFaceButtonChrome(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = active ? BUTTON : BUTTON_DISABLED;
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

    static void drawBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        drawCutBox(graphics, x - 1, y - 1, 18, 18, SLOT_FILL, SLOT_DIVIDER, FRAME_LIGHT, FRAME_DARK);
        graphics.fill(x, y, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, SLOT_FILL);
        graphics.fill(x, y, x + 16, y + 1, SLOT_HIGHLIGHT);
        graphics.fill(x, y, x + 1, y + 16, 0x224D8FC1);
        graphics.fill(x + 15, y + 1, x + 16, y + 16, 0x66050B14);
    }

    static void drawLockedSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        drawCutBox(graphics, x - 1, y - 1, 18, 18, SLOT_LOCKED_FILL, SLOT_LOCKED_DIVIDER,
                0xFF394D58, FRAME_DARK);
        graphics.fill(x, y, x + 17, y + 17, SLOT_LOCKED_SHADOW);
        graphics.fill(x + 1, y + 1, x + 16, y + 16, SLOT_LOCKED_FILL);
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

    private static void drawFrame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int light,
            int dark, int shadow) {
        graphics.fill(x, y, x + width, y + 1, light);
        graphics.fill(x, y, x + 1, y + height, light);
        graphics.fill(x, y + height - 1, x + width, y + height, dark);
        graphics.fill(x + width - 1, y, x + width, y + height, dark);
        graphics.fill(x + 1, y + height, x + width + 1, y + height + 1, shadow);
        graphics.fill(x + width, y + 1, x + width + 1, y + height + 1, shadow);
    }

    private static void drawCutBox(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill,
            int border, int light, int dark) {
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
