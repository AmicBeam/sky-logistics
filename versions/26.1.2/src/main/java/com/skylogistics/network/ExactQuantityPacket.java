package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.menu.SkyNecklaceMenu;
import com.skylogistics.menu.SkyNodeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ExactQuantityPacket(int amount) implements CustomPacketPayload {
    public static final Type<ExactQuantityPacket> TYPE = new Type<>(SkyLogistics.id("exact_quantity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExactQuantityPacket> STREAM_CODEC =
            StreamCodec.ofMember(ExactQuantityPacket::encode, ExactQuantityPacket::decode);
    private static void encode(ExactQuantityPacket packet, FriendlyByteBuf buffer) { buffer.writeVarInt(packet.amount); }
    private static ExactQuantityPacket decode(FriendlyByteBuf buffer) { return new ExactQuantityPacket(buffer.readVarInt()); }
    public static void handle(ExactQuantityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof SkyNecklaceMenu menu) {
                menu.setExactQuantity(player, packet.amount);
            } else if (player.containerMenu instanceof SkyNodeMenu menu) {
                menu.setExactQuantity(player, packet.amount);
            }
        });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
