package com.skylogistics.util;

public final class ItemSourceSlotCachePolicy {
    private ItemSourceSlotCachePolicy() {}

    public static boolean usesHotSlots(int totalSlots) {
        return totalSlots > 1;
    }

    public static int hotSlotCapacity(int configuredCapacity, int totalSlots) {
        if (!usesHotSlots(totalSlots)) return 0;
        return Math.min(configuredCapacity, totalSlots);
    }
}
