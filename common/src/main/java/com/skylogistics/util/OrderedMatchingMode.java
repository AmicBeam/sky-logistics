package com.skylogistics.util;

public enum OrderedMatchingMode {
    PER_SLOT("per_slot"),
    PER_ITEM("per_item");

    private final String serializedName;

    OrderedMatchingMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "mode.skylogistics.ordered_matching_upgrade." + serializedName;
    }

    public OrderedMatchingMode next() {
        return this == PER_SLOT ? PER_ITEM : PER_SLOT;
    }

    public static OrderedMatchingMode byName(String name) {
        if (name != null) {
            for (OrderedMatchingMode mode : values()) {
                if (mode.serializedName.equalsIgnoreCase(name) || mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
        }
        return PER_SLOT;
    }
}
