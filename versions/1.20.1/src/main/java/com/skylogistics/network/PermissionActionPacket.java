package com.skylogistics.network;

import com.skylogistics.menu.ConfiguratorMenu;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

public record PermissionActionPacket(int containerId, int action, String query, int page, UUID target) {
    public static void encode(PermissionActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId); buffer.writeVarInt(packet.action);
        buffer.writeUtf(packet.query, 48); buffer.writeVarInt(packet.page);
        buffer.writeBoolean(packet.target != null); if (packet.target != null) buffer.writeUUID(packet.target);
    }
    public static PermissionActionPacket decode(FriendlyByteBuf buffer) { return new PermissionActionPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(48), buffer.readVarInt(), buffer.readBoolean() ? buffer.readUUID() : null); }
    public static void handle(PermissionActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null && player.containerMenu instanceof ConfiguratorMenu menu
                    && menu.containerId == packet.containerId && menu.stillValid(player)) {
                menu.permissionAction(player, packet.action, packet.query, packet.page, packet.target);
            }
        });
        context.setPacketHandled(true);
    }
}
