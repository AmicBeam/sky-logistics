package com.skylogistics.compat.distributor;

import com.skylogistics.compat.industrialforegoingsouls.SoulHandlerBridge;

public final class DistributedSoulHandler implements SoulHandlerBridge, BudgetedDistributorHandler,
        ConstrainedDistributorAmountHandler {
    private final DistributedHandlerLookup<SoulHandlerBridge> lookup;
    private int insertCursor;
    private int extractCursor;
    private ScalarInsertPlan maintainedInsertPlan;
    private boolean maintainedInsertPlanAwaitingExecution;

    public DistributedSoulHandler(DistributedHandlerLookup<SoulHandlerBridge> lookup) {
        this.lookup = lookup;
    }

    @Override public boolean distributorBudgetExhausted() { return lookup.budgetExhausted(); }
    @Override public boolean distributorScanPending() { return lookup.scanPending(); }

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
        if (!simulate && matchingMaintainedPlan(amount)) return executeMaintainedPlan(amount);
        return move(amount, simulate, true);
    }

    @Override
    public long planMaintainedInsertion(long amount, boolean maintainByAmount, long maintainTarget,
            boolean fillMaintainedUnits) {
        maintainedInsertPlan = buildMaintainedPlan((int)Math.min(Integer.MAX_VALUE, Math.max(0L, amount)),
                maintainByAmount, maintainTarget, fillMaintainedUnits);
        maintainedInsertPlanAwaitingExecution = true;
        return maintainedInsertPlan.accepted;
    }

    private ScalarInsertPlan buildMaintainedPlan(int amount, boolean maintainByAmount, long maintainTarget,
            boolean fillMaintainedUnits) {
        int targets = lookup.size();
        if (amount <= 0 || targets <= 0) return new ScalarInsertPlan(lookup.gameTime(), amount, new int[0], new long[0], 0);
        int start = Math.floorMod(insertCursor, targets);
        int[] targetIndices = new int[targets];
        long[] stored = new long[targets], capacities = new long[targets], refill = new long[targets];
        int[] occupied = new int[targets];
        for (int offset = 0; offset < targets; offset++) {
            if (!lookup.takeOperation()) return new ScalarInsertPlan(lookup.gameTime(), amount, new int[0], new long[0], 0);
            int target = (start + offset) % targets;
            targetIndices[offset] = target;
            SoulHandlerBridge handler = lookup.handler(target);
            if (handler == null) continue;
            for (int tank = 0; tank < handler.getSoulTanks(); tank++) {
                int current = Math.max(0, handler.getSoulInTank(tank));
                stored[offset] += current;
                if (current > 0) {
                    occupied[offset]++;
                    refill[offset] += Math.max(0, handler.getTankCapacity(tank) - current);
                }
            }
            capacities[offset] = Math.max(0, handler.fill(amount, true));
        }
        long[] assignments = DistributorResourceMaintenancePolicy.assignments(amount, stored, capacities,
                occupied, refill, maintainByAmount, maintainTarget, fillMaintainedUnits,
                lookup.sequentialInsertion());
        long accepted = 0L;
        int last = -1;
        for (int i = 0; i < assignments.length; i++) if (assignments[i] > 0L) {
            accepted += assignments[i]; last = targetIndices[i];
        }
        insertCursor = last >= 0 ? last + 1 : start + 1;
        return new ScalarInsertPlan(lookup.gameTime(), amount, targetIndices, assignments,
                (int)Math.min(Integer.MAX_VALUE, accepted));
    }

    private boolean matchingMaintainedPlan(int amount) {
        return maintainedInsertPlanAwaitingExecution && maintainedInsertPlan != null
                && maintainedInsertPlan.tick == lookup.gameTime() && amount <= maintainedInsertPlan.accepted;
    }

    private int executeMaintainedPlan(int amount) {
        int moved = 0;
        for (int i = 0; i < maintainedInsertPlan.assignments.length && moved < amount; i++) {
            long planned = maintainedInsertPlan.assignments[i];
            if (planned <= 0L) continue;
            SoulHandlerBridge handler = lookup.handler(maintainedInsertPlan.targets[i]);
            if (handler != null) moved += handler.fill((int)Math.min(planned, amount - moved), false);
        }
        maintainedInsertPlanAwaitingExecution = false;
        maintainedInsertPlan = null;
        return Math.min(amount, moved);
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

    private record ScalarInsertPlan(long tick, int requested, int[] targets, long[] assignments, int accepted) {}
}
