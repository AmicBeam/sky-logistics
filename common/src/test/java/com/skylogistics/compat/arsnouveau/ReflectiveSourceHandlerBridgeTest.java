package com.skylogistics.compat.arsnouveau;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReflectiveSourceHandlerBridgeTest {
    @Test
    void measuresStateWhenMethodsReturnStoredAmount() {
        assertTransfers(new StoredAmountSource(80, 100));
    }

    @Test
    void measuresStateWhenMethodsReturnTransferredAmount() {
        assertTransfers(new TransferredAmountSource(80, 100));
    }

    private static void assertTransfers(Object source) {
        SourceHandlerBridge bridge = ReflectiveSourceHandlerBridge.create(source);
        assertEquals(30, bridge.extractSource(30, true));
        assertEquals(80, bridge.getCurrentSource());
        assertEquals(30, bridge.extractSource(30, false));
        assertEquals(50, bridge.getCurrentSource());
        assertEquals(40, bridge.insertSource(40, true));
        assertEquals(50, bridge.getCurrentSource());
        assertEquals(40, bridge.insertSource(40, false));
        assertEquals(90, bridge.getCurrentSource());
    }

    private abstract static class TestSource {
        protected int stored;
        private final int capacity;

        private TestSource(int stored, int capacity) {
            this.stored = stored;
            this.capacity = capacity;
        }

        public int getSource() {
            return stored;
        }

        public int getMaxSource() {
            return capacity;
        }

        public boolean canAcceptSource() {
            return stored < capacity;
        }
    }

    private static final class StoredAmountSource extends TestSource {
        private StoredAmountSource(int stored, int capacity) {
            super(stored, capacity);
        }

        public int addSource(int amount) {
            stored = Math.min(getMaxSource(), stored + amount);
            return stored;
        }

        public int removeSource(int amount) {
            stored = Math.max(0, stored - amount);
            return stored;
        }
    }

    private static final class TransferredAmountSource extends TestSource {
        private TransferredAmountSource(int stored, int capacity) {
            super(stored, capacity);
        }

        public int addSource(int amount) {
            int moved = Math.min(amount, getMaxSource() - stored);
            stored += moved;
            return moved;
        }

        public int removeSource(int amount) {
            int moved = Math.min(amount, stored);
            stored -= moved;
            return moved;
        }
    }
}
