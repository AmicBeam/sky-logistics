package com.skylogistics.compat.mekanism;

public interface ChemicalStackView {
    boolean isEmpty();

    long getAmount();

    ChemicalStackView copyWithAmount(long amount);

    boolean isSameChemical(ChemicalStackView other);

    default Object rawStack() {
        return null;
    }
}
