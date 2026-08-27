package com.skylogistics.compat.industrialforegoingsouls;

public interface SoulHandlerBridge {
    int getSoulTanks();

    int getSoulInTank(int tank);

    int getTankCapacity(int tank);

    int fill(int amount, boolean simulate);

    int drain(int amount, boolean simulate);
}
