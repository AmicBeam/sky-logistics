package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.skylogistics.compat.industrialforegoingsouls.SoulHandlerBridge;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistributedSoulHandlerTest {
    @Test
    void exposesTanksAndMovesSoulsAcrossTargets() {
        FakeSoulHandler first = new FakeSoulHandler(3, 5);
        FakeSoulHandler second = new FakeSoulHandler(4, 8);
        DistributedSoulHandler distributed = new DistributedSoulHandler(new Lookup(List.of(first, second)));

        assertEquals(2, distributed.getSoulTanks());
        assertEquals(3, distributed.getSoulInTank(0));
        assertEquals(4, distributed.getSoulInTank(1));
        assertEquals(7, distributed.drain(7, true));
        assertEquals(3, first.amount);
        assertEquals(4, second.amount);
        assertEquals(7, distributed.drain(7, false));
        assertEquals(0, first.amount);
        assertEquals(0, second.amount);

        assertEquals(9, distributed.fill(9, false));
        assertEquals(5, first.amount);
        assertEquals(4, second.amount);
    }

    private static final class Lookup implements DistributedHandlerLookup<SoulHandlerBridge> {
        private final List<SoulHandlerBridge> handlers;

        private Lookup(List<SoulHandlerBridge> handlers) {
            this.handlers = handlers;
        }

        @Override public int size() { return handlers.size(); }
        @Override public SoulHandlerBridge handler(int index) { return handlers.get(index); }
        @Override public boolean takeOperation() { return true; }
    }

    private static final class FakeSoulHandler implements SoulHandlerBridge {
        private int amount;
        private final int capacity;

        private FakeSoulHandler(int amount, int capacity) {
            this.amount = amount;
            this.capacity = capacity;
        }

        @Override public int getSoulTanks() { return 1; }
        @Override public int getSoulInTank(int tank) { return tank == 0 ? amount : 0; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? capacity : 0; }
        @Override public int fill(int requested, boolean simulate) {
            int accepted = Math.min(requested, capacity - amount);
            if (!simulate) amount += accepted;
            return accepted;
        }
        @Override public int drain(int requested, boolean simulate) {
            int extracted = Math.min(requested, amount);
            if (!simulate) amount -= extracted;
            return extracted;
        }
    }
}
