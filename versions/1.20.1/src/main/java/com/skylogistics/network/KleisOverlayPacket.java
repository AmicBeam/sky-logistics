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

public record KleisOverlayPacket(UUID lineId, List<Entry> entries) {
    public record Entry(BlockPos pos, Direction face, NodeFaceMode mode) {}
    public static KleisOverlayPacket from(UUID lineId, List<KleisEndpointSavedData.Snapshot> snapshots) {
        return new KleisOverlayPacket(lineId, snapshots.stream().limit(512)
                .map(s -> new Entry(s.pos(), s.face(), s.mode())).toList());
    }
    public static void encode(KleisOverlayPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.lineId); buffer.writeVarInt(packet.entries.size());
        for (Entry e : packet.entries) { buffer.writeBlockPos(e.pos); buffer.writeEnum(e.face); buffer.writeEnum(e.mode); }
    }
    public static KleisOverlayPacket decode(FriendlyByteBuf buffer) {
        UUID line = buffer.readUUID(); int size = Math.min(512, buffer.readVarInt()); List<Entry> entries = new ArrayList<>(size);
        for (int i=0;i<size;i++) entries.add(new Entry(buffer.readBlockPos(), buffer.readEnum(Direction.class), buffer.readEnum(NodeFaceMode.class)));
        return new KleisOverlayPacket(line, List.copyOf(entries));
    }
    public static void handle(KleisOverlayPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientKleisOverlays.apply(packet)));
        context.setPacketHandled(true);
    }
}
