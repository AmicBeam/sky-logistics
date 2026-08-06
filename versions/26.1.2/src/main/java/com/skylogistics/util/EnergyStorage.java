package com.skylogistics.util;

/** Scheduler-facing energy storage view for the Minecraft 26.1 transfer API. */
public interface EnergyStorage {
    int receiveEnergy(int maxReceive, boolean simulate);

    int extractEnergy(int maxExtract, boolean simulate);

    int getEnergyStored();

    int getMaxEnergyStored();

    boolean canExtract();

    boolean canReceive();
}
