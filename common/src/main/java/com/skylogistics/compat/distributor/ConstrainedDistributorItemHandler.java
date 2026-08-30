package com.skylogistics.compat.distributor;

import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/** Item distributor extension that plans an insertion with node maintenance and matching constraints. */
public interface ConstrainedDistributorItemHandler extends BudgetedDistributorHandler {
    /**
     * Simulates and retains one insertion plan. The following non-simulated handler insertion consumes that plan.
     * The returned value is the exact amount accepted by the retained plan.
     */
    int planItemInsertion(ItemStack stack, DistributorItemInsertContext context,
            Predicate<ItemStack> maintainedMatcher);
}
