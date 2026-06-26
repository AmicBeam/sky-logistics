package com.skylogistics.storage;

import com.skylogistics.util.StackData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ItemStackKey {
    private final ItemStack stack;

    private ItemStackKey(ItemStack stack) {
        this.stack = stack.copy();
        if (!this.stack.isEmpty()) {
            this.stack.setCount(1);
        }
    }

    public static ItemStackKey of(ItemStack stack) {
        return new ItemStackKey(stack);
    }

    public Item item() {
        return stack.getItem();
    }

    public ItemStack toStack(int amount) {
        ItemStack copy = stack.copy();
        copy.setCount(amount);
        return copy;
    }

    public CompoundTag save() {
        return StackData.saveItem(stack);
    }

    public static ItemStackKey load(CompoundTag data) {
        return new ItemStackKey(StackData.loadItem(data));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        return object instanceof ItemStackKey other && StackData.sameItemAndComponents(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(stack);
    }
}
