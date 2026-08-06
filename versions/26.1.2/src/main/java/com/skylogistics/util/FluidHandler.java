package com.skylogistics.util;

import net.neoforged.neoforge.fluids.FluidStack;

/** Scheduler-facing fluid storage view for the Minecraft 26.1 transfer API. */
public interface FluidHandler {
    int getTanks();

    FluidStack getFluidInTank(int tank);

    int getTankCapacity(int tank);

    boolean isFluidValid(int tank, FluidStack stack);

    int fill(FluidStack resource, FluidAction action);

    FluidStack drain(FluidStack resource, FluidAction action);

    FluidStack drain(int maxDrain, FluidAction action);

    enum FluidAction {
        EXECUTE,
        SIMULATE;

        public boolean execute() {
            return this == EXECUTE;
        }
    }
}
