package com.skylogistics.client;

import com.skylogistics.network.ModNetworking;
import com.skylogistics.util.AmountFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    static final int BORDER_DIM = 0xFF244966;
    static final int SLOT_DIVIDER = 0xFFEAF6FF;
    static final int SLOT_SHADOW = 0xFF262C30;
    static final int SLOT_FILL = 0xFF6F7678;
    private static final int SKY_TOP = 0xFF172B49;
    private static final int SKY_HORIZON = 0xFF1F456C;
    private static final int SKY_CLOUD = 0x3A8ECFFF;
    private static final int SKY_CLOUD_SOFT = 0x256FB9FF;
    private static final int STAR = 0x55EAF6FF;
    private static final int FRAME_LIGHT = 0xFFEAF6FF;
    private static final int FRAME = 0xFF637984;
    private static final int FRAME_DARK = 0xFF182733;
    private static final int FRAME_SHADOW = 0xFF050B12;
    private static final int GOLD_DARK = 0xFF8B681F;
    private static final int SLOT_LOCKED_DIVIDER = 0xFF1C3147;
    private static final int SLOT_LOCKED_SHADOW = 0xFF050B14;
    private static final int SLOT_LOCKED_FILL = 0xFF0D1928;
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

    static int textCenterY(int y, int height) {
        return y + (height - 8) / 2;
    }

    static void drawInputBox(GuiGraphics graphics, int x, int y, int width, int height, boolean active) {
        int border = active ? BORDER_ACTIVE : FRAME;
        int fill = active ? 0xEE07111F : 0xDD07111F;
        drawThinFrame(graphics, x, y, width, height, fill, active ? FRAME_LIGHT : FRAME, border, FRAME_DARK);
    }

    static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y + 1, x + width + 1, y + height + 1, 0x99000000);
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 16, SKY_TOP);
        graphics.fill(x + 2, y + 16, x + width - 2, y + height - 2, PANEL_SOFT);
        graphics.fill(x + 2, y + height - 18, x + width - 2, y + height - 2, 0x551F456C);
        drawSkyPixels(graphics, x, y, width, height);
        drawCloudBand(graphics, x, y, width, height);
        drawOuterFrame(graphics, x, y, width, height);
    }

    static void drawContentPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x3307111F);
        drawInteriorClouds(graphics, x, y, width, height);
    }

    static void drawButtonChrome(GuiGraphics graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = selected ? 0xFF102637 : (active ? BUTTON : BUTTON_DISABLED);
        int border = selected ? CYAN : (active ? 0xFF4FA7C8 : BORDER_DIM);
        graphics.fill(x + 1, y + 1, x + width + 1, y + height + 1, FRAME_SHADOW);
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawStepperValue(GuiGraphics graphics, int x, int y, int width, int height, boolean active) {
        int fill = active ? 0xEE07111F : 0xDD07111F;
        int border = active ? FRAME : BORDER_DIM;
        graphics.fill(x, y + 1, x + width, y + height + 1, FRAME_SHADOW);
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x, y + 1, x + width, y + height - 1, fill);
        graphics.fill(x, y, x + width, y + 1, active ? 0x663F5660 : BORDER_DIM);
        graphics.fill(x, y + height - 1, x + width, y + height, FRAME_SHADOW);
    }

    static void drawFaceButtonChrome(GuiGraphics graphics, int x, int y, int width, int height,
            boolean active, boolean selected) {
        int fill = selected ? 0xFF102637 : (active ? 0xFF132839 : BUTTON_DISABLED);
        int border = selected ? CYAN : (active ? 0xFF4FA7C8 : BORDER_DIM);
        if (selected) {
            drawSelectorTriangle(graphics, x + width / 2 - 4, y - 8);
        }
        graphics.fill(x + 1, y + 1, x + width + 1, y + height + 1, FRAME_SHADOW);
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawStatusStrip(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, FRAME_SHADOW);
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x, y, x + width, y + 1, 0x99FFFFFF);
    }

    static void drawListRow(GuiGraphics graphics, int x, int y, int width, int height,
            boolean selected, int accent) {
        int fill = selected ? 0xDD07111F : 0x6607111F;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x + 1, y + 2, x + 4, y + height - 2, accent);
        if (selected) {
            graphics.fill(x, y, x + width, y + 1, ACCENT);
        }
    }

    static void drawScrollbar(GuiGraphics graphics, int x, int y, int height, int thumbY, int thumbHeight,
            boolean active) {
        drawThinFrame(graphics, x, y, 7, height, 0xDD07111F, FRAME, BORDER_DIM, FRAME_DARK);
        if (!active) {
            graphics.fill(x + 2, y + 2, x + 5, y + height - 2, 0x77425058);
            return;
        }
        graphics.fill(x + 1, thumbY, x + 6, thumbY + thumbHeight, FRAME_SHADOW);
        graphics.fill(x + 2, thumbY + 1, x + 5, thumbY + thumbHeight - 1, CYAN);
        graphics.fill(x + 2, thumbY + 1, x + 5, thumbY + 2, FRAME_LIGHT);
        graphics.fill(x + 4, thumbY + 2, x + 5, thumbY + thumbHeight - 1, BORDER);
    }

    static void drawInventoryPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x2207111F);
        drawInteriorClouds(graphics, x, y, width, height);
    }

    static void drawBox(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    static void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0x99EAF6FF);
        graphics.fill(x + 1, y + 1, x + 2, y + 17, 0x99EAF6FF);
        graphics.fill(x + 2, y + 16, x + 17, y + 17, 0xCC15191D);
        graphics.fill(x + 16, y + 2, x + 17, y + 17, 0xCC15191D);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF8B9090);
    }

    static void drawLockedSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_LOCKED_SHADOW);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_LOCKED_FILL);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, SLOT_LOCKED_HIGHLIGHT);
        graphics.fill(x + 4, y + 8, x + 14, y + 9, 0x665C7890);
    }

    static void drawTerminalSlotBackground(GuiGraphics graphics, int x, int y) {
        drawSlotBackground(graphics, x, y);
    }

    static void drawLockedTerminalSlotBackground(GuiGraphics graphics, int x, int y) {
        drawLockedSlotBackground(graphics, x, y);
    }

    static String yesNo(boolean value) {
        return value ? "ON" : "OFF";
    }

    static String amount(long value) {
        return AmountFormatter.compact(value);
    }

    private static void drawSkyPixels(GuiGraphics graphics, int x, int y, int width, int height) {
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

    private static void drawCloudBand(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width < 120 || height < 70) {
            return;
        }
        drawCloudCluster(graphics, x + width - Math.min(72, width / 3) - 8, y + 17,
                Math.min(72, width / 3));
        int midY = y + Math.max(48, height / 2 - 10);
        drawCloudCluster(graphics, x + 10, midY, Math.min(64, width / 3));
        if (width > 180) {
            drawCloudCluster(graphics, x + width / 2 + 12, midY + 13, Math.min(76, width / 3));
        }
        drawCloudCluster(graphics, x + width - Math.min(82, width / 3) - 10, y + height - 56,
                Math.min(82, width / 3));
    }

    private static void drawCloudCluster(GuiGraphics graphics, int x, int y, int width) {
        if (width < 36) {
            return;
        }
        graphics.fill(x, y + 8, x + width, y + 9, SKY_CLOUD_SOFT);
        graphics.fill(x + 6, y + 5, x + width - 8, y + 7, SKY_CLOUD);
        graphics.fill(x + 14, y + 2, x + width - 18, y + 5, SKY_CLOUD_SOFT);
        if (width >= 56) {
            graphics.fill(x + 22, y, x + width - 26, y + 2, SKY_CLOUD);
        }
        graphics.fill(x + width - 18, y + 6, x + width - 4, y + 8, SKY_CLOUD);
    }

    private static void drawInteriorClouds(GuiGraphics graphics, int x, int y, int width, int height) {
        if (width < 96 || height < 34) {
            return;
        }
        int cloudWidth = Math.min(84, width / 2);
        drawCloudCluster(graphics, x + width - cloudWidth - 8, y + height - 16, cloudWidth);
        if (height >= 70) {
            drawCloudCluster(graphics, x + 10, y + 8, Math.min(64, width / 3));
        }
    }

    private static void drawOuterFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        drawThinFrame(graphics, x, y, width, height, 0x00000000, FRAME_LIGHT, BORDER_ACTIVE, FRAME_SHADOW);
    }

    private static void drawThinFrame(GuiGraphics graphics, int x, int y, int width, int height, int fill,
            int light, int border, int dark) {
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x, y, x + width, y + 1, light);
        graphics.fill(x, y, x + 1, y + height, light);
        graphics.fill(x + width - 1, y, x + width, y + height, dark);
        graphics.fill(x, y + height - 1, x + width, y + height, FRAME_SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    private static void drawSelectorTriangle(GuiGraphics graphics, int x, int y) {
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
        public void onPress() {
            if (active) {
                pressAction.run();
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            drawButtonChrome(graphics, getX(), getY(), width, height, active, isHoveredOrFocused());
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    textCenterY(getY(), height), active ? TEXT : MUTED);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
