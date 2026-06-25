package com.skylogistics.menu;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class FluidVaultMenu extends AbstractContainerMenu {
    private final BlockPos pos;

    public FluidVaultMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.FLUID_VAULT.get(), containerId);
        this.pos = pos;
        FluidVaultBlockEntity vault = vault(inventory.player);
        if (vault != null) {
            vault.addViewer(inventory.player);
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
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }
        IFluidHandler fluidHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        if (fluidHandler == null) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        ItemStack single = original.copy();
        single.setCount(1);
        FluidActionResult result = FluidUtil.tryEmptyContainer(single, fluidHandler, Integer.MAX_VALUE, player, true);
        if (!result.isSuccess()) {
            result = FluidUtil.tryFillContainer(single, fluidHandler, Integer.MAX_VALUE, player, true);
        }
        if (!result.isSuccess()) {
            return ItemStack.EMPTY;
        }
        if (original.getCount() == 1) {
            slot.set(result.getResult());
        } else {
            original.shrink(1);
            ItemStack resultStack = result.getResult();
            if (!player.getInventory().add(resultStack)) {
                player.drop(resultStack, false);
            }
            slot.setChanged();
        }
        return copy;
    }

    public void handleTerminalClick(ServerPlayer player, FluidStack viewedFluid, int button, boolean shiftDown) {
        if (button != 0 && button != 1 || getCarried().isEmpty()) {
            return;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof FluidVaultBlockEntity vault)) {
            return;
        }
        IFluidHandler fluidHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        if (fluidHandler == null) {
            return;
        }
        ItemStack carried = getCarried();
        FluidActionResult result = interactCarriedWithVault(player, carried, viewedFluid, fluidHandler, vault);
        if (!result.isSuccess()) {
            return;
        }
        applyCarriedContainerResult(player, carried, result.getResult());
        vault.syncTo(player);
        broadcastChanges();
    }

    private FluidActionResult interactCarriedWithVault(ServerPlayer player, ItemStack carried, FluidStack viewedFluid,
            IFluidHandler fluidHandler, FluidVaultBlockEntity vault) {
        ItemStack single = carried.copy();
        single.setCount(1);
        FluidActionResult result = FluidUtil.tryEmptyContainer(single, fluidHandler, Integer.MAX_VALUE, player, true);
        if (result.isSuccess() || viewedFluid.isEmpty()) {
            return result;
        }
        return FluidUtil.tryFillContainer(single, new ViewedFluidSource(vault, viewedFluid), Integer.MAX_VALUE,
                player, true);
    }

    private void applyCarriedContainerResult(ServerPlayer player, ItemStack carried, ItemStack resultStack) {
        if (carried.getCount() == 1) {
            setCarried(resultStack);
            return;
        }
        carried.shrink(1);
        if (!resultStack.isEmpty()) {
            player.getInventory().add(resultStack);
            if (!resultStack.isEmpty()) {
                player.drop(resultStack, false);
            }
        }
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

    private static final class ViewedFluidSource implements IFluidHandler {
        private final FluidVaultBlockEntity vault;
        private final FluidStack viewedFluid;

        private ViewedFluidSource(FluidVaultBlockEntity vault, FluidStack viewedFluid) {
            this.vault = vault;
            this.viewedFluid = viewedFluid.copy();
            this.viewedFluid.setAmount(1);
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank != 0 || viewedFluid.isEmpty()) {
                return FluidStack.EMPTY;
            }
            return vault.drainForPlayer(viewedFluid, Integer.MAX_VALUE, FluidAction.SIMULATE);
        }

        @Override
        public int getTankCapacity(int tank) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !resource.isFluidEqual(viewedFluid)) {
                return FluidStack.EMPTY;
            }
            return vault.drainForPlayer(viewedFluid, resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return vault.drainForPlayer(viewedFluid, maxDrain, action);
        }
    }
}
