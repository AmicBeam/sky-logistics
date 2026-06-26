package com.skylogistics.network;

import com.skylogistics.client.ClientLineNames;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record LineNamePacket(UUID lineId, String assignedName, String displayName) {
    public static void encode(LineNamePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.lineId);
        buffer.writeUtf(packet.assignedName, 128);
        buffer.writeUtf(packet.displayName, 128);
    }

    public static LineNamePacket decode(FriendlyByteBuf buffer) {
        return new LineNamePacket(buffer.readUUID(), buffer.readUtf(128), buffer.readUtf(128));
    }

    public static void handle(LineNamePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientLineNames.apply(packet.lineId(), packet.assignedName(), packet.displayName())));
        context.setPacketHandled(true);
    }
}
