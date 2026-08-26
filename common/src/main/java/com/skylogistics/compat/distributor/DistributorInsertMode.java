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
}
