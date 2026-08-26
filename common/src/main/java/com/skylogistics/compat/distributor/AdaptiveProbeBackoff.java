package com.skylogistics.compat.distributor;

/** Shared four-tier retry policy for machine probes at every distributor routing layer. */
public final class AdaptiveProbeBackoff {
    public static final byte HOT = 0;
    public static final byte FALLBACK = 3;

    private final int[] intervals = {1, 5, 20, 40};
    private int missesPerDemotion = 3;

    public void configure(int hotTicks, int warmTicks, int coolTicks, int fallbackTicks,
            int configuredMissesPerDemotion) {
        intervals[HOT] = Math.max(1, hotTicks);
        intervals[1] = Math.max(intervals[HOT], warmTicks);
        intervals[2] = Math.max(intervals[1], coolTicks);
        intervals[FALLBACK] = Math.max(intervals[2], fallbackTicks);
        missesPerDemotion = Math.max(1, configuredMissesPerDemotion);
    }

    public int interval(byte tier) {
        return intervals[Math.max(HOT, Math.min(FALLBACK, tier))];
    }

    public boolean shouldDemote(int consecutiveTierMisses) {
        return consecutiveTierMisses >= missesPerDemotion;
    }

    public byte demote(byte tier) {
        return (byte)Math.min(FALLBACK, tier + 1);
    }
}
