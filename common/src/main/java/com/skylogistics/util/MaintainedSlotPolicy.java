package com.skylogistics.util;

public final class MaintainedSlotPolicy {
    private MaintainedSlotPolicy() {
    }

    public static boolean shouldInsert(boolean maintainByItems, int current, int target,
            boolean fillMaintainedSlots) {
        return current < target || !maintainByItems && current == target && fillMaintainedSlots;
    }

    public static boolean tracksRefillCandidate(boolean fillMaintainedSlots) {
        return fillMaintainedSlots;
    }

    public static boolean blocksInsertionAtSlotLimit(boolean fillMaintainedSlots, int matchingSlots,
            int slotLimit, boolean targetSlotEmpty) {
        return matchingSlots >= slotLimit && (!fillMaintainedSlots || targetSlotEmpty);
    }
}
