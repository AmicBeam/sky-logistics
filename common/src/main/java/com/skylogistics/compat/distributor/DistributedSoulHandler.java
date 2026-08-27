package com.skylogistics.compat.distributor;

import com.skylogistics.compat.industrialforegoingsouls.SoulHandlerBridge;

public final class DistributedSoulHandler implements SoulHandlerBridge {
    private final DistributedHandlerLookup<SoulHandlerBridge> lookup;
    private int insertCursor;
    private int extractCursor;

    public DistributedSoulHandler(DistributedHandlerLookup<SoulHandlerBridge> lookup) {
        this.lookup = lookup;
    }

    @Override
    public int getSoulTanks() {
        int tanks = 0;
        for (int i = 0; i < lookup.size(); i++) {
            if (!lookup.takeOperation()) break;
            SoulHandlerBridge handler = lookup.handler(i);
            if (handler != null) tanks += Math.max(0, handler.getSoulTanks());
        }
        return tanks;
    }

    @Override
    public int getSoulInTank(int tank) {
        TankTarget target = tank(tank);
        return target == null ? 0 : target.handler().getSoulInTank(target.tank());
    }

    @Override
    public int getTankCapacity(int tank) {
        TankTarget target = tank(tank);
        return target == null ? 0 : target.handler().getTankCapacity(target.tank());
    }

    @Override
    public int fill(int amount, boolean simulate) {
        return move(amount, simulate, true);
    }

    @Override
    public int drain(int amount, boolean simulate) {
        return move(amount, simulate, false);
    }

    private TankTarget tank(int tank) {
        if (tank < 0) return null;
        int remaining = tank;
        for (int i = 0; i < lookup.size(); i++) {
            if (!lookup.takeOperation()) return null;
            SoulHandlerBridge handler = lookup.handler(i);
            if (handler == null) continue;
            int tanks = Math.max(0, handler.getSoulTanks());
            if (remaining < tanks) return new TankTarget(handler, remaining);
            remaining -= tanks;
        }
        return null;
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
            SoulHandlerBridge handler = lookup.handler((start + offset) % targets);
            if (handler == null) continue;
            int request = Math.min(share, amount - moved);
            moved += receive ? handler.fill(request, simulate) : handler.drain(amount - moved, simulate);
        }
        if (!simulate) {
            if (receive) insertCursor = (start + 1) % targets;
            else extractCursor = (start + 1) % targets;
        }
        return Math.min(amount, moved);
    }

    private record TankTarget(SoulHandlerBridge handler, int tank) {
    }
}
