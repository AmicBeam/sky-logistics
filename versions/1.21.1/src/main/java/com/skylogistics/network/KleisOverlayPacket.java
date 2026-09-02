package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientKleisOverlays;
import com.skylogistics.util.NodeFaceMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record KleisOverlayPacket(boolean editNearby, UUID selectedLineId, List<Entry> entries)
        implements CustomPacketPayload {
    public record Entry(BlockPos pos, Direction face, NodeFaceMode mode, UUID lineId, int revision) {}
    public static final Type<KleisOverlayPacket> TYPE = new Type<>(SkyLogistics.id("kleis_overlay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KleisOverlayPacket> STREAM_CODEC =
            StreamCodec.ofMember(KleisOverlayPacket::encode, KleisOverlayPacket::decode);

    public static KleisOverlayPacket from(boolean editNearby, UUID selectedLineId,
            List<KleisEndpointSavedData.Snapshot> snapshots) {
        return new KleisOverlayPacket(editNearby, selectedLineId, snapshots.stream().limit(512)
                .map(snapshot -> new Entry(snapshot.pos(), snapshot.face(), snapshot.mode(),
                        snapshot.lineId(), snapshot.revision())).toList());
    }

    private static void encode(KleisOverlayPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.editNearby);
        buffer.writeBoolean(packet.selectedLineId != null);
        if (packet.selectedLineId != null) buffer.writeUUID(packet.selectedLineId);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeBlockPos(entry.pos); buffer.writeEnum(entry.face); buffer.writeEnum(entry.mode);
            buffer.writeUUID(entry.lineId); buffer.writeVarInt(entry.revision);
        }
    }

    private static KleisOverlayPacket decode(RegistryFriendlyByteBuf buffer) {
        boolean editNearby = buffer.readBoolean();
        UUID selected = buffer.readBoolean() ? buffer.readUUID() : null;
        int size = Math.max(0, Math.min(512, buffer.readVarInt()));
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(buffer.readBlockPos(),
                buffer.readEnum(Direction.class), buffer.readEnum(NodeFaceMode.class),
                buffer.readUUID(), buffer.readVarInt()));
        return new KleisOverlayPacket(editNearby, selected, List.copyOf(entries));
    }

    public static void handle(KleisOverlayPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientKleisOverlays.apply(packet));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
