package com.skylogistics.compat.mekanism;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.SlurryStack;
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

    static ChemicalHandlerBridge wrapChemicalHandlers(Object... handlers) {
        List<Delegate> delegates = new ArrayList<>(handlers.length);
        for (Object handler : handlers) {
            if (!(handler instanceof IChemicalHandler chemicalHandler)) {
                continue;
            }
            Kind kind = kindForHandler(handler);
            if (kind != null) {
                delegates.add(new Delegate(kind, chemicalHandler));
            }
        }
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

    private static Kind kindForHandler(Object handler) {
        if (handler instanceof IGasHandler) {
            return Kind.GAS;
        }
        if (handler instanceof IInfusionHandler) {
            return Kind.INFUSION;
        }
        if (handler instanceof IPigmentHandler) {
            return Kind.PIGMENT;
        }
        if (handler instanceof ISlurryHandler) {
            return Kind.SLURRY;
        }
        return null;
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
            ChemicalStack<?> rawStack;
            Kind rawKind;
            if (stack instanceof StackView view) {
                rawStack = view.stack;
                rawKind = view.kind;
            } else if (stack.rawStack() instanceof ChemicalStack<?> chemicalStack) {
                rawStack = chemicalStack;
                rawKind = kind(rawStack);
            } else {
                return 0L;
            }
            if (rawKind == null || rawStack.isEmpty()) {
                return 0L;
            }
            long remaining = rawStack.getAmount();
            for (Delegate delegate : delegates) {
                if (delegate.kind != rawKind || remaining <= 0L) {
                    continue;
                }
                ChemicalStack<?> inserted = copyStack(rawStack, remaining);
                ChemicalStack<?> remainder = insertRaw(delegate.handler, inserted, action(simulate));
                remaining = remainder.getAmount();
            }
            return rawStack.getAmount() - remaining;
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

    private static Kind kind(ChemicalStack<?> stack) {
        if (stack instanceof GasStack) {
            return Kind.GAS;
        }
        if (stack instanceof InfusionStack) {
            return Kind.INFUSION;
        }
        if (stack instanceof PigmentStack) {
            return Kind.PIGMENT;
        }
        if (stack instanceof SlurryStack) {
            return Kind.SLURRY;
        }
        return null;
    }

    private static ChemicalStack<?> copyStack(ChemicalStack<?> stack, long amount) {
        ChemicalStack<?> copy = stack.copy();
        copy.setAmount(amount);
        return copy;
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
            return wrap(kind, MekanismChemicalCompat.copyStack(stack, amount));
        }

        @Override
        public boolean isSameChemical(ChemicalStackView other) {
            return other instanceof StackView view
                    && kind == view.kind
                    && stack.getTypeRegistryName().equals(view.stack.getTypeRegistryName());
        }

        @Override
        public Object rawStack() {
            return stack.copy();
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
