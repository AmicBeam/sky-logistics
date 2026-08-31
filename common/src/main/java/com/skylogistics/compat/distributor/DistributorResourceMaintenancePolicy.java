package com.skylogistics.compat.distributor;

/** Pure per-device maintenance and assignment policy for distributor resources. */
public final class DistributorResourceMaintenancePolicy {
    private DistributorResourceMaintenancePolicy() {}

    public static long[] assignments(long requested, long[] stored, long[] capacities,
            int[] occupiedUnits, long[] existingRefill, boolean maintainByAmount, long maintainTarget,
            boolean fillMaintainedUnits, boolean sequential) {
        int count = minimumLength(stored, capacities, occupiedUnits, existingRefill);
        long[] allowed = new long[count];
        if (requested <= 0L || maintainTarget <= 0L || count <= 0) return allowed;
        if (!sequential) {
            for (int index = 0; index < count; index++) {
                allowed[index] = allowance(capacities[index], stored[index], occupiedUnits[index],
                        existingRefill[index], maintainByAmount, maintainTarget, fillMaintainedUnits);
            }
            return DistributorInsertMode.balancedAssignments(requested, allowed);
        }
        long totalStored = 0L;
        long totalRefill = 0L;
        int totalOccupied = 0;
        for (int index = 0; index < count; index++) {
            totalStored = saturatedAdd(totalStored, Math.max(0L, stored[index]));
            totalRefill = saturatedAdd(totalRefill, Math.max(0L, existingRefill[index]));
            totalOccupied = (int)Math.min(Integer.MAX_VALUE,
                    (long)totalOccupied + Math.max(0, occupiedUnits[index]));
            allowed[index] = Math.max(0L, capacities[index]);
        }
        long remaining = allowance(requested, totalStored, totalOccupied, totalRefill,
                maintainByAmount, maintainTarget, fillMaintainedUnits);
        long[] assigned = new long[count];
        for (int index = 0; index < count && remaining > 0L; index++) {
            assigned[index] = Math.min(remaining, allowed[index]);
            remaining -= assigned[index];
        }
        return assigned;
    }

    private static long allowance(long capacity, long stored, int occupied, long refill,
            boolean byAmount, long target, boolean fill) {
        capacity = Math.max(0L, capacity);
        if (byAmount) return Math.min(capacity, Math.max(0L, target - Math.max(0L, stored)));
        occupied = Math.max(0, occupied);
        if (occupied < target) return capacity;
        return fill && occupied == target ? Math.min(capacity, Math.max(0L, refill)) : 0L;
    }

    private static int minimumLength(long[] stored, long[] capacities, int[] occupied, long[] refill) {
        if (stored == null || capacities == null || occupied == null || refill == null) return 0;
        return Math.min(Math.min(stored.length, capacities.length), Math.min(occupied.length, refill.length));
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }
}
