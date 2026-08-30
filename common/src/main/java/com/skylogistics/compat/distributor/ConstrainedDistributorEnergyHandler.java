package com.skylogistics.compat.distributor;

/** Plans one distributor energy insertion while applying the node's maintain constraint. */
public interface ConstrainedDistributorEnergyHandler extends BudgetedDistributorHandler {
    int planMaintainedEnergyInsertion(int amount, boolean maintainByAmount, long maintainTarget,
            boolean fillMaintainedUnits);
}
