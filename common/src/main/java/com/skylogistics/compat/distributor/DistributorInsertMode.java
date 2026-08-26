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
}
