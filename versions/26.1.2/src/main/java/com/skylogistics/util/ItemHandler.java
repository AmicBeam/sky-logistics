package com.skylogistics.util;

import net.minecraft.world.item.ItemStack;

/**
 * Scheduler-facing item storage view.
 *
 * <p>Minecraft 26.1 exposes inventories through NeoForge's transactional
 * {@code ResourceHandler<ItemResource>}. The logistics scheduler keeps this
 * small simulation-oriented view so the version-specific capability adapter is
 * isolated in one place.
 */
public interface ItemHandler {
    int getSlots();

    ItemStack getStackInSlot(int slot);

    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, ItemStack stack);
}
