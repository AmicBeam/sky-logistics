package com.skylogistics.compat.distributor;

/** Pure maintenance arithmetic shared by all distributor implementations. */
public final class DistributorMaintenancePolicy {
    private DistributorMaintenancePolicy() {}

    public static int remainingItems(long current, int target) {
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, (long)Math.max(0, target) - current));
    }

    public static int remainingSlots(int current, int target) {
        return Math.max(0, Math.max(0, target) - Math.max(0, current));
    }

    public static boolean blocksSlotInsertion(int current, int target, boolean fillExisting) {
        return current > target || current == target && !fillExisting;
    }
}
