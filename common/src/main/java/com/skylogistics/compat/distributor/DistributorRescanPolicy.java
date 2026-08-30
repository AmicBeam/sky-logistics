package com.skylogistics.compat.distributor;

/** Decides whether an active distributor side needs background discovery work. */
public final class DistributorRescanPolicy {
    private DistributorRescanPolicy() {
    }

    public static boolean shouldScan(boolean active, boolean dirty, boolean discoveryPending,
            long gameTime, long nextRescan) {
        return active && (dirty || discoveryPending || gameTime >= nextRescan);
    }
}
