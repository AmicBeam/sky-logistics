package com.skylogistics.compat.distributor;

/** Calculates how much of one insertion request should be offered to the next target. */
public final class DistributorInsertMode {
    private DistributorInsertMode() {}

    public static int offer(int requested, int targetCount, boolean sequential) {
        if (requested <= 0 || targetCount <= 0) return 0;
        return sequential ? requested : (int)(((long)requested + targetCount - 1L) / targetCount);
    }

    public static long offer(long requested, int targetCount, boolean sequential) {
        if (requested <= 0L || targetCount <= 0) return 0L;
        return sequential ? requested : 1L + (requested - 1L) / targetCount;
    }

    /** Exact balanced quota for one zero-based target rank; earlier ranks receive the remainder. */
    public static int balancedOffer(int requested, int targetCount, int targetRank) {
        if (requested <= 0 || targetCount <= 0 || targetRank < 0 || targetRank >= targetCount) return 0;
        return requested / targetCount + (targetRank < requested % targetCount ? 1 : 0);
    }

    public static long balancedOffer(long requested, int targetCount, int targetRank) {
        if (requested <= 0L || targetCount <= 0 || targetRank < 0 || targetRank >= targetCount) return 0L;
        return requested / targetCount + (targetRank < requested % targetCount ? 1L : 0L);
    }

    /** Balances a request across target capacities and redistributes any share a smaller target cannot use. */
    public static int[] balancedAssignments(int requested, int[] capacities) {
        int[] assigned = new int[capacities == null ? 0 : capacities.length];
        int remaining = Math.max(0, requested);
        while (remaining > 0) {
            int active = 0;
            for (int index = 0; index < assigned.length; index++)
                if (assigned[index] < Math.max(0, capacities[index])) active++;
            if (active <= 0) break;
            int distributed = 0;
            int activeRank = 0;
            int roundRequest = remaining;
            for (int index = 0; index < assigned.length && remaining > 0; index++) {
                int capacity = Math.max(0, capacities[index]);
                if (assigned[index] >= capacity) continue;
                int share = balancedOffer(roundRequest, active, activeRank++);
                int amount = Math.min(share, capacity - assigned[index]);
                if (amount <= 0) continue;
                assigned[index] += amount;
                remaining -= amount;
                distributed += amount;
            }
            if (distributed <= 0) break;
        }
        return assigned;
    }

    public static long[] balancedAssignments(long requested, long[] capacities) {
        long[] assigned = new long[capacities == null ? 0 : capacities.length];
        long remaining = Math.max(0L, requested);
        while (remaining > 0L) {
            int active = 0;
            for (int index = 0; index < assigned.length; index++)
                if (assigned[index] < Math.max(0L, capacities[index])) active++;
            if (active <= 0) break;
            long distributed = 0L;
            int activeRank = 0;
            long roundRequest = remaining;
            for (int index = 0; index < assigned.length && remaining > 0L; index++) {
                long capacity = Math.max(0L, capacities[index]);
                if (assigned[index] >= capacity) continue;
                long share = balancedOffer(roundRequest, active, activeRank++);
                long amount = Math.min(share, capacity - assigned[index]);
                if (amount <= 0L) continue;
                assigned[index] += amount;
                remaining -= amount;
                distributed += amount;
            }
            if (distributed <= 0L) break;
        }
        return assigned;
    }
}
