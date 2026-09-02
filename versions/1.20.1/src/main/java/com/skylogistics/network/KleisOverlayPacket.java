package com.skylogistics.network;

import com.skylogistics.client.ClientKleisOverlays;
import com.skylogistics.util.NodeFaceMode;
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

public record KleisOverlayPacket(boolean editNearby, UUID selectedLineId, List<Entry> entries) {
    public record Entry(BlockPos pos, Direction face, NodeFaceMode mode, UUID lineId, String lineName, int revision) {}

    public static KleisOverlayPacket from(boolean editNearby, UUID selectedLineId,
            List<KleisEndpointSavedData.Snapshot> snapshots) {
        return new KleisOverlayPacket(editNearby, selectedLineId, snapshots.stream().limit(512)
                .map(snapshot -> new Entry(snapshot.pos(), snapshot.face(), snapshot.mode(),
                        snapshot.lineId(), snapshot.lineName(), snapshot.revision())).toList());
    }

    public static void encode(KleisOverlayPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.editNearby);
        buffer.writeBoolean(packet.selectedLineId != null);
        if (packet.selectedLineId != null) buffer.writeUUID(packet.selectedLineId);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeBlockPos(entry.pos); buffer.writeEnum(entry.face); buffer.writeEnum(entry.mode);
            buffer.writeUUID(entry.lineId); buffer.writeUtf(entry.lineName, 48); buffer.writeVarInt(entry.revision);
        }
    }

    public static KleisOverlayPacket decode(FriendlyByteBuf buffer) {
        boolean editNearby = buffer.readBoolean();
        UUID selected = buffer.readBoolean() ? buffer.readUUID() : null;
        int size = Math.max(0, Math.min(512, buffer.readVarInt()));
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(buffer.readBlockPos(),
                buffer.readEnum(Direction.class), buffer.readEnum(NodeFaceMode.class),
                buffer.readUUID(), buffer.readUtf(48), buffer.readVarInt()));
        return new KleisOverlayPacket(editNearby, selected, List.copyOf(entries));
    }

    public static void handle(KleisOverlayPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientKleisOverlays.apply(packet)));
        context.setPacketHandled(true);
    }
}
