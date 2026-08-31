package com.skylogistics.compat.distributor;

/** Builds a maintained scalar-resource insertion plan against each bound device. */
public interface ConstrainedDistributorAmountHandler extends BudgetedDistributorHandler {
    long planMaintainedInsertion(long amount, boolean maintainByAmount, long maintainTarget,
            boolean fillMaintainedUnits);
}
