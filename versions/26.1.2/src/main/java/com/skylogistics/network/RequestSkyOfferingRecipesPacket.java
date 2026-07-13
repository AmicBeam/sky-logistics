package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestSkyOfferingRecipesPacket() implements CustomPacketPayload {
    public static final RequestSkyOfferingRecipesPacket INSTANCE = new RequestSkyOfferingRecipesPacket();
    public static final Type<RequestSkyOfferingRecipesPacket> TYPE =
            new Type<>(SkyLogistics.id("request_sky_offering_recipes"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSkyOfferingRecipesPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public static void handle(RequestSkyOfferingRecipesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SkyOfferingRecipesPacket.sendToPlayer(player);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
