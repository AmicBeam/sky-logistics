package com.skylogistics.compat.mekanism;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

final class MekanismChemicalCompat {
    private MekanismChemicalCompat() {
    }

    static ChemicalHandlerBridge chemicalHandler(Level level, BlockPos pos, Direction side) {
        BlockEntity target = level.getBlockEntity(pos);
        if (target == null) {
            return null;
        }
        List<Delegate> delegates = new ArrayList<>(4);
        target.getCapability(Capabilities.GAS_HANDLER, side)
                .ifPresent(handler -> delegates.add(new Delegate(Kind.GAS, handler)));
        target.getCapability(Capabilities.INFUSION_HANDLER, side)
                .ifPresent(handler -> delegates.add(new Delegate(Kind.INFUSION, handler)));
        target.getCapability(Capabilities.PIGMENT_HANDLER, side)
                .ifPresent(handler -> delegates.add(new Delegate(Kind.PIGMENT, handler)));
        target.getCapability(Capabilities.SLURRY_HANDLER, side)
                .ifPresent(handler -> delegates.add(new Delegate(Kind.SLURRY, handler)));
        return delegates.isEmpty() ? null : new Handler(delegates);
    }

    private static Action action(boolean simulate) {
        return simulate ? Action.SIMULATE : Action.EXECUTE;
    }

    private enum Kind {
        GAS,
        INFUSION,
        PIGMENT,
        SLURRY
    }

    private record Delegate(Kind kind, IChemicalHandler handler) {
    }

    private record IndexedTank(Delegate delegate, int tank) {
    }

    private record Handler(List<Delegate> delegates) implements ChemicalHandlerBridge {
        @Override
        public int getTanks() {
            int tanks = 0;
            for (Delegate delegate : delegates) {
                tanks += delegate.handler.getTanks();
            }
            return tanks;
        }

        @Override
        public ChemicalStackView getChemicalInTank(int tank) {
            IndexedTank indexed = tank(tank);
            return indexed == null ? StackView.empty() : StackView.wrap(indexed.delegate.kind,
                    indexed.delegate.handler.getChemicalInTank(indexed.tank));
        }

        @Override
        public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
            IndexedTank indexed = tank(tank);
            return indexed == null ? StackView.empty() : StackView.wrap(indexed.delegate.kind,
                    indexed.delegate.handler.extractChemical(indexed.tank, amount, action(simulate)));
        }

        @Override
        public long insertChemical(ChemicalStackView stack, boolean simulate) {
            if (!(stack instanceof StackView view) || view.stack.isEmpty()) {
                return 0L;
            }
            long remaining = view.stack.getAmount();
            for (Delegate delegate : delegates) {
                if (delegate.kind != view.kind || remaining <= 0L) {
                    continue;
                }
                ChemicalStack<?> inserted = view.copyStack(remaining);
                ChemicalStack<?> remainder = insertRaw(delegate.handler, inserted, action(simulate));
                remaining = remainder.getAmount();
            }
            return view.stack.getAmount() - remaining;
        }

        private IndexedTank tank(int tank) {
            int index = tank;
            for (Delegate delegate : delegates) {
                int delegateTanks = delegate.handler.getTanks();
                if (index < delegateTanks) {
                    return new IndexedTank(delegate, index);
                }
                index -= delegateTanks;
            }
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ChemicalStack<?> insertRaw(IChemicalHandler handler, ChemicalStack<?> stack, Action action) {
        return handler.insertChemical(stack, action);
    }

    private record StackView(Kind kind, ChemicalStack<?> stack) implements ChemicalStackView {
        private static ChemicalStackView empty() {
            return new EmptyStackView();
        }

        private static ChemicalStackView wrap(Kind kind, ChemicalStack<?> stack) {
            return stack == null || stack.isEmpty() ? empty() : new StackView(kind, stack);
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
            return wrap(kind, copyStack(amount));
        }

        @Override
        public boolean isSameChemical(ChemicalStackView other) {
            return other instanceof StackView view
                    && kind == view.kind
                    && stack.getTypeRegistryName().equals(view.stack.getTypeRegistryName());
        }

        private ChemicalStack<?> copyStack(long amount) {
            ChemicalStack<?> copy = stack.copy();
            copy.setAmount(amount);
            return copy;
        }

        @Override
        public String toString() {
            return stack.toString();
        }
    }

    private record EmptyStackView() implements ChemicalStackView {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public long getAmount() {
            return 0L;
        }

        @Override
        public ChemicalStackView copyWithAmount(long amount) {
            return this;
        }

        @Override
        public boolean isSameChemical(ChemicalStackView other) {
            return other != null && other.isEmpty();
        }
    }
}
