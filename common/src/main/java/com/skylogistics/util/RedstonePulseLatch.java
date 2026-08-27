package com.skylogistics.util;

/** Tracks one face's rising-edge redstone trigger without treating chunk load as an edge. */
public final class RedstonePulseLatch {
    private boolean initialized;
    private boolean powered;
    private boolean armed;

    /**
     * Samples the current signal. The first sample establishes a baseline; later rising edges arm the latch.
     *
     * @return {@code true} when this sample newly armed the latch
     */
    public boolean sample(boolean powered) {
        boolean newlyArmed = initialized && powered && !this.powered && !armed;
        if (initialized && powered && !this.powered) {
            armed = true;
        }
        this.powered = powered;
        initialized = true;
        return newlyArmed;
    }

    public boolean isArmed() {
        return armed;
    }

    /**
     * Consumes a pending pulse after one successful transfer.
     *
     * @return {@code true} when an armed pulse was consumed
     */
    public boolean consume() {
        if (!armed) {
            return false;
        }
        armed = false;
        return true;
    }

    public void reset(boolean powered) {
        initialized = true;
        this.powered = powered;
        armed = false;
    }

    public void restoreArmed(boolean armed) {
        initialized = false;
        this.armed = armed;
    }
}
