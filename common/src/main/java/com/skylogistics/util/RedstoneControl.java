package com.skylogistics.util;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum RedstoneControl implements StringRepresentable {
    IGNORE,
    HIGH,
    LOW,
    DISABLED;

    public static RedstoneControl byName(String name) {
        try {
            RedstoneControl control = RedstoneControl.valueOf(name.toUpperCase(Locale.ROOT));
            return control == DISABLED ? IGNORE : control;
        } catch (IllegalArgumentException | NullPointerException exception) {
            return IGNORE;
        }
    }

    public RedstoneControl next() {
        return switch (this) {
            case IGNORE, DISABLED -> HIGH;
            case HIGH -> LOW;
            case LOW -> IGNORE;
        };
    }

    public String translationKey() {
        return "tooltip.skylogistics.redstone." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
