package com.skylogistics.network;

import com.skylogistics.menu.SkyNecklaceMenu;
import com.skylogistics.menu.SkyNodeMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record ExactQuantityPacket(int amount) {
    public static void encode(ExactQuantityPacket packet, FriendlyByteBuf buffer) { buffer.writeVarInt(packet.amount); }
    public static ExactQuantityPacket decode(FriendlyByteBuf buffer) { return new ExactQuantityPacket(buffer.readVarInt()); }
    public static void handle(ExactQuantityPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (player.containerMenu instanceof SkyNecklaceMenu menu) menu.setExactQuantity(player, packet.amount);
        });
        context.setPacketHandled(true);
    }
}
