package com.skylogistics.item;

import com.skylogistics.util.StackData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class ExactQuantityUpgrade {
    private static final String AMOUNT = "ExactQuantity";
    public static final int MIN = 1;
    public static final int MAX = Integer.MAX_VALUE;
    public static final int DEFAULT = 64;
    private ExactQuantityUpgrade() { }
    public static int amount(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag == null || !tag.contains(AMOUNT) ? DEFAULT : clamp(tag.getInt(AMOUNT));
    }
    public static void setAmount(ItemStack stack, int amount) {
        if (!stack.isEmpty()) StackData.update(stack, tag -> tag.putInt(AMOUNT, clamp(amount)));
    }
    public static int clamp(int amount) { return Math.max(MIN, amount); }
}
