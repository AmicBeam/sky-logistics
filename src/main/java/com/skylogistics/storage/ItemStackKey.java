package com.skylogistics.storage;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class ItemStackKey {
    private final Item item;
    private final CompoundTag tag;

    private ItemStackKey(Item item, CompoundTag tag) {
        this.item = item;
        this.tag = tag == null ? null : tag.copy();
    }

    public static ItemStackKey of(ItemStack stack) {
        return new ItemStackKey(stack.getItem(), stack.hasTag() ? stack.getTag() : null);
    }

    public Item item() {
        return item;
    }

    public ItemStack toStack(int amount) {
        ItemStack stack = new ItemStack(item, amount);
        if (tag != null) {
            stack.setTag(tag.copy());
        }
        return stack;
    }

    public CompoundTag save() {
        CompoundTag data = new CompoundTag();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        data.putString("Item", id == null ? "minecraft:air" : id.toString());
        if (tag != null) {
            data.put("Tag", tag.copy());
        }
        return data;
    }

    public static ItemStackKey load(CompoundTag data) {
        ResourceLocation id = ResourceLocation.tryParse(data.getString("Item"));
        Item item = id == null ? Items.AIR : ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            item = Items.AIR;
        }
        CompoundTag tag = data.contains("Tag") ? data.getCompound("Tag") : null;
        return new ItemStackKey(item, tag);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ItemStackKey other)) {
            return false;
        }
        return item == other.item && Objects.equals(tag, other.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, tag);
    }
}
