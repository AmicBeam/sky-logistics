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
    public static final int LINE_FIRST = 14;
    public static final int LINE_PREVIOUS = 15;
    public static final int LINE_NEXT_OR_CREATE = 16;
    public static final int LINE_LAST = 17;
    public static final int LINE_REMOVE_CURRENT = 18;
    public static final int FILTER_SET_WHITELIST = 20;
    public static final int FILTER_SET_BLACKLIST = 21;
    public static final int FILTER_CLEAR = 22;
    public static final int FILTER_SET_NBT_ON = 23;
    public static final int FILTER_SET_NBT_OFF = 24;
    public static final int FILTER_SET_DURABILITY_ON = 25;
    public static final int FILTER_SET_DURABILITY_OFF = 26;
    public static final int NECKLACE_INSERT_SLOTS_DOWN = 30;
    public static final int NECKLACE_INSERT_SLOTS_UP = 31;
    public static final int CONFIG_PRIORITY_DOWN_FAST = 32;
    public static final int CONFIG_PRIORITY_UP_FAST = 33;
    public static final int NECKLACE_INSERT_SLOTS_DOWN_FAST = 34;
    public static final int NECKLACE_INSERT_SLOTS_UP_FAST = 35;
    public static final int NECKLACE_PRIORITY_DOWN = 36;
    public static final int NECKLACE_PRIORITY_UP = 37;
    public static final int NECKLACE_PRIORITY_DOWN_FAST = 38;
    public static final int NECKLACE_PRIORITY_UP_FAST = 39;
    public static final int FACE_NONE_BASE = 100;
    public static final int FACE_EXTRACT_BASE = 110;
    public static final int FACE_INSERT_BASE = 120;
    public static final int FACE_PRIORITY_DOWN_BASE = 140;
    public static final int FACE_PRIORITY_UP_BASE = 150;
    public static final int FACE_REDSTONE_BASE = 160;
    public static final int FACE_SELECT_BASE = 170;
    public static final int FACE_PRIORITY_DOWN_FAST_BASE = 180;
    public static final int FACE_PRIORITY_UP_FAST_BASE = 190;

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

    public static int facePriorityDownFast(net.minecraft.core.Direction direction) {
        return FACE_PRIORITY_DOWN_FAST_BASE + direction.ordinal();
    }

    public static int facePriorityUpFast(net.minecraft.core.Direction direction) {
        return FACE_PRIORITY_UP_FAST_BASE + direction.ordinal();
    }

    public static int faceRedstone(net.minecraft.core.Direction direction) {
        return FACE_REDSTONE_BASE + direction.ordinal();
    }

    public static int faceSelect(net.minecraft.core.Direction direction) {
        return FACE_SELECT_BASE + direction.ordinal();
    }

}
