package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.menu.SkyNodeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MenuActionPacket(int action) implements CustomPacketPayload {
    public static final Type<MenuActionPacket> TYPE = new Type<>(SkyLogistics.id("menu_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuActionPacket> STREAM_CODEC =
            StreamCodec.ofMember(MenuActionPacket::encode, MenuActionPacket::decode);

    public static void encode(MenuActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.action);
    }

    public static MenuActionPacket decode(FriendlyByteBuf buffer) {
        return new MenuActionPacket(buffer.readVarInt());
    }

    public static void handle(MenuActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.containerMenu instanceof ConfiguratorMenu menu) {
                menu.applyAction(player, packet.action);
            } else if (player.containerMenu instanceof SkyNodeMenu menu) {
                menu.applyAction(player, packet.action);
            } else if (player.containerMenu instanceof FilterListMenu menu) {
                menu.applyAction(player, packet.action);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
