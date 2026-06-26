package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.SkyNecklaceMenu;
import com.skylogistics.menu.SkyNodeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LineRenamePacket(String lineName) implements CustomPacketPayload {
    public static final Type<LineRenamePacket> TYPE = new Type<>(SkyLogistics.id("line_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LineRenamePacket> STREAM_CODEC =
            StreamCodec.ofMember(LineRenamePacket::encode, LineRenamePacket::decode);

    public static void encode(LineRenamePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.lineName, 128);
    }

    public static LineRenamePacket decode(FriendlyByteBuf buffer) {
        return new LineRenamePacket(buffer.readUtf(128));
    }

    public static void handle(LineRenamePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.containerMenu instanceof ConfiguratorMenu menu) {
                menu.renameCurrentLine(player, packet.lineName);
            } else if (player.containerMenu instanceof SkyNodeMenu menu) {
                menu.renameCurrentLine(player, packet.lineName);
            } else if (player.containerMenu instanceof SkyNecklaceMenu menu) {
                menu.renameCurrentLine(player, packet.lineName);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
