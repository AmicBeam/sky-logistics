package com.skylogistics.item;

import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

/** Reads a detached copy without draining or changing the player's container. */
public final class FilterContainerContents {
    private FilterContainerContents() {
    }

    public static FluidStack fluid(ItemStack stack) {
        if (stack.isEmpty() || !SkyLogisticsConfig.allowFilterContainerFluids()) {
            return FluidStack.EMPTY;
        }
        var handler = stack.copy().getCapability(Capabilities.FluidHandler.ITEM);
        if (handler != null) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack fluid = handler.getFluidInTank(tank).copy();
                if (!fluid.isEmpty()) {
                    fluid.setAmount(1);
                    return fluid;
                }
            }
        }
        return FluidStack.EMPTY;
    }
}
