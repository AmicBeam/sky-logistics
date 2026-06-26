package com.skylogistics.menu;

import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ItemVaultMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Inventory inventory;
    private long lastSyncedVaultVersion = Long.MIN_VALUE;

    public ItemVaultMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.ITEM_VAULT.get(), containerId);
        this.pos = pos;
        this.inventory = inventory;
        ItemVaultBlockEntity vault = vault(inventory.player);
        if (vault != null) {
            vault.addViewer(inventory.player);
            lastSyncedVaultVersion = vault.getSyncVersion();
        }
        addPlayerInventory(inventory, 17, 138);
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
        vault.syncToPlayerIfPresent(player);
        noteVaultSnapshotSynced(vault.getSyncVersion());
        broadcastChanges();
        return copy;
    }

    @Override
    public void broadcastChanges() {
        syncVaultSnapshotIfChanged();
        super.broadcastChanges();
    }

    public void handleTerminalClick(ServerPlayer player, ItemStack viewedStack, int button, boolean shiftDown) {
        if (button != 0 && button != 1) {
            return;
        }
        ItemVaultBlockEntity vault = vault(player);
        if (vault == null) {
            return;
        }
        ItemStack carried = getCarried();
        boolean changed;
        if (carried.isEmpty()) {
            changed = extractToCursorOrInventory(player, vault, viewedStack, button, shiftDown);
        } else {
            changed = insertCarried(vault, carried, button);
        }
        if (changed) {
            vault.syncTo(player);
            noteVaultSnapshotSynced(vault.getSyncVersion());
            broadcastChanges();
        }
    }

    private boolean insertCarried(ItemVaultBlockEntity vault, ItemStack carried, int button) {
        ItemStack toInsert = carried.copy();
        toInsert.setCount(button == 1 ? 1 : carried.getCount());
        int before = toInsert.getCount();
        if (!vault.insertFromPlayer(toInsert)) {
            return false;
        }
        int inserted = before - toInsert.getCount();
        if (inserted <= 0) {
            return false;
        }
        carried.shrink(inserted);
        if (carried.isEmpty()) {
            setCarried(ItemStack.EMPTY);
        }
        return true;
    }

    private boolean extractToCursorOrInventory(ServerPlayer player, ItemVaultBlockEntity vault, ItemStack viewedStack,
            int button, boolean shiftDown) {
        if (viewedStack.isEmpty()) {
            return false;
        }
        int amount = button == 1 ? 1 : viewedStack.getMaxStackSize();
        ItemStack extracted = vault.extractForPlayer(viewedStack, amount);
        if (extracted.isEmpty()) {
            return false;
        }
        if (!shiftDown) {
            setCarried(extracted);
            return true;
        }
        player.getInventory().add(extracted);
        if (!extracted.isEmpty()) {
            vault.insertFromPlayer(extracted);
        }
        return true;
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

    private void syncVaultSnapshotIfChanged() {
        if (!(inventory.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemVaultBlockEntity vault = vault(serverPlayer);
        if (vault == null || vault.getSyncVersion() == lastSyncedVaultVersion) {
            return;
        }
        vault.syncTo(serverPlayer);
        noteVaultSnapshotSynced(vault.getSyncVersion());
    }

    public void noteVaultSnapshotSynced(long version) {
        lastSyncedVaultVersion = version;
    }
}
