package com.skylogistics.compat.distributor;

import com.skylogistics.compat.mekanism.ChemicalStackView;

public enum EmptyChemicalStackView implements ChemicalStackView {
    INSTANCE;

    @Override public boolean isEmpty() { return true; }
    @Override public long getAmount() { return 0L; }
    @Override public ChemicalStackView copyWithAmount(long amount) { return this; }
    @Override public boolean isSameChemical(ChemicalStackView other) { return other == this; }
}
