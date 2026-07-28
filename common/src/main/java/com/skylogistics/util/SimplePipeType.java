package com.skylogistics.util;

public enum SimplePipeType {
    ITEM(64),
    FLUID(10_000),
    ENERGY(131_072);

    private final int transferRate;

    SimplePipeType(int transferRate) {
        this.transferRate = transferRate;
    }

    public int transferRate() {
        return transferRate;
    }
}
