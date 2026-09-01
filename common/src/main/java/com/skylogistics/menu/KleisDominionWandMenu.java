package com.skylogistics.menu;

import com.skylogistics.block.entity.KleisVirtualNodeBlockEntity;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.util.NodeFaceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;

/** The normal node menu bound to a remembered Kleis endpoint instead of a placed node block. */
public final class KleisDominionWandMenu extends SkyNodeMenu {
    private final Direction targetFace;

    public KleisDominionWandMenu(int containerId, Inventory inventory, BlockPos pos, Direction face,
            String lineName, NodeFaceMode mode, int resourceMask, int priority) {
        this(containerId, inventory, pos, face,
                clientEndpoint(inventory, pos, face, lineName, mode, resourceMask, priority));
    }

    public KleisDominionWandMenu(int containerId, Inventory inventory, BlockPos pos, Direction face,
            KleisVirtualNodeBlockEntity endpoint) {
        super(ModMenus.KLEIS_DOMINION_WAND.get(), containerId, inventory, pos, false, endpoint);
        this.targetFace = face;
        addDataSlot(endpointState(endpoint));
        addDataSlot(resourceState(endpoint));
        addDataSlot(priorityState(endpoint));
    }

    public Direction targetFace() {
        return targetFace;
    }

    /** Compatibility for clients that still have the first implementation's compact action packet. */
    public void handleAction(int action) {
        KleisVirtualNodeBlockEntity endpoint = (KleisVirtualNodeBlockEntity) endpointNode();
        Direction face = KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION;
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

    private static KleisVirtualNodeBlockEntity clientEndpoint(Inventory inventory, BlockPos pos, Direction face,
            String lineName, NodeFaceMode mode, int resourceMask, int priority) {
        KleisVirtualNodeBlockEntity endpoint = new KleisVirtualNodeBlockEntity(pos, face);
        endpoint.setLevel(inventory.player.level());
        endpoint.setSuppressChanges(true);
        endpoint.selectPlayerLine(java.util.UUID.nameUUIDFromBytes(
                lineName.getBytes(java.nio.charset.StandardCharsets.UTF_8)), lineName, lineName);
        endpoint.setFaceMode(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION, mode);
        endpoint.setItemsEnabled(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION, (resourceMask & 1) != 0);
        endpoint.setFluidsEnabled(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION, (resourceMask & 2) != 0);
        endpoint.setEnergyEnabled(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION, (resourceMask & 4) != 0);
        endpoint.adjustPriority(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION, priority);
        endpoint.setSuppressChanges(false);
        return endpoint;
    }

    private static DataSlot endpointState(KleisVirtualNodeBlockEntity endpoint) {
        return new DataSlot() {
            @Override public int get() { return endpoint.getFaceMode(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION).ordinal(); }
            @Override public void set(int value) {
                NodeFaceMode[] values = NodeFaceMode.values();
                endpoint.setFaceMode(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION,
                        values[Math.max(0, Math.min(values.length - 1, value))]);
            }
        };
    }

    private static DataSlot resourceState(KleisVirtualNodeBlockEntity endpoint) {
        return new DataSlot() {
            @Override public int get() {
                Direction face = KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION;
                return (endpoint.isItemsEnabled(face) ? 1 : 0)
                        | (endpoint.isFluidsEnabled(face) ? 2 : 0)
                        | (endpoint.isEnergyEnabled(face) ? 4 : 0);
            }
            @Override public void set(int value) {
                Direction face = KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION;
                endpoint.setItemsEnabled(face, (value & 1) != 0);
                endpoint.setFluidsEnabled(face, (value & 2) != 0);
                endpoint.setEnergyEnabled(face, (value & 4) != 0);
            }
        };
    }

    private static DataSlot priorityState(KleisVirtualNodeBlockEntity endpoint) {
        return new DataSlot() {
            @Override public int get() { return endpoint.getPriority(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION); }
            @Override public void set(int value) {
                Direction face = KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION;
                endpoint.adjustPriority(face, value - endpoint.getPriority(face));
            }
        };
    }
}
