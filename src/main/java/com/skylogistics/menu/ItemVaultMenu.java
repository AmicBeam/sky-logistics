package com.skylogistics.menu;

import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ItemVaultMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public ItemVaultMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.ITEM_VAULT.get(), containerId);
        this.pos = pos;
        ItemVaultBlockEntity vault = vault(inventory.player);
        if (vault != null) {
            vault.addViewer(inventory.player);
        }
        addPlayerInventory(inventory, 38, 156);
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.ITEM_VAULT.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        ItemVaultBlockEntity vault = vault(player);
        if (vault != null) {
            vault.removeViewer(player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemVaultBlockEntity vault = vault(player);
        if (vault == null) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (!vault.insertFromPlayer(original)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    private void addPlayerInventory(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, x + column * 18, y + 58));
        }
    }

    private ItemVaultBlockEntity vault(Player player) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        return blockEntity instanceof ItemVaultBlockEntity vault ? vault : null;
    }
}
