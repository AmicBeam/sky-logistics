package com.skylogistics.menu;

import com.skylogistics.network.KleisRuntimeEndpoint;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.util.NodeFaceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

/** The normal node menu bound to a remembered Kleis endpoint instead of a placed node block. */
public final class KleisDominionWandMenu extends SkyNodeMenu {
    private final Direction targetFace;

    public KleisDominionWandMenu(int containerId, Inventory inventory, BlockPos pos, Direction face,
            String lineName, NodeFaceMode mode, int resourceMask, int priority) {
        this(containerId, inventory, pos, face,
                clientEndpoint(inventory, pos, face, lineName, mode, resourceMask, priority));
    }

    public KleisDominionWandMenu(int containerId, Inventory inventory, BlockPos pos, Direction face,
            KleisRuntimeEndpoint endpoint) {
        super(ModMenus.KLEIS_DOMINION_WAND.get(), containerId, inventory, pos, false, endpoint);
        this.targetFace = face;
        addDataSlot(endpointState(endpoint));
        addDataSlot(resourceState(endpoint));
        addDataSlot(priorityState(endpoint));
    }

    public Direction targetFace() {
        return targetFace;
    }

    @Override
    public boolean stillValid(Player player) {
        boolean wandMode = player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                && player.getOffhandItem().is(ModItems.CONFIGURATOR.get());
        boolean editMode = player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                && player.getOffhandItem().is(ModItems.KLEIS_DOMINION_WAND.get());
        return (wandMode || editMode) && super.stillValid(player);
    }

    /** Compatibility for clients that still have the first implementation's compact action packet. */
    public void handleAction(int action) {
        KleisRuntimeEndpoint endpoint = (KleisRuntimeEndpoint) endpointNode();
        Direction face = KleisRuntimeEndpoint.ENDPOINT_DIRECTION;
        switch (action) {
            case 0 -> endpoint.setFaceMode(face, endpoint.getFaceMode(face) == NodeFaceMode.INPUT
                    ? NodeFaceMode.OUTPUT : NodeFaceMode.INPUT);
            case 1 -> endpoint.setItemsEnabled(face, !endpoint.isItemsEnabled(face));
            case 2 -> endpoint.setFluidsEnabled(face, !endpoint.isFluidsEnabled(face));
            case 3 -> endpoint.setEnergyEnabled(face, !endpoint.isEnergyEnabled(face));
            case 4 -> endpoint.adjustPriority(face, -1);
            case 5 -> endpoint.adjustPriority(face, 1);
            default -> { return; }
        }
        broadcastChanges();
    }

    private static KleisRuntimeEndpoint clientEndpoint(Inventory inventory, BlockPos pos, Direction face,
            String lineName, NodeFaceMode mode, int resourceMask, int priority) {
        java.util.UUID lineId = java.util.UUID.nameUUIDFromBytes(
                lineName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var placement = new com.skylogistics.item.ConfiguratorItem.FaceConfig(mode,
                (resourceMask & 1) != 0, (resourceMask & 2) != 0, (resourceMask & 4) != 0,
                false, com.skylogistics.util.RedstoneControl.IGNORE, priority, 0,
                java.util.List.of(ItemStack.EMPTY));
        var config = new com.skylogistics.item.ConfiguratorItem.ToolConfig(lineId, lineName, placement,
                java.util.Map.of(), false, java.util.List.of());
        return new KleisRuntimeEndpoint(inventory.player.level(), pos, face, inventory.player.getUUID(), config, mode);
    }

    private static DataSlot endpointState(KleisRuntimeEndpoint endpoint) {
        return new DataSlot() {
            @Override public int get() { return endpoint.getFaceMode(KleisRuntimeEndpoint.ENDPOINT_DIRECTION).ordinal(); }
            @Override public void set(int value) {
                NodeFaceMode[] values = NodeFaceMode.values();
                endpoint.setFaceMode(KleisRuntimeEndpoint.ENDPOINT_DIRECTION,
                        values[Math.max(0, Math.min(values.length - 1, value))]);
            }
        };
    }

    private static DataSlot resourceState(KleisRuntimeEndpoint endpoint) {
        return new DataSlot() {
            @Override public int get() {
                Direction face = KleisRuntimeEndpoint.ENDPOINT_DIRECTION;
                return (endpoint.isItemsEnabled(face) ? 1 : 0)
                        | (endpoint.isFluidsEnabled(face) ? 2 : 0)
                        | (endpoint.isEnergyEnabled(face) ? 4 : 0);
            }
            @Override public void set(int value) {
                Direction face = KleisRuntimeEndpoint.ENDPOINT_DIRECTION;
                endpoint.setItemsEnabled(face, (value & 1) != 0);
                endpoint.setFluidsEnabled(face, (value & 2) != 0);
                endpoint.setEnergyEnabled(face, (value & 4) != 0);
            }
        };
    }

    private static DataSlot priorityState(KleisRuntimeEndpoint endpoint) {
        return new DataSlot() {
            @Override public int get() { return endpoint.getPriority(KleisRuntimeEndpoint.ENDPOINT_DIRECTION); }
            @Override public void set(int value) {
                Direction face = KleisRuntimeEndpoint.ENDPOINT_DIRECTION;
                endpoint.adjustPriority(face, value - endpoint.getPriority(face));
            }
        };
    }
}
