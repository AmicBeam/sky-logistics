package com.skylogistics.client;

import com.skylogistics.network.ModNetworking;
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
