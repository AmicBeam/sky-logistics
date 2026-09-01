package com.skylogistics.menu;

import com.skylogistics.registry.ModMenus;
import com.skylogistics.block.entity.KleisVirtualNodeBlockEntity;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public final class KleisDominionWandMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Direction face;
    private final String lineName;
    public static final int ACTION_TOGGLE_MODE = 0;
    public static final int ACTION_TOGGLE_ITEMS = 1;
    public static final int ACTION_TOGGLE_FLUIDS = 2;
    public static final int ACTION_TOGGLE_ENERGY = 3;
    public static final int ACTION_PRIORITY_DOWN = 4;
    public static final int ACTION_PRIORITY_UP = 5;
    private int modeOrdinal;
    private int resourceMask;
    private int priority;
    private final KleisVirtualNodeBlockEntity serverNode;

    public KleisDominionWandMenu(int containerId, Inventory inventory, BlockPos pos, Direction face,
            String lineName, NodeFaceMode mode, int resourceMask, int priority) {
        this(containerId, inventory, pos, face, lineName, mode, resourceMask, priority, null);
    }

    public KleisDominionWandMenu(int containerId, Inventory inventory, BlockPos pos, Direction face,
            String lineName, NodeFaceMode mode, int resourceMask, int priority,
            KleisVirtualNodeBlockEntity serverNode) {
        super(ModMenus.KLEIS_DOMINION_WAND.get(), containerId);
        this.pos = pos;
        this.face = face;
        this.lineName = lineName;
        this.modeOrdinal = mode.ordinal();
        this.resourceMask = resourceMask;
        this.priority = priority;
        this.serverNode = serverNode;
        addDataSlot(slot(() -> modeOrdinal, value -> modeOrdinal = value));
        addDataSlot(slot(() -> this.resourceMask, value -> this.resourceMask = value));
        addDataSlot(slot(() -> this.priority, value -> this.priority = value));
        addPlayerInventory(inventory, 39, 101);
    }

    public BlockPos pos() { return pos; }
    public Direction face() { return face; }
    public String lineName() { return lineName; }
    public NodeFaceMode mode() { return NodeFaceMode.values()[Math.max(0, Math.min(NodeFaceMode.values().length - 1, modeOrdinal))]; }
    public int resourceMask() { return resourceMask; }
    public int priority() { return priority; }

    public void handleAction(int action) {
        if (serverNode == null) return;
        Direction endpoint = KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION;
        switch (action) {
            case ACTION_TOGGLE_MODE -> serverNode.setMode(serverNode.getFaceMode(endpoint) == NodeFaceMode.INPUT
                    ? NodeMode.OUTPUT : NodeMode.INPUT);
            case ACTION_TOGGLE_ITEMS -> serverNode.setItemsEnabled(endpoint, !serverNode.isItemsEnabled(endpoint));
            case ACTION_TOGGLE_FLUIDS -> serverNode.setFluidsEnabled(endpoint, !serverNode.isFluidsEnabled(endpoint));
            case ACTION_TOGGLE_ENERGY -> serverNode.setEnergyEnabled(endpoint, !serverNode.isEnergyEnabled(endpoint));
            case ACTION_PRIORITY_DOWN -> serverNode.adjustPriority(endpoint, -1);
            case ACTION_PRIORITY_UP -> serverNode.adjustPriority(endpoint, 1);
            default -> { return; }
        }
        refreshFromNode();
        broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        refreshFromNode();
        super.broadcastChanges();
    }

    private void refreshFromNode() {
        if (serverNode == null) return;
        Direction endpoint = KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION;
        modeOrdinal = serverNode.getFaceMode(endpoint).ordinal();
        resourceMask = (serverNode.isItemsEnabled(endpoint) ? 1 : 0)
                | (serverNode.isFluidsEnabled(endpoint) ? 2 : 0)
                | (serverNode.isEnergyEnabled(endpoint) ? 4 : 0);
        priority = serverNode.getPriority(endpoint);
    }

    private static DataSlot slot(java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        return new DataSlot() {
            @Override public int get() { return getter.getAsInt(); }
            @Override public void set(int value) { setter.accept(value); }
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
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
}
