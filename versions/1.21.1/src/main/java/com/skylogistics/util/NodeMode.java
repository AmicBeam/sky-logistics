package com.skylogistics.util;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum NodeMode implements StringRepresentable {
    INPUT,
    OUTPUT;

    public NodeMode next() {
        return this == INPUT ? OUTPUT : INPUT;
    }

    public static NodeMode byName(String name) {
        try {
            return NodeMode.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return OUTPUT;
        }
    }

    public String translationKey() {
        return "tooltip.skylogistics.mode." + name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
