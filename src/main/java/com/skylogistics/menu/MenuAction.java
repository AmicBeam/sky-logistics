package com.skylogistics.menu;

public final class MenuAction {
    public static final int NEW_LINE = 0;
    public static final int TOGGLE_ITEMS = 1;
    public static final int TOGGLE_FLUIDS = 2;
    public static final int RATE_DOWN = 3;
    public static final int RATE_UP = 4;
    public static final int MODE_INSERT = 5;
    public static final int MODE_EXTRACT = 6;
    public static final int TOGGLE_ENERGY = 10;
    public static final int CONFIG_REDSTONE = 11;
    public static final int CONFIG_PRIORITY_DOWN = 12;
    public static final int CONFIG_PRIORITY_UP = 13;
    public static final int FILTER_TOGGLE_WHITELIST = 20;
    public static final int FILTER_TOGGLE_TAGS = 21;
    public static final int FILTER_CLEAR = 22;
    public static final int FILTER_TOGGLE_MODE = 23;
    public static final int FILTER_TOGGLE_MODS = 24;
    public static final int FILTER_ATTRIBUTE_BASE = 30;
    public static final int FACE_NONE_BASE = 100;
    public static final int FACE_EXTRACT_BASE = 110;
    public static final int FACE_INSERT_BASE = 120;
    public static final int FACE_PRIORITY_DOWN_BASE = 140;
    public static final int FACE_PRIORITY_UP_BASE = 150;
    public static final int FACE_REDSTONE_BASE = 160;
    public static final int FACE_SELECT_BASE = 170;

    private MenuAction() {
    }

    public static int faceNone(net.minecraft.core.Direction direction) {
        return FACE_NONE_BASE + direction.ordinal();
    }

    public static int faceExtract(net.minecraft.core.Direction direction) {
        return FACE_EXTRACT_BASE + direction.ordinal();
    }

    public static int faceInsert(net.minecraft.core.Direction direction) {
        return FACE_INSERT_BASE + direction.ordinal();
    }

    public static int facePriorityDown(net.minecraft.core.Direction direction) {
        return FACE_PRIORITY_DOWN_BASE + direction.ordinal();
    }

    public static int facePriorityUp(net.minecraft.core.Direction direction) {
        return FACE_PRIORITY_UP_BASE + direction.ordinal();
    }

    public static int faceRedstone(net.minecraft.core.Direction direction) {
        return FACE_REDSTONE_BASE + direction.ordinal();
    }

    public static int faceSelect(net.minecraft.core.Direction direction) {
        return FACE_SELECT_BASE + direction.ordinal();
    }

    public static int filterAttribute(int attribute) {
        return FILTER_ATTRIBUTE_BASE + attribute;
    }
}
