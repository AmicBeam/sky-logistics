package com.skylogistics.util;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum NodeFaceMode implements StringRepresentable {
    NONE,
    INPUT,
    OUTPUT;

    public static NodeFaceMode byName(String name) {
        try {
            return NodeFaceMode.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return NONE;
        }
    }

    public String translationKey() {
        return "tooltip.skylogistics.face_mode." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
