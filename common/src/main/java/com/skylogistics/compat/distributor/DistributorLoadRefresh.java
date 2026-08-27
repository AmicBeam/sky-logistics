package com.skylogistics.compat.distributor;

/** Defers distributor target discovery until the first server tick after block-entity loading. */
public final class DistributorLoadRefresh {
    private boolean pending;

    public void schedule() {
        pending = true;
    }

    public boolean consume() {
        if (!pending) return false;
        pending = false;
        return true;
    }
}
