package com.skylogistics.network;

import com.skylogistics.menu.FilterListMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record ChemicalFilterPacket(int slot, String chemical) {
    public static void encode(ChemicalFilterPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot); buffer.writeUtf(packet.chemical, 128);
    }
    public static ChemicalFilterPacket decode(FriendlyByteBuf buffer) {
        return new ChemicalFilterPacket(buffer.readVarInt(), buffer.readUtf(128));
    }
    public static void handle(ChemicalFilterPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null && context.getSender().containerMenu instanceof FilterListMenu menu) {
                menu.setGhostChemical(packet.slot, packet.chemical);
            }
        });
        context.setPacketHandled(true);
    }
}
