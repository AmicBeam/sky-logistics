package com.skylogistics.compat.distributor;

import com.skylogistics.util.OrderedMatchingPolicy;

/** Immutable node-side constraints attached to one distributor item insertion plan. */
public record DistributorItemInsertContext(MaintainUnit maintainUnit, int maintainAmount,
        boolean fillMaintainedSlots, boolean orderedMatching, int sourcePosition, int orderedOffset) {
    public enum MaintainUnit {
        NONE,
        SLOTS,
        ITEMS
    }

    public DistributorItemInsertContext {
        maintainUnit = maintainUnit == null ? MaintainUnit.NONE : maintainUnit;
        maintainAmount = Math.max(0, maintainAmount);
    }

    public static DistributorItemInsertContext unrestricted() {
        return new DistributorItemInsertContext(MaintainUnit.NONE, 0, false, false, -1, 0);
    }

    public boolean maintained() {
        return maintainUnit != MaintainUnit.NONE && maintainAmount > 0;
    }

    /** Resolves the ordered-matching position against either local slots or distributor devices. */
    public int orderedPosition(int positionCount) {
        if (!orderedMatching) return -1;
        return OrderedMatchingPolicy.offsetPosition(sourcePosition, orderedOffset, positionCount);
    }
}
