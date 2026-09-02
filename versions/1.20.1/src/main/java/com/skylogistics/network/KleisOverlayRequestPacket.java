package com.skylogistics.network;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.registry.ModItems;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record KleisOverlayRequestPacket(boolean editNearby) {
    public static void encode(KleisOverlayRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.editNearby);
    }

    public static KleisOverlayRequestPacket decode(FriendlyByteBuf buffer) {
        return new KleisOverlayRequestPacket(buffer.readBoolean());
    }

    public static void handle(KleisOverlayRequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            boolean actualEdit = player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                    && player.getOffhandItem().is(ModItems.KLEIS_DOMINION_WAND.get());
            boolean currentLine = player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                    && player.getOffhandItem().is(ModItems.CONFIGURATOR.get());
            if (packet.editNearby != actualEdit || !actualEdit && !currentLine) return;
            KleisEndpointSavedData data = KleisEndpointSavedData.get(player.getServer());
            if (actualEdit) {
                UUID selected = ConfiguratorItem.readLineId(player.getMainHandItem());
                ModNetworking.sendToPlayer(player, KleisOverlayPacket.from(true, selected,
                        data.snapshotsNearby(player, player.level().dimension(), player.blockPosition(), 64)));
            } else {
                UUID selected = ConfiguratorItem.readLineId(player.getOffhandItem());
                if (selected != null) ModNetworking.sendToPlayer(player, KleisOverlayPacket.from(false, selected,
                        data.snapshots(player, player.level().dimension(), selected, player.blockPosition(), 64)));
            }
        });
        context.setPacketHandled(true);
    }
}
