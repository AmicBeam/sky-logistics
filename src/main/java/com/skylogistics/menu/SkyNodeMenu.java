package com.skylogistics.menu;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SkyNodeMenu extends AbstractContainerMenu {
    public static final int UPGRADE_ROW_Y = 164;

    private final BlockPos pos;
    private final boolean openedWithConfigurator;
    private final NodeUpgradeContainer upgradeContainer;
    private final FaceFilterContainer faceFilterContainer;
    private Direction selectedFace = Direction.NORTH;
    private boolean faceFilterSlotsActive;

    public SkyNodeMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, false, InteractionHand.MAIN_HAND);
    }

    public SkyNodeMenu(int containerId, Inventory inventory, BlockPos pos, boolean openedWithConfigurator,
            InteractionHand ignoredConfiguratorHand) {
        super(ModMenus.SKY_NODE.get(), containerId);
        this.pos = pos;
        this.openedWithConfigurator = openedWithConfigurator;
        this.upgradeContainer = new NodeUpgradeContainer(inventory.player, pos);
        this.faceFilterContainer = new FaceFilterContainer(inventory.player, pos, this);
        int upgradeX = upgradeSlotX(openedWithConfigurator);
        for (int slot = 0; slot < SkyNodeBlockEntity.UPGRADE_SLOTS; slot++) {
            int slotIndex = slot;
            addSlot(new Slot(upgradeContainer, slotIndex, upgradeX + slot * 20, UPGRADE_ROW_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return upgradeContainer.canPlace(slotIndex, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int slot = 0; slot < SkyNodeBlockEntity.FACE_FILTER_SLOTS; slot++) {
            int slotIndex = slot;
            addSlot(new Slot(faceFilterContainer, slotIndex, 176 + slot * 20, 112) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return faceFilterContainer.canPlace(slotIndex, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return faceFilterSlotsActive;
                }
            });
        }
        addPlayerInventory(inventory, 46, 190);
    }

    public static int upgradeSlotX(boolean openedWithConfigurator) {
        return 82;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isOpenedWithConfigurator() {
        return openedWithConfigurator;
    }

    public Direction selectedFace() {
        return selectedFace;
    }

    public void selectFace(Direction face) {
        selectedFace = face;
    }

    public void setFaceFilterSlotsActive(boolean active) {
        faceFilterSlotsActive = active;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.SKY_NODE.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        int upgradeEnd = SkyNodeBlockEntity.UPGRADE_SLOTS;
        int faceFilterEnd = upgradeEnd + SkyNodeBlockEntity.FACE_FILTER_SLOTS;
        if (index < upgradeEnd) {
            if (!moveItemStackTo(original, faceFilterEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
        } else if (index < faceFilterEnd) {
            if (!moveItemStackTo(original, faceFilterEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
        } else if (SkyNodeBlockEntity.isUpgradeItem(original)) {
            if (!moveItemStackTo(original, 0, upgradeEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (SkyNodeBlockEntity.isFaceFilterItem(original)) {
            if (!moveItemStackTo(original, upgradeEnd, faceFilterEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    public void applyAction(Player player, int action) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof SkyNodeBlockEntity node)) {
            return;
        }
        Direction face = faceForAction(action, MenuAction.FACE_NONE_BASE);
        if (face != null) {
            node.setFaceMode(face, NodeFaceMode.NONE);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_EXTRACT_BASE);
        if (face != null) {
            node.setFaceMode(face, NodeFaceMode.INPUT);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_INSERT_BASE);
        if (face != null) {
            node.setFaceMode(face, NodeFaceMode.OUTPUT);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_PRIORITY_DOWN_BASE);
        if (face != null) {
            node.adjustPriority(face, -1);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_PRIORITY_UP_BASE);
        if (face != null) {
            node.adjustPriority(face, 1);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_REDSTONE_BASE);
        if (face != null) {
            node.cycleRedstoneControl(face);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_SELECT_BASE);
        if (face != null) {
            selectedFace = face;
            broadcastChanges();
            return;
        }
        switch (action) {
            case MenuAction.NEW_LINE -> node.setLineId(UUID.randomUUID());
            case MenuAction.TOGGLE_ITEMS -> node.setItemsEnabled(selectedFace, !node.isItemsEnabled(selectedFace));
            case MenuAction.TOGGLE_FLUIDS -> node.setFluidsEnabled(selectedFace, !node.isFluidsEnabled(selectedFace));
            case MenuAction.TOGGLE_ENERGY -> node.setEnergyEnabled(selectedFace, !node.isEnergyEnabled(selectedFace));
            case MenuAction.MODE_INSERT -> node.setMode(NodeMode.OUTPUT);
            case MenuAction.MODE_EXTRACT -> node.setMode(NodeMode.INPUT);
            default -> {
            }
        }
        broadcastChanges();
    }

    private static Direction faceForAction(int action, int base) {
        int ordinal = action - base;
        Direction[] values = Direction.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return null;
        }
        return values[ordinal];
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

    private static final class NodeUpgradeContainer implements Container {
        private final Player player;
        private final BlockPos pos;

        private NodeUpgradeContainer(Player player, BlockPos pos) {
            this.player = player;
            this.pos = pos;
        }

        @Override
        public int getContainerSize() {
            return SkyNodeBlockEntity.UPGRADE_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < getContainerSize(); slot++) {
                if (!getItem(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            SkyNodeBlockEntity node = node();
            return node == null ? ItemStack.EMPTY : node.getUpgrade(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            SkyNodeBlockEntity node = node();
            if (node == null || node.getUpgrade(slot).isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack current = node.getUpgrade(slot).copy();
            node.setUpgrade(slot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            SkyNodeBlockEntity node = node();
            if (node != null) {
                node.setUpgrade(slot, stack);
            }
        }

        private boolean canPlace(int slot, ItemStack stack) {
            SkyNodeBlockEntity node = node();
            return node != null && node.canAcceptUpgrade(slot, stack);
        }

        @Override
        public void setChanged() {
            SkyNodeBlockEntity node = node();
            if (node != null) {
                node.setChanged();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < getContainerSize(); slot++) {
                setItem(slot, ItemStack.EMPTY);
            }
        }

        private SkyNodeBlockEntity node() {
            BlockEntity blockEntity = player.level().getBlockEntity(pos);
            return blockEntity instanceof SkyNodeBlockEntity node ? node : null;
        }
    }

    private static final class FaceFilterContainer implements Container {
        private final Player player;
        private final BlockPos pos;
        private final SkyNodeMenu menu;

        private FaceFilterContainer(Player player, BlockPos pos, SkyNodeMenu menu) {
            this.player = player;
            this.pos = pos;
            this.menu = menu;
        }

        @Override
        public int getContainerSize() {
            return SkyNodeBlockEntity.FACE_FILTER_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            for (int slot = 0; slot < getContainerSize(); slot++) {
                if (!getItem(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            SkyNodeBlockEntity node = node();
            return node == null ? ItemStack.EMPTY : node.getFaceFilter(menu.selectedFace(), slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            SkyNodeBlockEntity node = node();
            if (node == null || node.getFaceFilter(menu.selectedFace(), slot).isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack current = node.getFaceFilter(menu.selectedFace(), slot).copy();
            node.setFaceFilter(menu.selectedFace(), slot, ItemStack.EMPTY);
            return current;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            SkyNodeBlockEntity node = node();
            if (node != null) {
                node.setFaceFilter(menu.selectedFace(), slot, stack);
            }
        }

        private boolean canPlace(int slot, ItemStack stack) {
            SkyNodeBlockEntity node = node();
            return node != null && node.canAcceptFaceFilter(slot, stack);
        }

        @Override
        public void setChanged() {
            SkyNodeBlockEntity node = node();
            if (node != null) {
                node.setChanged();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < getContainerSize(); slot++) {
                setItem(slot, ItemStack.EMPTY);
            }
        }

        private SkyNodeBlockEntity node() {
            BlockEntity blockEntity = player.level().getBlockEntity(pos);
            return blockEntity instanceof SkyNodeBlockEntity node ? node : null;
        }
    }
}
