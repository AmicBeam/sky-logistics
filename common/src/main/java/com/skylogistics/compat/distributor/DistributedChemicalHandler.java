package com.skylogistics.compat.distributor;

import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import java.util.Arrays;

public final class DistributedChemicalHandler implements ChemicalHandlerBridge {
    private static final int MAX_TARGETS = 64;
    private final DistributedHandlerLookup<ChemicalHandlerBridge> lookup;
    private final int[] visibleTanks = new int[MAX_TARGETS];
    private int insertCursor;

    public DistributedChemicalHandler(DistributedHandlerLookup<ChemicalHandlerBridge> lookup) {
        this.lookup = lookup;
        Arrays.fill(visibleTanks, -1);
    }

    @Override public int getTanks() { return lookup.size(); }

    @Override
    public ChemicalStackView getChemicalInTank(int tank) {
        ChemicalHandlerBridge handler = handler(tank);
        if (handler == null) return EmptyChemicalStackView.INSTANCE;
        int tanks = handler.getTanks();
        if (tanks <= 0) return EmptyChemicalStackView.INSTANCE;
        int preferred = visibleTanks[tank];
        if (preferred >= 0 && preferred < tanks && lookup.takeOperation()) {
            ChemicalStackView stack = handler.getChemicalInTank(preferred);
            if (stack != null && !stack.isEmpty()) return stack;
        }
        for (int sourceTank = 0; sourceTank < tanks; sourceTank++) {
            if (sourceTank == preferred || !lookup.takeOperation()) continue;
            ChemicalStackView stack = handler.getChemicalInTank(sourceTank);
            if (stack != null && !stack.isEmpty()) {
                visibleTanks[tank] = sourceTank;
                return stack;
            }
        }
        visibleTanks[tank] = -1;
        return EmptyChemicalStackView.INSTANCE;
    }

    @Override
    public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
        if (amount <= 0L) return EmptyChemicalStackView.INSTANCE;
        ChemicalHandlerBridge handler = handler(tank);
        if (handler == null) return EmptyChemicalStackView.INSTANCE;
        int sourceTank = visibleTanks[tank];
        if (sourceTank < 0 || sourceTank >= handler.getTanks()) {
            ChemicalStackView visible = getChemicalInTank(tank);
            if (visible.isEmpty()) return EmptyChemicalStackView.INSTANCE;
            sourceTank = visibleTanks[tank];
        }
        if (!lookup.takeOperation()) return EmptyChemicalStackView.INSTANCE;
        ChemicalStackView extracted = handler.extractChemical(sourceTank, amount, simulate);
        if (!simulate && (extracted == null || extracted.isEmpty())) visibleTanks[tank] = -1;
        return extracted == null ? EmptyChemicalStackView.INSTANCE : extracted;
    }

    @Override
    public long insertChemical(ChemicalStackView stack, boolean simulate) {
        if (stack == null || stack.isEmpty()) return 0L;
        int targets = lookup.size();
        if (targets <= 0) return 0L;
        long inserted = 0L;
        int start = Math.floorMod(insertCursor, targets);
        for (int offset = 0; offset < targets && inserted < stack.getAmount(); offset++) {
            if (!lookup.takeOperation()) break;
            int target = (start + offset) % targets;
            ChemicalHandlerBridge handler = lookup.handler(target);
            if (handler == null) continue;
            ChemicalStackView offer = stack.copyWithAmount(stack.getAmount() - inserted);
            inserted += Math.max(0L, handler.insertChemical(offer, simulate));
            if (!simulate) visibleTanks[target] = -1;
        }
        if (!simulate) insertCursor = (start + 1) % targets;
        return Math.min(stack.getAmount(), inserted);
    }

    private ChemicalHandlerBridge handler(int target) {
        return target < 0 || target >= lookup.size() || target >= MAX_TARGETS ? null : lookup.handler(target);
    }
}
