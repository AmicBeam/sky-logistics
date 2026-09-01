package com.skylogistics.network;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record KleisOverlayRequestPacket() {
    public static void encode(KleisOverlayRequestPacket packet, FriendlyByteBuf buffer) {}
    public static KleisOverlayRequestPacket decode(FriendlyByteBuf buffer) { return new KleisOverlayRequestPacket(); }
    public static void handle(KleisOverlayRequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                    || !player.getOffhandItem().is(ModItems.CONFIGURATOR.get())) return;
            java.util.UUID lineId = ConfiguratorItem.readLineId(player.getOffhandItem());
            if (lineId == null) return;
            ModNetworking.sendToPlayer(player, KleisOverlayPacket.from(lineId,
                    KleisEndpointSavedData.get(player.getServer()).snapshots(player.level().dimension(), lineId,
                            player.blockPosition(), 64)));
        });
        context.setPacketHandled(true);
    }
}
