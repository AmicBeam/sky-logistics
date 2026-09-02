package com.skylogistics.item;

import net.minecraft.world.item.ItemStack;

/** An item that can absorb starlight in a player inventory or on an offering table. */
public interface SkyChargeableItem {
    int minimumChargeY();

    boolean isStackCharged(ItemStack stack);

    boolean chargeStackOneSecond(ItemStack stack);
}
