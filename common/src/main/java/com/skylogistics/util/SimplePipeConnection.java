package com.skylogistics.util;

import net.minecraft.util.StringRepresentable;

public enum SimplePipeConnection implements StringRepresentable {
    NONE("none"),
    PIPE("pipe"),
    INSERT("insert"),
    EXTRACT("extract");

    private final String serializedName;

    SimplePipeConnection(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public boolean isContainer() {
        return this == INSERT || this == EXTRACT;
    }
}
