package com.skylogistics.compat.mekanism;

import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

final class MekanismChemicalCompat {
    private MekanismChemicalCompat() {
    }

    static ChemicalHandlerBridge chemicalHandler(Level level, BlockPos pos, Direction side) {
        IChemicalHandler handler = level.getCapability(Capabilities.CHEMICAL.block(), pos, side);
        return handler == null ? null : new Handler(handler);
    }

    private static Action action(boolean simulate) {
        return simulate ? Action.SIMULATE : Action.EXECUTE;
    }

    private record Handler(IChemicalHandler handler) implements ChemicalHandlerBridge {
        @Override
        public int getTanks() {
            return handler.getChemicalTanks();
        }

        @Override
        public ChemicalStackView getChemicalInTank(int tank) {
            return StackView.wrap(handler.getChemicalInTank(tank));
        }

        @Override
        public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
            return StackView.wrap(handler.extractChemical(tank, amount, action(simulate)));
        }

        @Override
        public long insertChemical(ChemicalStackView stack, boolean simulate) {
            if (!(stack instanceof StackView view) || view.stack.isEmpty()) {
                return 0L;
            }
            ChemicalStack inserted = view.stack.copy();
            ChemicalStack remainder = handler.insertChemical(inserted, action(simulate));
            return inserted.getAmount() - remainder.getAmount();
        }
    }

    private record StackView(ChemicalStack stack) implements ChemicalStackView {
        private static ChemicalStackView wrap(ChemicalStack stack) {
            return new StackView(stack == null ? ChemicalStack.EMPTY : stack);
        }

        @Override
        public boolean isEmpty() {
            return stack.isEmpty();
        }

        @Override
        public long getAmount() {
            return stack.getAmount();
        }

        @Override
        public ChemicalStackView copyWithAmount(long amount) {
            return wrap(stack.copyWithAmount(amount));
        }

        @Override
        public boolean isSameChemical(ChemicalStackView other) {
            return other instanceof StackView view && ChemicalStack.isSameChemical(stack, view.stack);
        }

        @Override
        public String toString() {
            return stack.toString();
        }
    }
}
