package com.skylogistics.compat.distributor;

/** Exposes transient distributor work that must be retried without recording a transfer failure. */
public interface BudgetedDistributorHandler {
    boolean distributorBudgetExhausted();

    /** Returns true while the proxy is still rebuilding its target index. */
    default boolean distributorScanPending() {
        return false;
    }

    default boolean usesIndependentExtractionProbes() {
        return false;
    }

    /** Returns a virtual source slot whose target is due for a fairness probe, or {@code -1}. */
    default int nextFairExtractionSlot(long gameTime) {
        return -1;
    }

    /** Returns how many target-machine probes are currently due, independent of endpoint hot slots. */
    default int fairExtractionProbesDue(long gameTime) {
        return 0;
    }

    /** Caps adaptive source backoff while a maintained receiver still has demand. */
    default void setMaintainedExtractionPollTicks(int pollTicks) {
    }
}
