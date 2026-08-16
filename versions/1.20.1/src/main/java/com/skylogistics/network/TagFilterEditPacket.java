package com.skylogistics.network;

import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.menu.TagFilterListMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record TagFilterEditPacket(int slot, String tag, boolean fluid) {
    public static void encode(TagFilterEditPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot);
        buffer.writeUtf(packet.tag, TagFilterListItem.MAX_TAG_LENGTH);
        buffer.writeBoolean(packet.fluid);
    }

    public static TagFilterEditPacket decode(FriendlyByteBuf buffer) {
        return new TagFilterEditPacket(buffer.readVarInt(), buffer.readUtf(TagFilterListItem.MAX_TAG_LENGTH),
                buffer.readBoolean());
    }

    public static void handle(TagFilterEditPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof TagFilterListMenu menu)) {
                return;
            }
            menu.setTag(packet.slot, packet.tag, packet.fluid);
        });
        context.setPacketHandled(true);
    }
}
