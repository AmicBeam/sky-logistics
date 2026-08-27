package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DistributedChemicalHandlerTest {
    @Test void exposesWhetherItsTargetIndexIsStillBeingBuilt() {
        Lookup lookup = new Lookup(1);
        DistributedChemicalHandler distributed = new DistributedChemicalHandler(lookup);

        assertFalse(distributed.distributorScanPending());
        lookup.scanPending = true;
        assertTrue(distributed.distributorScanPending());
    }

    @Test void twoUnitBatchesRemainFairAcrossFourAcceptingMachines() {
        Lookup lookup = new Lookup(4);
        DistributedChemicalHandler distributed = new DistributedChemicalHandler(lookup);
        TestStack batch = new TestStack("steam", 2);

        for (int transfer = 0; transfer < 100; transfer++) {
            lookup.tick++;
            long accepted = distributed.insertChemical(batch, true);
            distributed.insertChemical(batch.copyWithAmount(accepted), false);
        }

        long min = Arrays.stream(lookup.handlers).mapToLong(handler -> handler.stored).min().orElseThrow();
        long max = Arrays.stream(lookup.handlers).mapToLong(handler -> handler.stored).max().orElseThrow();
        assertTrue(max - min <= 2L, "machine totals=" + Arrays.toString(
                Arrays.stream(lookup.handlers).mapToLong(handler -> handler.stored).toArray()));
    }

    private static final class Lookup implements DistributedHandlerLookup<ChemicalHandlerBridge> {
        private final TargetHandler[] handlers;
        private long tick;
        private boolean scanPending;

        private Lookup(int targets) {
            handlers = new TargetHandler[targets];
            Arrays.setAll(handlers, ignored -> new TargetHandler());
        }

        @Override public int size() { return handlers.length; }
        @Override public ChemicalHandlerBridge handler(int index) { return handlers[index]; }
        @Override public boolean takeOperation() { return true; }
        @Override public boolean scanPending() { return scanPending; }
        @Override public long gameTime() { return tick; }
        @Override public AdaptiveRoutingConfig adaptiveRoutingConfig() {
            return new AdaptiveRoutingConfig(true, 16, 1, 5, 20, 40, 3);
        }
    }

    private static final class TargetHandler implements ChemicalHandlerBridge {
        private long stored;
        @Override public int getTanks() { return 1; }
        @Override public ChemicalStackView getChemicalInTank(int tank) { return new TestStack("steam", stored); }
        @Override public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
            long extracted = Math.min(stored, amount);
            if (!simulate) stored -= extracted;
            return new TestStack("steam", extracted);
        }
        @Override public long insertChemical(ChemicalStackView stack, boolean simulate) {
            if (!simulate) stored += stack.getAmount();
            return stack.getAmount();
        }
    }

    private record TestStack(String key, long amount) implements ChemicalStackView {
        @Override public boolean isEmpty() { return amount <= 0L; }
        @Override public long getAmount() { return amount; }
        @Override public ChemicalStackView copyWithAmount(long copiedAmount) { return new TestStack(key, copiedAmount); }
        @Override public boolean isSameChemical(ChemicalStackView other) {
            return other instanceof TestStack stack && key.equals(stack.key);
        }
        @Override public String chemicalKey() { return key; }
    }
}
