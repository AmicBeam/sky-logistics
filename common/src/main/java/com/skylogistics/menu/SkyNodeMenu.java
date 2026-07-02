package com.skylogistics.menu;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.network.SkyPlayerLines;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SkyNodeMenu extends AbstractContainerMenu {
    public static final int UPGRADE_ROW_Y = 153;
    public static final int FACE_FILTER_SLOT_X = 172;
    public static final int FACE_FILTER_ROW_Y = 126;
    public static final int SINGLE_ENDPOINT_VERTICAL_SHIFT = 44;
    private static final int UPGRADE_SLOT_X = 78;
    private static final int PLAYER_INVENTORY_X = 46;
    private static final int PLAYER_INVENTORY_Y = 179;
    private static final Direction[] FACE_ORDER = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private final BlockPos pos;
    private final Player player;
    private final boolean openedWithConfigurator;
    private final boolean singleEndpoint;
    private final int verticalShift;
    private final NodeUpgradeContainer upgradeContainer;
    private final FaceFilterContainer faceFilterContainer;
    private Direction selectedFace = Direction.NORTH;
    private boolean faceFilterSlotsActive;
    private int lineIndex;
    private int lineCount = 1;

    public SkyNodeMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, false, InteractionHand.MAIN_HAND);
    }

    public SkyNodeMenu(int containerId, Inventory inventory, BlockPos pos, boolean openedWithConfigurator,
            InteractionHand ignoredConfiguratorHand) {
        super(ModMenus.SKY_NODE.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.openedWithConfigurator = openedWithConfigurator;
        this.upgradeContainer = new NodeUpgradeContainer(inventory.player, pos);
        this.faceFilterContainer = new FaceFilterContainer(inventory.player, pos, this);
        this.singleEndpoint = usesSingleEndpoint(inventory.player, pos);
        this.verticalShift = singleEndpoint ? SINGLE_ENDPOINT_VERTICAL_SHIFT : 0;
        this.selectedFace = initialSelectedFace(inventory.player, pos);
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return lineIndex;
            }

            @Override
            public void set(int value) {
                lineIndex = value;
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return lineCount;
            }

            @Override
            public void set(int value) {
                lineCount = value;
            }
        });
        int upgradeX = upgradeSlotX(openedWithConfigurator);
        for (int slot = 0; slot < SkyNodeBlockEntity.UPGRADE_SLOTS; slot++) {
            int slotIndex = slot;
            addSlot(new Slot(upgradeContainer, slotIndex, upgradeX + slot * 20, screenY(UPGRADE_ROW_Y)) {
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
            addSlot(new Slot(faceFilterContainer, slotIndex, FACE_FILTER_SLOT_X + slot * 20, screenY(FACE_FILTER_ROW_Y)) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
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
        addPlayerInventory(inventory, PLAYER_INVENTORY_X, screenY(PLAYER_INVENTORY_Y));
    }

    public static int upgradeSlotX(boolean openedWithConfigurator) {
        return UPGRADE_SLOT_X;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isOpenedWithConfigurator() {
        return openedWithConfigurator;
    }

    public boolean isSingleEndpoint() {
        return singleEndpoint;
    }

    public int getLineIndex() {
        return lineIndex;
    }

    public int getLineCount() {
        return lineCount;
    }

    public int screenY(int normalY) {
        return normalY - verticalShift;
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
        return player.level().getBlockEntity(pos) instanceof SkyNodeBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isFaceFilterSlot(slotId)) {
            int slot = slotId - SkyNodeBlockEntity.UPGRADE_SLOTS;
            ItemStack carried = getCarried();
            if (carried.isEmpty() || SkyNodeBlockEntity.isFaceFilterItem(carried)) {
                faceFilterContainer.setGhost(slot, carried);
                broadcastChanges();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
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
            faceFilterContainer.setGhost(index - upgradeEnd, ItemStack.EMPTY);
            broadcastChanges();
            return ItemStack.EMPTY;
        } else if (SkyNodeBlockEntity.isUpgradeItem(original)) {
            if (!moveItemStackTo(original, 0, upgradeEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (SkyNodeBlockEntity.isFaceFilterItem(original)) {
            faceFilterContainer.setGhost(0, original);
            broadcastChanges();
            return ItemStack.EMPTY;
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

    private static boolean isFaceFilterSlot(int slotId) {
        return slotId >= SkyNodeBlockEntity.UPGRADE_SLOTS
                && slotId < SkyNodeBlockEntity.UPGRADE_SLOTS + SkyNodeBlockEntity.FACE_FILTER_SLOTS;
    }

    public void applyAction(Player player, int action) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof SkyNodeBlockEntity node)) {
            return;
        }
        Direction face = faceForAction(action, MenuAction.FACE_NONE_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.setFaceMode(face, NodeFaceMode.NONE);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_EXTRACT_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.setFaceMode(face, NodeFaceMode.INPUT);
            node.configureTargetResourcesFromCapabilities(face);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_INSERT_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.setFaceMode(face, NodeFaceMode.OUTPUT);
            node.configureTargetResourcesFromCapabilities(face);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_PRIORITY_DOWN_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.adjustPriority(face, -1);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_PRIORITY_UP_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.adjustPriority(face, 1);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_PRIORITY_DOWN_FAST_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.adjustPriority(face, -10);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_PRIORITY_UP_FAST_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.adjustPriority(face, 10);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_SLOT_LIMIT_DOWN_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.adjustItemSlotLimit(face, -1);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_SLOT_LIMIT_UP_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.adjustItemSlotLimit(face, 1);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_SLOT_LIMIT_DOWN_FAST_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.adjustItemSlotLimit(face, -10);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_SLOT_LIMIT_UP_FAST_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.adjustItemSlotLimit(face, 10);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_REDSTONE_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            node.cycleRedstoneControl(face);
            broadcastChanges();
            return;
        }
        face = faceForAction(action, MenuAction.FACE_SELECT_BASE);
        if (face != null) {
            if (!node.canConfigureFace(face)) {
                return;
            }
            selectedFace = face;
            broadcastChanges();
            return;
        }
        switch (action) {
            case MenuAction.NEW_LINE, MenuAction.LINE_NEXT_OR_CREATE -> selectPlayerLine(node,
                    SkyPlayerLines.selectNextOrCreate(player.level().getServer(), player, node.getLineId(),
                            node.getAssignedLineName(), node.getLineName()));
            case MenuAction.LINE_FIRST -> selectPlayerLine(node,
                    SkyPlayerLines.selectFirst(player.level().getServer(), player, node.getLineId(),
                            node.getAssignedLineName(), node.getLineName()));
            case MenuAction.LINE_PREVIOUS -> selectPlayerLine(node,
                    SkyPlayerLines.selectPrevious(player.level().getServer(), player, node.getLineId(),
                            node.getAssignedLineName(), node.getLineName()));
            case MenuAction.LINE_LAST -> selectPlayerLine(node,
                    SkyPlayerLines.selectLast(player.level().getServer(), player, node.getLineId(),
                            node.getAssignedLineName(), node.getLineName()));
            case MenuAction.LINE_REMOVE_CURRENT -> {
                if (currentLineInUse(node)) {
                    player.displayClientMessage(Component.translatable(
                            "message.skylogistics.configurator.line_in_use"), true);
                } else {
                    selectPlayerLine(node,
                            SkyPlayerLines.removeCurrent(player.level().getServer(), player, node.getLineId(),
                                    node.getAssignedLineName(), node.getLineName()));
                }
            }
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

    public void renameCurrentLine(Player player, String lineName) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof SkyNodeBlockEntity node)) {
            return;
        }
        if (!player.level().isClientSide && player.level().getServer() != null) {
            SkyNetworkRegistry.renameLine(player.level().getServer(), node.getLineId(), lineName,
                    node.getAssignedLineName());
        }
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        syncPlayerLineSelection();
        super.broadcastChanges();
    }

    private void syncPlayerLineSelection() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof SkyNodeBlockEntity node) {
            SkyPlayerLines.LineSelection selection = SkyPlayerLines.selection(player.level().getServer(), player,
                    node.getLineId(), node.getAssignedLineName(), node.getLineName());
            lineIndex = selection.index();
            lineCount = selection.count();
            node.selectPlayerLine(selection.lineId(), selection.assignedName(), selection.displayName());
            SkyNetworkRegistry.syncLineName(serverPlayer, selection.lineId(), selection.assignedName(),
                    selection.displayName());
        }
    }

    private void selectPlayerLine(SkyNodeBlockEntity node, SkyPlayerLines.LineSelection selection) {
        lineIndex = selection.index();
        lineCount = selection.count();
        node.selectPlayerLine(selection.lineId(), selection.assignedName(), selection.displayName());
    }

    private boolean currentLineInUse(SkyNodeBlockEntity node) {
        if (player.level().isClientSide || player.level().getServer() == null) {
            return false;
        }
        return SkyNetworkRegistry.lineHasExternalConnections(player.level().getServer(), node.getLineId(),
                player.level().dimension(), node.getBlockPos());
    }

    private static Direction faceForAction(int action, int base) {
        int ordinal = action - base;
        Direction[] values = Direction.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return null;
        }
        return values[ordinal];
    }

    private static Direction initialSelectedFace(Player player, BlockPos pos) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof SkyNodeBlockEntity node)) {
            return Direction.NORTH;
        }
        if (node.usesSingleEndpoint()) {
            return node.getSingleEndpointDirection();
        }
        for (Direction direction : FACE_ORDER) {
            if (isPreferredFace(player, node, direction)) {
                return direction;
            }
        }
        for (Direction direction : FACE_ORDER) {
            if (hasTargetBlock(player, node, direction)) {
                return direction;
            }
        }
        return node.getTargetDirection();
    }

    private static boolean usesSingleEndpoint(Player player, BlockPos pos) {
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        return blockEntity instanceof SkyNodeBlockEntity node && node.usesSingleEndpoint();
    }

    private static boolean isPreferredFace(Player player, SkyNodeBlockEntity node, Direction direction) {
        return hasTargetBlock(player, node, direction)
                && node.getFaceMode(direction) != NodeFaceMode.NONE
                && (node.isItemsEnabled(direction) || node.isFluidsEnabled(direction)
                        || node.isEnergyEnabled(direction));
    }

    private static boolean hasTargetBlock(Player player, SkyNodeBlockEntity node, Direction direction) {
        return node.hasConfigurableTarget(direction);
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
            node.setFaceFilter(menu.selectedFace(), slot, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            setGhost(slot, stack);
        }

        private void setGhost(int slot, ItemStack stack) {
            SkyNodeBlockEntity node = node();
            if (node != null) {
                node.setFaceFilter(menu.selectedFace(), slot, stack);
            }
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
