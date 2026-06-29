package com.skylogistics.compat.arsnouveau;

public interface SourceHandlerBridge {
    int getCurrentSource();

    int getMaxSource();

    boolean canExtract();

    boolean canReceive();

    int extractSource(int amount, boolean simulate);

    int insertSource(int amount, boolean simulate);
}
