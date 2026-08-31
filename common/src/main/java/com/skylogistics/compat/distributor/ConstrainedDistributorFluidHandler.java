package com.skylogistics.compat.distributor;

/** Builds a maintained fluid insertion plan against each bound device. */
public interface ConstrainedDistributorFluidHandler<T> extends BudgetedDistributorHandler {
    int planMaintainedFluidInsertion(T resource, boolean maintainByAmount, long maintainTarget,
            boolean fillMaintainedUnits);
}
