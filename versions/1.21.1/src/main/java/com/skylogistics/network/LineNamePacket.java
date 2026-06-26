package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientLineNames;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LineNamePacket(UUID lineId, String assignedName, String displayName) implements CustomPacketPayload {
    public static final Type<LineNamePacket> TYPE = new Type<>(SkyLogistics.id("line_name"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LineNamePacket> STREAM_CODEC =
            StreamCodec.ofMember(LineNamePacket::encode, LineNamePacket::decode);

    public static void encode(LineNamePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.lineId);
        buffer.writeUtf(packet.assignedName, 128);
        buffer.writeUtf(packet.displayName, 128);
    }

    public static LineNamePacket decode(FriendlyByteBuf buffer) {
        return new LineNamePacket(buffer.readUUID(), buffer.readUtf(128), buffer.readUtf(128));
    }

    public static void handle(LineNamePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientLineNames.apply(packet.lineId(), packet.assignedName(), packet.displayName()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
