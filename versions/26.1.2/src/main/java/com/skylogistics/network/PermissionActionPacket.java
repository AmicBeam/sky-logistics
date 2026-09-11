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

public record PermissionActionPacket(int containerId, int action, String query, int page, UUID target) implements CustomPacketPayload {
    public static final Type<PermissionActionPacket> TYPE = new Type<>(SkyLogistics.id("permission_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PermissionActionPacket> STREAM_CODEC = StreamCodec.ofMember(PermissionActionPacket::encode, PermissionActionPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void encode(PermissionActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId); buffer.writeVarInt(packet.action);
        buffer.writeUtf(packet.query, 48); buffer.writeVarInt(packet.page);
        buffer.writeBoolean(packet.target != null); if (packet.target != null) buffer.writeUUID(packet.target);
    }
    public static PermissionActionPacket decode(FriendlyByteBuf buffer) { return new PermissionActionPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(48), buffer.readVarInt(), buffer.readBoolean() ? buffer.readUUID() : null); }
    public static void handle(PermissionActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player != null && player.containerMenu instanceof ConfiguratorMenu menu
                    && menu.containerId == packet.containerId && menu.stillValid(player)) {
                menu.permissionAction(player, packet.action, packet.query, packet.page, packet.target);
            }
        });
    }
}
