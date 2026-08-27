package com.skylogistics.compat.distributor;

/** Fixed-rate watchdog used to recover distributor routes from stale runtime state. */
public final class DistributorFallbackSchedule {
    private long nextRun = Long.MIN_VALUE;

    public boolean consume(long gameTime, int intervalTicks) {
        if (nextRun != Long.MIN_VALUE && gameTime < nextRun) return false;
        nextRun = gameTime + Math.max(1, intervalTicks);
        return true;
    }

    public void reset() {
        nextRun = Long.MIN_VALUE;
    }
}
