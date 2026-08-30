package com.skylogistics.compat.distributor;

/** Optional aggregate view used to apply maintained slot/amount limits to proxy handlers. */
public interface MaintainedStorageView {
    long maintainedStoredAmount();

    int maintainedOccupiedStorageUnits();

    default long maintainedExistingUnitRefillCapacity() {
        return 0L;
    }
}
