package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.registry.ModItems;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record KleisOverlayRequestPacket(boolean editNearby) implements CustomPacketPayload {
    public static final Type<KleisOverlayRequestPacket> TYPE = new Type<>(SkyLogistics.id("kleis_overlay_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KleisOverlayRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buffer) -> buffer.writeBoolean(packet.editNearby),
                    buffer -> new KleisOverlayRequestPacket(buffer.readBoolean()));

    public static void handle(KleisOverlayRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            boolean actualEdit = player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                    && player.getOffhandItem().is(ModItems.KLEIS_DOMINION_WAND.get());
            boolean currentLine = player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                    && player.getOffhandItem().is(ModItems.CONFIGURATOR.get());
            if (packet.editNearby != actualEdit || !actualEdit && !currentLine) return;
            KleisEndpointSavedData data = KleisEndpointSavedData.get(player.level().getServer());
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
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
