package com.skylogistics.compat.botania;

public interface ManaHandlerBridge {
    int getCurrentMana();

    int getMaxMana();

    boolean canExtract();

    boolean canReceive();

    int extractMana(int amount, boolean simulate);

    int insertMana(int amount, boolean simulate);
}
