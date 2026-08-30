package com.skylogistics.compat.distributor;

/** Guards transfer access until a complete distributor target index has been published. */
public final class DistributorIndexPolicy {
    private DistributorIndexPolicy() {
    }

    public static boolean transferBlocked(boolean completeIndexPublished, boolean dirty,
            boolean discoveryPending) {
        return !completeIndexPublished || dirty || discoveryPending;
    }
}
