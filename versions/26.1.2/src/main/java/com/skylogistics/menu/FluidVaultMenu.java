package com.skylogistics.menu;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.util.TransferCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class FluidVaultMenu extends AbstractContainerMenu {
    private static final int SNAPSHOT_SYNC_INTERVAL_TICKS = 5;

    private final BlockPos pos;
    private final Inventory inventory;
    private long lastSyncedVaultVersion = Long.MIN_VALUE;
    private long lastSnapshotSyncTime = Long.MIN_VALUE;

    public FluidVaultMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.FLUID_VAULT.get(), containerId);
        this.pos = pos;
        this.inventory = inventory;
        FluidVaultBlockEntity vault = vault(inventory.player);
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
        return player.level().getBlockState(pos).is(ModBlocks.FLUID_VAULT.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        FluidVaultBlockEntity vault = vault(player);
        if (vault != null) {
            vault.removeViewer(player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof FluidVaultBlockEntity vault)) {
            return ItemStack.EMPTY;
        }
        ResourceHandler<FluidResource> fluidHandler =
                TransferCompat.fluidResourceHandler(vault.fluidHandler());
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (!transferContainerFluid(ItemAccess.forPlayerSlot(player, slot.getContainerSlot()), fluidHandler,
                FluidResource.EMPTY)) {
            return ItemStack.EMPTY;
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

    public void handleTerminalClick(ServerPlayer player, FluidStack viewedFluid, int button, boolean shiftDown) {
        if (button != 0 && button != 1 || getCarried().isEmpty()) {
            return;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof FluidVaultBlockEntity vault)) {
            return;
        }
        ResourceHandler<FluidResource> fluidHandler =
                TransferCompat.fluidResourceHandler(vault.fluidHandler());
        ItemStack carried = getCarried();
        FluidResource viewedResource = viewedFluid.isEmpty() ? null : FluidResource.of(viewedFluid);
        if (!transferContainerFluid(ItemAccess.forPlayerCursor(player, this), fluidHandler, viewedResource)) {
            return;
        }
        vault.syncTo(player);
        noteVaultSnapshotSynced(vault.getSyncVersion());
        broadcastChanges();
    }

    private static boolean transferContainerFluid(ItemAccess access, ResourceHandler<FluidResource> vault,
            FluidResource requestedFill) {
        ResourceHandler<FluidResource> container =
                access.oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (container == null) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (ResourceHandlerUtil.moveFirst(container, vault, resource -> true, Integer.MAX_VALUE, transaction)
                    != null) {
                transaction.commit();
                return true;
            }
        }
        if (requestedFill == null) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (ResourceHandlerUtil.moveFirst(vault, container,
                    resource -> requestedFill.isEmpty() || requestedFill.equals(resource),
                    Integer.MAX_VALUE, transaction)
                    != null) {
                transaction.commit();
                return true;
            }
        }
        return false;
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

    private FluidVaultBlockEntity vault(Player player) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        return blockEntity instanceof FluidVaultBlockEntity vault ? vault : null;
    }

    private void syncVaultSnapshotIfChanged() {
        if (!(inventory.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        FluidVaultBlockEntity vault = vault(serverPlayer);
        if (vault == null || vault.getSyncVersion() == lastSyncedVaultVersion) {
            return;
        }
        long gameTime = serverPlayer.level().getGameTime();
        if (lastSnapshotSyncTime != Long.MIN_VALUE
                && gameTime - lastSnapshotSyncTime < SNAPSHOT_SYNC_INTERVAL_TICKS) {
            return;
        }
        vault.syncTo(serverPlayer);
        noteVaultSnapshotSynced(vault.getSyncVersion());
    }

    public void noteVaultSnapshotSynced(long version) {
        lastSyncedVaultVersion = version;
        lastSnapshotSyncTime = inventory.player.level().getGameTime();
    }

}
