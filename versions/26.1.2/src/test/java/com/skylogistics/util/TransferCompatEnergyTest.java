package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;

class TransferCompatEnergyTest {
    @Test
    void resourceAdapterRollsBackUncommittedChanges() {
        MutableEnergyStorage storage = new MutableEnergyStorage(100, 20);
        EnergyHandler handler = TransferCompat.energyHandler(storage);

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(30, handler.insert(30, transaction));
            assertEquals(50, storage.getEnergyStored());
        }

        assertEquals(20, storage.getEnergyStored());
    }

    @Test
    void resourceAdapterCommitsChanges() {
        MutableEnergyStorage storage = new MutableEnergyStorage(100, 20);
        EnergyHandler handler = TransferCompat.energyHandler(storage);

        try (Transaction transaction = Transaction.openRoot()) {
            assertEquals(30, handler.insert(30, transaction));
            transaction.commit();
        }

        assertEquals(50, storage.getEnergyStored());
    }

    @Test
    void schedulerViewKeepsSimulationSideEffectFree() {
        MutableEnergyStorage storage = new MutableEnergyStorage(100, 20);
        EnergyStorage schedulerView = TransferCompat.energyStorage(TransferCompat.energyHandler(storage));

        assertEquals(30, schedulerView.receiveEnergy(30, true));
        assertEquals(20, storage.getEnergyStored());
        assertEquals(30, schedulerView.receiveEnergy(30, false));
        assertEquals(50, storage.getEnergyStored());
    }

    private static final class MutableEnergyStorage implements EnergyStorage {
        private final int capacity;
        private int amount;

        private MutableEnergyStorage(int capacity, int amount) {
            this.capacity = capacity;
            this.amount = amount;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = Math.min(maxReceive, capacity - amount);
            if (!simulate) {
                amount += received;
            }
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = Math.min(maxExtract, amount);
            if (!simulate) {
                amount -= extracted;
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return amount;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public boolean canExtract() {
            return amount > 0;
        }

        @Override
        public boolean canReceive() {
            return amount < capacity;
        }
    }
}
