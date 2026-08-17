package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.menu.FilterListMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChemicalFilterPacket(int slot, String chemical) implements CustomPacketPayload {
    public static final Type<ChemicalFilterPacket> TYPE = new Type<>(SkyLogistics.id("chemical_filter"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChemicalFilterPacket> STREAM_CODEC =
            StreamCodec.ofMember(ChemicalFilterPacket::encode, ChemicalFilterPacket::decode);
    private static void encode(ChemicalFilterPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot); buffer.writeUtf(packet.chemical, 128);
    }
    private static ChemicalFilterPacket decode(FriendlyByteBuf buffer) {
        return new ChemicalFilterPacket(buffer.readVarInt(), buffer.readUtf(128));
    }
    public static void handle(ChemicalFilterPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof FilterListMenu menu) {
                menu.setGhostChemical(packet.slot, packet.chemical);
            }
        });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
