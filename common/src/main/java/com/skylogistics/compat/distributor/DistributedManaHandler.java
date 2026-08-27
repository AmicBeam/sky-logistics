package com.skylogistics.compat.distributor;

import com.skylogistics.compat.botania.ManaHandlerBridge;

public final class DistributedManaHandler implements ManaHandlerBridge {
    private final DistributedHandlerLookup<ManaHandlerBridge> lookup;
    private int insertCursor;
    private int extractCursor;

    public DistributedManaHandler(DistributedHandlerLookup<ManaHandlerBridge> lookup) { this.lookup = lookup; }

    @Override public int getCurrentMana() { return snapshot(true); }
    @Override public int getMaxMana() { return snapshot(false); }
    @Override public boolean canExtract() { return firstCapable(false) >= 0; }
    @Override public boolean canReceive() { return firstCapable(true) >= 0; }
    @Override public int extractMana(int amount, boolean simulate) { return move(amount, simulate, false); }
    @Override public int insertMana(int amount, boolean simulate) { return move(amount, simulate, true); }

    private int snapshot(boolean stored) {
        long total = 0L;
        for (int i = 0; i < lookup.size(); i++) {
            if (!lookup.takeOperation()) break;
            ManaHandlerBridge handler = lookup.handler(i);
            if (handler != null) total += stored ? handler.getCurrentMana() : handler.getMaxMana();
        }
        return (int)Math.min(Integer.MAX_VALUE, total);
    }

    private int firstCapable(boolean receive) {
        for (int i = 0; i < lookup.size(); i++) {
            if (!lookup.takeOperation()) break;
            ManaHandlerBridge handler = lookup.handler(i);
            if (handler != null && (receive ? handler.canReceive() : handler.canExtract())) return i;
        }
        return -1;
    }

    private int move(int amount, boolean simulate, boolean receive) {
        int targets = lookup.size();
        if (amount <= 0 || targets <= 0) return 0;
        int cursor = receive ? insertCursor : extractCursor;
        int start = Math.floorMod(cursor, targets);
        int moved = 0;
        int share = receive ? DistributorInsertMode.offer(amount, targets, lookup.sequentialInsertion()) : amount;
        for (int offset = 0; offset < targets && moved < amount; offset++) {
            if (!lookup.takeOperation()) break;
            ManaHandlerBridge handler = lookup.handler((start + offset) % targets);
            if (handler == null) continue;
            int request = Math.min(share, amount - moved);
            moved += receive ? handler.insertMana(request, simulate)
                    : handler.extractMana(amount - moved, simulate);
        }
        if (!simulate) {
            if (receive) insertCursor = (start + 1) % targets;
            else extractCursor = (start + 1) % targets;
        }
        return Math.min(amount, moved);
    }
}
