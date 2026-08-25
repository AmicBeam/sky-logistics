package com.skylogistics.network;

/**
 * Decides when source-slot caching must yield to the node's configured scan rate.
 */
public final class ItemSourceScanPolicy {
    private ItemSourceScanPolicy() {
    }

    public static boolean scansEverySlot(int slots, int operationRate) {
        return slots > 0 && operationRate >= slots;
    }
}
