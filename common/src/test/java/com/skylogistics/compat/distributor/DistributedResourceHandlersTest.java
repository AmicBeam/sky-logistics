package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.compat.industrialforegoingsouls.SoulHandlerBridge;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistributedResourceHandlersTest {
    @Test void balancedMaintainedManaUsesEachDeviceTargetAndReusesPlan() {
        ScalarMana full = new ScalarMana(1000, 1200);
        ScalarMana low = new ScalarMana(500, 1200);
        DistributedManaHandler handler = new DistributedManaHandler(lookup(List.of(full, low), false));
        assertEquals(100, handler.planMaintainedInsertion(100, true, 1000, true));
        assertEquals(100, handler.insertMana(100, false));
        assertEquals(1000, full.stored);
        assertEquals(600, low.stored);
    }

    @Test void maintainedManaExecutionDoesNotRescanDevices() {
        CountingLookup<ManaHandlerBridge> lookup = new CountingLookup<>(List.of(
                new ScalarMana(0, 100), new ScalarMana(0, 100)), false);
        DistributedManaHandler handler = new DistributedManaHandler(lookup);
        assertEquals(80, handler.planMaintainedInsertion(80, true, 100, true));
        assertEquals(2, lookup.operations);
        assertEquals(80, handler.insertMana(80, false));
        assertEquals(2, lookup.operations);
    }

    @Test void sequentialMaintainedManaUsesAggregateTarget() {
        ScalarMana first = new ScalarMana(600, 1200);
        ScalarMana second = new ScalarMana(500, 1200);
        DistributedManaHandler handler = new DistributedManaHandler(lookup(List.of(first, second), true));
        assertEquals(0, handler.planMaintainedInsertion(100, true, 1000, true));
    }

    @Test void balancedMaintainedSourceUsesEachDeviceTarget() {
        ScalarSource full = new ScalarSource(1000, 1200);
        ScalarSource low = new ScalarSource(500, 1200);
        DistributedSourceHandler handler = new DistributedSourceHandler(lookup(List.of(full, low), false));
        assertEquals(100, handler.planMaintainedInsertion(100, true, 1000, true));
        assertEquals(100, handler.insertSource(100, false));
        assertEquals(1000, full.stored);
        assertEquals(600, low.stored);
    }

    @Test void balancedMaintainedChemicalUsesEachDeviceTarget() {
        ChemicalTank full = new ChemicalTank(1000, 1200);
        ChemicalTank low = new ChemicalTank(500, 1200);
        DistributedChemicalHandler handler = new DistributedChemicalHandler(lookup(List.of(full, low), false));
        assertEquals(100, handler.planMaintainedChemicalInsertion(new TestChemical(100), true, 1000, true));
        assertEquals(100, handler.insertChemical(new TestChemical(100), false));
        assertEquals(1000, full.stored);
        assertEquals(600, low.stored);
    }

    @Test void balancedMaintainedSoulsUseEachDeviceTarget() {
        SoulTank full = new SoulTank(1000, 1200);
        SoulTank low = new SoulTank(500, 1200);
        DistributedSoulHandler handler = new DistributedSoulHandler(lookup(List.of(full, low), false));
        assertEquals(100, handler.planMaintainedInsertion(100, true, 1000, true));
        assertEquals(100, handler.fill(100, false));
        assertEquals(1000, full.stored);
        assertEquals(600, low.stored);
    }

    @Test void redstoneModeSwitchesManaFromBalancedToSequentialInsertion() {
        ScalarMana balancedFirst = new ScalarMana(0, 100);
        ScalarMana balancedSecond = new ScalarMana(0, 100);
        DistributedManaHandler balanced = new DistributedManaHandler(
                lookup(List.of(balancedFirst, balancedSecond), false));
        assertEquals(80, balanced.insertMana(80, false));
        assertEquals(40, balancedFirst.stored);
        assertEquals(40, balancedSecond.stored);

        ScalarMana sequentialFirst = new ScalarMana(0, 100);
        ScalarMana sequentialSecond = new ScalarMana(0, 100);
        DistributedManaHandler sequential = new DistributedManaHandler(
                lookup(List.of(sequentialFirst, sequentialSecond), true));
        assertEquals(80, sequential.insertMana(80, false));
        assertEquals(80, sequentialFirst.stored);
        assertEquals(0, sequentialSecond.stored);
        assertEquals(10, sequential.insertMana(10, false));
        assertEquals(80, sequentialFirst.stored);
        assertEquals(10, sequentialSecond.stored);
    }

    @Test void redstoneModeAdvancesChemicalCursorAfterEachInsertion() {
        ChemicalTank first = new ChemicalTank(0, 100);
        ChemicalTank second = new ChemicalTank(0, 100);
        DistributedChemicalHandler sequential = new DistributedChemicalHandler(
                lookup(List.of(first, second), true));

        assertEquals(80, sequential.insertChemical(new TestChemical(80), false));
        assertEquals(10, sequential.insertChemical(new TestChemical(10), false));
        assertEquals(80, first.stored);
        assertEquals(10, second.stored);
    }

    @Test void distributesManaAcrossTargetsWithoutMutatingSimulation() {
        ScalarMana first = new ScalarMana(60, 100);
        ScalarMana second = new ScalarMana(10, 100);
        DistributedManaHandler handler = new DistributedManaHandler(lookup(List.of(first, second)));

        assertEquals(70, handler.extractMana(70, true));
        assertEquals(70, handler.getCurrentMana());
        assertEquals(70, handler.extractMana(70, false));
        assertEquals(0, handler.getCurrentMana());
        assertEquals(130, handler.insertMana(130, false));
        assertEquals(130, handler.getCurrentMana());
    }

    @Test void distributesSourceAcrossTargets() {
        ScalarSource first = new ScalarSource(25, 50);
        ScalarSource second = new ScalarSource(25, 75);
        DistributedSourceHandler handler = new DistributedSourceHandler(lookup(List.of(first, second)));

        assertEquals(50, handler.getCurrentSource());
        assertEquals(125, handler.getMaxSource());
        assertEquals(50, handler.extractSource(80, false));
        assertEquals(0, handler.getCurrentSource());
        assertEquals(90, handler.insertSource(90, false));
        assertEquals(90, handler.getCurrentSource());
    }

    @Test void exposesAndMovesChemicalsThroughTargetTanks() {
        ChemicalTank first = new ChemicalTank(40, 100);
        ChemicalTank second = new ChemicalTank(10, 100);
        DistributedChemicalHandler handler = new DistributedChemicalHandler(lookup(List.of(first, second)));

        assertEquals(2, handler.getTanks());
        assertEquals(40, handler.getChemicalInTank(0).getAmount());
        assertEquals(30, handler.extractChemical(0, 30, false).getAmount());
        assertEquals(20, handler.insertChemical(new TestChemical(20), false));
        assertFalse(handler.getChemicalInTank(0).isEmpty());
    }

    private static <T> DistributedHandlerLookup<T> lookup(List<T> handlers) {
        return lookup(handlers, false);
    }

    private static <T> DistributedHandlerLookup<T> lookup(List<T> handlers, boolean sequential) {
        return new DistributedHandlerLookup<>() {
            @Override public int size() { return handlers.size(); }
            @Override public T handler(int index) { return handlers.get(index); }
            @Override public boolean takeOperation() { return true; }
            @Override public boolean sequentialInsertion() { return sequential; }
        };
    }

    private static final class CountingLookup<T> implements DistributedHandlerLookup<T> {
        private final List<T> handlers;
        private final boolean sequential;
        private int operations;
        private CountingLookup(List<T> handlers, boolean sequential) {
            this.handlers = handlers;
            this.sequential = sequential;
        }
        @Override public int size() { return handlers.size(); }
        @Override public T handler(int index) { return handlers.get(index); }
        @Override public boolean takeOperation() { operations++; return true; }
        @Override public boolean sequentialInsertion() { return sequential; }
    }

    private static final class ScalarMana implements ManaHandlerBridge {
        private int stored;
        private final int capacity;
        private ScalarMana(int stored, int capacity) { this.stored = stored; this.capacity = capacity; }
        @Override public int getCurrentMana() { return stored; }
        @Override public int getMaxMana() { return capacity; }
        @Override public boolean canExtract() { return stored > 0; }
        @Override public boolean canReceive() { return stored < capacity; }
        @Override public int extractMana(int amount, boolean simulate) {
            int moved = Math.min(amount, stored);
            if (!simulate) stored -= moved;
            return moved;
        }
        @Override public int insertMana(int amount, boolean simulate) {
            int moved = Math.min(amount, capacity - stored);
            if (!simulate) stored += moved;
            return moved;
        }
    }

    private static final class ScalarSource implements SourceHandlerBridge {
        private int stored;
        private final int capacity;
        private ScalarSource(int stored, int capacity) { this.stored = stored; this.capacity = capacity; }
        @Override public int getCurrentSource() { return stored; }
        @Override public int getMaxSource() { return capacity; }
        @Override public boolean canExtract() { return stored > 0; }
        @Override public boolean canReceive() { return stored < capacity; }
        @Override public int extractSource(int amount, boolean simulate) {
            int moved = Math.min(amount, stored);
            if (!simulate) stored -= moved;
            return moved;
        }
        @Override public int insertSource(int amount, boolean simulate) {
            int moved = Math.min(amount, capacity - stored);
            if (!simulate) stored += moved;
            return moved;
        }
    }

    private record TestChemical(long amount) implements ChemicalStackView {
        @Override public boolean isEmpty() { return amount <= 0L; }
        @Override public long getAmount() { return amount; }
        @Override public ChemicalStackView copyWithAmount(long amount) { return new TestChemical(amount); }
        @Override public boolean isSameChemical(ChemicalStackView other) { return other instanceof TestChemical; }
    }

    private static final class ChemicalTank implements ChemicalHandlerBridge {
        private long stored;
        private final long capacity;
        private ChemicalTank(long stored, long capacity) { this.stored = stored; this.capacity = capacity; }
        @Override public int getTanks() { return 1; }
        @Override public ChemicalStackView getChemicalInTank(int tank) { return new TestChemical(stored); }
        @Override public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
            long moved = Math.min(amount, stored);
            if (!simulate) stored -= moved;
            return new TestChemical(moved);
        }
        @Override public long insertChemical(ChemicalStackView stack, boolean simulate) {
            long moved = Math.min(stack.getAmount(), capacity - stored);
            if (!simulate) stored += moved;
            return moved;
        }
    }

    private static final class SoulTank implements SoulHandlerBridge {
        private int stored;
        private final int capacity;
        private SoulTank(int stored, int capacity) { this.stored = stored; this.capacity = capacity; }
        @Override public int getSoulTanks() { return 1; }
        @Override public int getSoulInTank(int tank) { return stored; }
        @Override public int getTankCapacity(int tank) { return capacity; }
        @Override public int fill(int amount, boolean simulate) {
            int moved = Math.min(amount, capacity - stored);
            if (!simulate) stored += moved;
            return moved;
        }
        @Override public int drain(int amount, boolean simulate) {
            int moved = Math.min(amount, stored);
            if (!simulate) stored -= moved;
            return moved;
        }
    }
}
