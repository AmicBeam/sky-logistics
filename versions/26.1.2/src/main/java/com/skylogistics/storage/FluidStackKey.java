package com.skylogistics.storage;

import com.skylogistics.util.StackData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidStackKey {
    private final FluidStack stack;

    private FluidStackKey(FluidStack stack) {
        this.stack = stack.copy();
        if (!this.stack.isEmpty()) {
            this.stack.setAmount(1);
        }
    }

    public static FluidStackKey of(FluidStack stack) {
        return new FluidStackKey(stack);
    }

    public Fluid fluid() {
        return stack.getFluid();
    }

    public FluidStack toStack(int amount) {
        FluidStack copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }

    public CompoundTag save() {
        return StackData.saveFluid(stack);
    }

    public static FluidStackKey load(CompoundTag data) {
        return new FluidStackKey(StackData.loadFluid(data));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        return object instanceof FluidStackKey other && FluidStack.isSameFluidSameComponents(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return FluidStack.hashFluidAndComponents(stack);
    }
}
