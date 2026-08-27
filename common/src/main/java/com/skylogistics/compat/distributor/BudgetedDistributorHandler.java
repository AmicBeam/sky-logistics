package com.skylogistics.compat.distributor;

/** Marks a distributor proxy whose current tick budget stopped an otherwise incomplete scan. */
public interface BudgetedDistributorHandler {
    boolean distributorBudgetExhausted();

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
}
