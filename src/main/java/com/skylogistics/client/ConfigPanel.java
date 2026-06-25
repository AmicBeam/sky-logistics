package com.skylogistics.client;

import com.skylogistics.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class ConfigPanel {
    static final int BG = 0xF0152730;
    static final int BORDER = 0xFF68D7E5;
    static final int TEXT = 0xFFE8FBFF;
    static final int MUTED = 0xFF8FB7C1;
    static final int ACCENT = 0xFFFFE59A;

    private ConfigPanel() {
    }

    static Button actionButton(int x, int y, int width, Component label, int action) {
        return Button.builder(label, ignored -> ModNetworking.sendMenuAction(action))
                .bounds(x, y, width, 20)
                .build();
    }

    static void drawSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 18, y + 18, 0xFF07101B);
        graphics.fill(x - 1, y - 1, x + 18, y, BORDER);
        graphics.fill(x - 1, y + 17, x + 18, y + 18, BORDER);
        graphics.fill(x - 1, y - 1, x, y + 18, BORDER);
        graphics.fill(x + 17, y - 1, x + 18, y + 18, BORDER);
    }

    static String yesNo(boolean value) {
        return value ? "ON" : "OFF";
    }

    static String amount(long value) {
        if (value >= 1_000_000_000L) {
            return (value / 1_000_000_000L) + "B";
        }
        if (value >= 1_000_000L) {
            return (value / 1_000_000L) + "M";
        }
        if (value >= 1_000L) {
            return (value / 1_000L) + "K";
        }
        return Long.toString(value);
    }
}
