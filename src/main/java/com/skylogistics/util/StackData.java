package com.skylogistics.util;

import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

public final class StackData {
    private static final HolderLookup.Provider BUILTIN_REGISTRIES =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    private StackData() {
    }

    public static HolderLookup.Provider builtinRegistries() {
        return BUILTIN_REGISTRIES;
    }

    public static CompoundTag get(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : data.copyTag();
    }

    public static CompoundTag getOrEmpty(ItemStack stack) {
        CompoundTag tag = get(stack);
        return tag == null ? new CompoundTag() : tag;
    }

    public static boolean has(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    public static void set(ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
        }
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> updater) {
        CompoundTag tag = getOrEmpty(stack);
        updater.accept(tag);
        set(stack, tag);
    }

    public static void remove(ItemStack stack, String key) {
        update(stack, tag -> tag.remove(key));
    }

    public static CompoundTag saveItem(ItemStack stack) {
        return saveItem(stack, BUILTIN_REGISTRIES);
    }

    public static CompoundTag saveItem(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        Tag saved = stack.save(registries);
        return saved instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    public static ItemStack loadItem(CompoundTag tag) {
        return loadItem(tag, BUILTIN_REGISTRIES);
    }

    public static ItemStack loadItem(CompoundTag tag, HolderLookup.Provider registries) {
        return tag == null || tag.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, tag);
    }

    public static CompoundTag saveFluid(FluidStack stack) {
        return saveFluid(stack, BUILTIN_REGISTRIES);
    }

    public static CompoundTag saveFluid(FluidStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        Tag saved = stack.saveOptional(registries);
        return saved instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    public static FluidStack loadFluid(CompoundTag tag) {
        return loadFluid(tag, BUILTIN_REGISTRIES);
    }

    public static FluidStack loadFluid(CompoundTag tag, HolderLookup.Provider registries) {
        return tag == null || tag.isEmpty() ? FluidStack.EMPTY : FluidStack.parseOptional(registries, tag);
    }

    public static boolean sameItemAndComponents(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(first, second);
    }
}
