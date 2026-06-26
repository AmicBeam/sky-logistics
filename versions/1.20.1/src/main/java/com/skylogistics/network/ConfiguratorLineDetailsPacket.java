package com.skylogistics.network;

import com.skylogistics.client.ClientConfiguratorLineDetails;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record ConfiguratorLineDetailsPacket(UUID lineId, List<Entry> entries) {
    private static final int MAX_ENTRIES = 64;

    public record Entry(String dimension, BlockPos nodePos, Direction face, BlockPos targetPos,
                        String targetBlockId, NodeFaceMode mode, boolean itemsEnabled,
                        boolean fluidsEnabled, boolean energyEnabled, RedstoneControl redstoneControl,
                        int priority) {
    }

    public static void encode(ConfiguratorLineDetailsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.lineId);
        int size = Math.min(packet.entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Entry entry = packet.entries.get(i);
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeBlockPos(entry.nodePos);
            buffer.writeEnum(entry.face);
            buffer.writeBlockPos(entry.targetPos);
            buffer.writeUtf(entry.targetBlockId, 128);
            buffer.writeEnum(entry.mode);
            buffer.writeBoolean(entry.itemsEnabled);
            buffer.writeBoolean(entry.fluidsEnabled);
            buffer.writeBoolean(entry.energyEnabled);
            buffer.writeEnum(entry.redstoneControl);
            buffer.writeVarInt(entry.priority);
        }
    }

    public static ConfiguratorLineDetailsPacket decode(FriendlyByteBuf buffer) {
        UUID lineId = buffer.readUUID();
        int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUtf(128), buffer.readBlockPos(), buffer.readEnum(Direction.class),
                    buffer.readBlockPos(), buffer.readUtf(128), buffer.readEnum(NodeFaceMode.class),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readEnum(RedstoneControl.class), buffer.readVarInt()));
        }
        return new ConfiguratorLineDetailsPacket(lineId, entries);
    }

    public static void handle(ConfiguratorLineDetailsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientConfiguratorLineDetails.apply(packet)));
        context.setPacketHandled(true);
    }
}
