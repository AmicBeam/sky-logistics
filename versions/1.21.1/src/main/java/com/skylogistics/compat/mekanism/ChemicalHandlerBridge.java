package com.skylogistics.compat.mekanism;

public interface ChemicalHandlerBridge {
    int getTanks();

    ChemicalStackView getChemicalInTank(int tank);

    ChemicalStackView extractChemical(int tank, long amount, boolean simulate);

    long insertChemical(ChemicalStackView stack, boolean simulate);
}
