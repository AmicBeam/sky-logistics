package com.skylogistics.compat.distributor;

import com.skylogistics.compat.mekanism.ChemicalStackView;

/** Builds a maintained chemical insertion plan against each bound device. */
public interface ConstrainedDistributorChemicalHandler extends BudgetedDistributorHandler {
    long planMaintainedChemicalInsertion(ChemicalStackView stack, boolean maintainByAmount,
            long maintainTarget, boolean fillMaintainedUnits);
}
