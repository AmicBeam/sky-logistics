package com.skylogistics.util;

public final class ConfiguratorUpgradeLayout {
    public static final int DOWN_SHIFT = 4;
    public static final int GROUP_Y = 121 + DOWN_SHIFT;
    public static final int GROUP_WIDTH = 118;
    public static final int UPGRADE_GROUP_X = 10;
    public static final int FILTER_GROUP_X = 132;
    public static final int INVENTORY_X = 50;
    public static final int INVENTORY_LABEL_Y = 155 + DOWN_SHIFT;
    public static final int INVENTORY_Y = 166 + DOWN_SHIFT;
    public static final int HOTBAR_Y = INVENTORY_Y + 58;
    public static final int SAMPLE_Y = GROUP_Y + 7;
    public static final int SAMPLE_SPACING = 20;

    private ConfiguratorUpgradeLayout() {}

    public static int sampleX(int groupX, int index, int count) {
        return groupX + GROUP_WIDTH / 2 - 8 + index * SAMPLE_SPACING - (count - 1) * SAMPLE_SPACING / 2;
    }
}
