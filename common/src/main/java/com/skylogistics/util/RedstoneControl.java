package com.skylogistics.util;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum RedstoneControl implements StringRepresentable {
    IGNORE,
    HIGH,
    LOW,
    PULSE;

    public static RedstoneControl byName(String name) {
        try {
            return RedstoneControl.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return IGNORE;
        }
    }

    public RedstoneControl next() {
        RedstoneControl[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public RedstoneControl next(NodeFaceMode mode) {
        return next().normalizedFor(mode);
    }

    public RedstoneControl normalizedFor(NodeFaceMode mode) {
        return this == PULSE && mode != NodeFaceMode.INPUT ? IGNORE : this;
    }

    public String translationKey() {
        return "tooltip.skylogistics.redstone." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
