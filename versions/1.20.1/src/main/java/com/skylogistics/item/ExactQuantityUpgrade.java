package com.skylogistics.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class ExactQuantityUpgrade {
    private static final String AMOUNT = "ExactQuantity";
    public static final int MIN = 1;
    public static final int MAX = Integer.MAX_VALUE;
    public static final int DEFAULT = 64;

    private ExactQuantityUpgrade() { }

    public static int amount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(AMOUNT) ? DEFAULT : clamp(tag.getInt(AMOUNT));
    }

    public static void setAmount(ItemStack stack, int amount) {
        if (!stack.isEmpty()) stack.getOrCreateTag().putInt(AMOUNT, clamp(amount));
    }

    public static int clamp(int amount) { return Math.max(MIN, amount); }
}
