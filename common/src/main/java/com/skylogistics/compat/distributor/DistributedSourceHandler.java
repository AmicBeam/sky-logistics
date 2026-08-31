package com.skylogistics.compat.distributor;

import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;

public final class DistributedSourceHandler implements SourceHandlerBridge, BudgetedDistributorHandler,
        MaintainedStorageView, ConstrainedDistributorAmountHandler {
    private final DistributedHandlerLookup<SourceHandlerBridge> lookup;
    private int insertCursor;
    private int extractCursor;
    private long maintainedSnapshotTick = Long.MIN_VALUE;
    private long maintainedStored;
    private int maintainedOccupied;
    private long maintainedRefill;
    private ScalarInsertPlan maintainedInsertPlan;
    private boolean maintainedInsertPlanAwaitingExecution;

    public DistributedSourceHandler(DistributedHandlerLookup<SourceHandlerBridge> lookup) { this.lookup = lookup; }

    @Override public boolean distributorBudgetExhausted() { return lookup.budgetExhausted(); }
    @Override public boolean distributorScanPending() { return lookup.scanPending(); }

    @Override public int getCurrentSource() { return snapshot(true); }
    @Override public int getMaxSource() { return snapshot(false); }
    @Override public boolean canExtract() { return firstCapable(false) >= 0; }
    @Override public boolean canReceive() { return firstCapable(true) >= 0; }
    @Override public int extractSource(int amount, boolean simulate) { return move(amount, simulate, false); }
    @Override public int insertSource(int amount, boolean simulate) {
        if (!simulate && matchingMaintainedPlan(amount)) return executeMaintainedPlan(amount);
        return move(amount, simulate, true);
    }
    @Override public long maintainedStoredAmount() { refreshMaintainedSnapshot(); return maintainedStored; }
    @Override public int maintainedOccupiedStorageUnits() { refreshMaintainedSnapshot(); return maintainedOccupied; }
    @Override public long maintainedExistingUnitRefillCapacity() { refreshMaintainedSnapshot(); return maintainedRefill; }

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
            SourceHandlerBridge handler = lookup.handler(target);
            if (handler == null) continue;
            int current = Math.max(0, handler.getCurrentSource());
            stored[offset] = current;
            occupied[offset] = current > 0 ? 1 : 0;
            refill[offset] = current > 0 ? Math.max(0, handler.getMaxSource() - current) : 0;
            capacities[offset] = Math.max(0, handler.insertSource(amount, true));
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
            SourceHandlerBridge handler = lookup.handler(maintainedInsertPlan.targets[i]);
            if (handler != null) moved += handler.insertSource((int)Math.min(planned, amount - moved), false);
        }
        maintainedInsertPlanAwaitingExecution = false;
        maintainedInsertPlan = null;
        maintainedSnapshotTick = Long.MIN_VALUE;
        return Math.min(amount, moved);
    }

    private void refreshMaintainedSnapshot() {
        long tick = lookup.gameTime();
        if (tick != Long.MIN_VALUE && maintainedSnapshotTick == tick) return;
        long stored = 0L;
        int occupied = 0;
        long refill = 0L;
        for (int i = 0; i < lookup.size(); i++) {
            if (!lookup.takeOperation()) break;
            SourceHandlerBridge handler = lookup.handler(i);
            if (handler != null) {
                int current = handler.getCurrentSource();
                stored += current;
                if (current > 0) { occupied++; refill += Math.max(0, handler.getMaxSource() - current); }
            }
        }
        maintainedStored = stored;
        maintainedOccupied = occupied;
        maintainedRefill = refill;
        maintainedSnapshotTick = tick;
    }

    private int snapshot(boolean stored) {
        long total = 0L;
        for (int i = 0; i < lookup.size(); i++) {
            if (!lookup.takeOperation()) break;
            SourceHandlerBridge handler = lookup.handler(i);
            if (handler != null) total += stored ? handler.getCurrentSource() : handler.getMaxSource();
        }
        return (int)Math.min(Integer.MAX_VALUE, total);
    }

    private int firstCapable(boolean receive) {
        for (int i = 0; i < lookup.size(); i++) {
            if (!lookup.takeOperation()) break;
            SourceHandlerBridge handler = lookup.handler(i);
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
            SourceHandlerBridge handler = lookup.handler((start + offset) % targets);
            if (handler == null) continue;
            int request = Math.min(share, amount - moved);
            moved += receive ? handler.insertSource(request, simulate)
                    : handler.extractSource(amount - moved, simulate);
        }
        if (!simulate) {
            if (receive) insertCursor = (start + 1) % targets;
            else extractCursor = (start + 1) % targets;
        }
        return Math.min(amount, moved);
    }

    private record ScalarInsertPlan(long tick, int requested, int[] targets, long[] assignments, int accepted) {}
}
