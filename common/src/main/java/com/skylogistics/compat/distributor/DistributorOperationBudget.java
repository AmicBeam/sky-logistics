package com.skylogistics.compat.distributor;

/**
 * Shares the active logistics-line operation budget with distributor proxy handlers.
 * Calls outside the logistics ticker remain unbudgeted because they do not belong to a line.
 */
public final class DistributorOperationBudget {
    private static final ThreadLocal<State> ACTIVE = new ThreadLocal<>();

    private DistributorOperationBudget() {
    }

    public static Scope open(int operations) {
        State state = ACTIVE.get();
        if (state != null) return new Scope(state, false, state.consumed);
        state = new State(Math.max(0, operations));
        ACTIVE.set(state);
        return new Scope(state, true, 0);
    }

    public static boolean takeOperation() {
        State state = ACTIVE.get();
        if (state == null) return true;
        if (state.remaining <= 0) {
            state.blocked = true;
            return false;
        }
        state.remaining--;
        state.consumed++;
        return true;
    }

    public static boolean exhausted() {
        State state = ACTIVE.get();
        return state != null && state.blocked;
    }

    public static final class Scope implements AutoCloseable {
        private final State state;
        private final boolean owner;
        private final int consumedBefore;
        private boolean closed;

        private Scope(State state, boolean owner, int consumedBefore) {
            this.state = state;
            this.owner = owner;
            this.consumedBefore = consumedBefore;
        }

        public int consumedOperations() {
            return state.consumed - consumedBefore;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (owner) ACTIVE.remove();
        }
    }

    private static final class State {
        private int remaining;
        private int consumed;
        private boolean blocked;

        private State(int remaining) {
            this.remaining = remaining;
        }
    }
}
