package com.skylogistics.network;

import com.skylogistics.menu.ConfiguratorMenu;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.network.FriendlyByteBuf;
import com.skylogistics.SkyLogistics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PermissionSnapshotPacket(int containerId, boolean publicAccess, String query, int page, int total, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<PermissionSnapshotPacket> TYPE = new Type<>(SkyLogistics.id("permission_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PermissionSnapshotPacket> STREAM_CODEC = StreamCodec.ofMember(PermissionSnapshotPacket::encode, PermissionSnapshotPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void encode(PermissionSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId); buffer.writeBoolean(packet.publicAccess);
        buffer.writeUtf(packet.query, 48); buffer.writeVarInt(packet.page); buffer.writeVarInt(packet.total);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) { buffer.writeUUID(entry.id); buffer.writeUtf(entry.name, 64); buffer.writeBoolean(entry.granted); }
    }
    public static PermissionSnapshotPacket decode(FriendlyByteBuf buffer) { return decodeEntries(buffer); }
    public record Entry(UUID id, String name, boolean granted) {}
    private static PermissionSnapshotPacket decodeEntries(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt(); boolean publicAccess = buffer.readBoolean();
        String query = buffer.readUtf(48); int page = buffer.readVarInt(); int total = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > 7) throw new IllegalArgumentException("Invalid permission page size");
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean()));
        return new PermissionSnapshotPacket(container, publicAccess, query, page, total, List.copyOf(entries));
    }
    public static void handle(PermissionSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof ConfiguratorMenu menu && menu.containerId == packet.containerId)
                menu.acceptPermissions(packet);
        });
    }
}
