package com.skylogistics.item;

import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.fluids.FluidStack;

/** Reads a detached copy without draining or changing the player's container. */
public final class FilterContainerContents {
    private FilterContainerContents() {
    }

    public static FluidStack fluid(ItemStack stack) {
        if (stack.isEmpty() || !SkyLogisticsConfig.allowFilterContainerFluids()) {
            return FluidStack.EMPTY;
        }
        var handler = ItemAccess.forStack(stack.copy()).getCapability(Capabilities.Fluid.ITEM);
        if (handler != null) {
            for (int tank = 0; tank < handler.size(); tank++) {
                FluidStack fluid = handler.getResource(tank).toStack(1);
                if (!fluid.isEmpty() && handler.getAmountAsLong(tank) > 0) {
                    fluid.setAmount(1);
                    return fluid;
                }
            }
        }
        return FluidStack.EMPTY;
    }
}
