package com.skylogistics.compat.distributor;

/** Pure assignment policy for maintained energy insertion across distributor devices. */
public final class DistributorEnergyMaintenancePolicy {
    private DistributorEnergyMaintenancePolicy() {
    }

    public static int[] assignments(int requested, int[] stored, int[] capacities,
            boolean maintainByAmount, long maintainTarget, boolean fillMaintainedUnits, boolean sequential) {
        int count = Math.min(stored == null ? 0 : stored.length, capacities == null ? 0 : capacities.length);
        int[] allowed = new int[count];
        if (requested <= 0 || maintainTarget <= 0L || count <= 0) return allowed;
        if (!sequential) {
            for (int index = 0; index < count; index++) {
                int capacity = Math.max(0, capacities[index]);
                if (maintainByAmount) {
                    allowed[index] = (int)Math.min(capacity,
                            Math.max(0L, maintainTarget - Math.max(0, stored[index])));
                } else {
                    int occupied = stored[index] > 0 ? 1 : 0;
                    if (occupied < maintainTarget || fillMaintainedUnits && occupied == maintainTarget) {
                        allowed[index] = capacity;
                    }
                }
            }
            return DistributorInsertMode.balancedAssignments(requested, allowed);
        }
        long totalStored = 0L;
        int occupied = 0;
        for (int index = 0; index < count; index++) {
            int current = Math.max(0, stored[index]);
            totalStored += current;
            if (current > 0) occupied++;
        }
        int limitedRequest = requested;
        if (maintainByAmount) {
            limitedRequest = (int)Math.min(requested, Math.max(0L, maintainTarget - totalStored));
            for (int index = 0; index < count; index++) allowed[index] = Math.max(0, capacities[index]);
        } else {
            int emptyUnits = Math.max(0, (int)Math.min(Integer.MAX_VALUE, maintainTarget) - occupied);
            for (int index = 0; index < count; index++) {
                boolean existing = stored[index] > 0;
                if (existing && (occupied < maintainTarget || fillMaintainedUnits)) {
                    allowed[index] = Math.max(0, capacities[index]);
                } else if (!existing && emptyUnits > 0) {
                    allowed[index] = Math.max(0, capacities[index]);
                    emptyUnits--;
                }
            }
        }
        if (limitedRequest <= 0) return new int[count];
        int[] assigned = new int[count];
        int remaining = limitedRequest;
        for (int index = 0; index < count && remaining > 0; index++) {
            assigned[index] = Math.min(remaining, allowed[index]);
            remaining -= assigned[index];
        }
        return assigned;
    }
}
