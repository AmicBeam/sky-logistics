package com.skylogistics.network;

import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.menu.SkyNodeMenu;
import com.skylogistics.menu.SkyNecklaceMenu;
import com.skylogistics.menu.TagFilterListMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record MenuActionPacket(int action) {
    public static void encode(MenuActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.action);
    }

    public static MenuActionPacket decode(FriendlyByteBuf buffer) {
        return new MenuActionPacket(buffer.readVarInt());
    }

    public static void handle(MenuActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof ConfiguratorMenu menu) {
                menu.applyAction(player, packet.action);
            } else if (player.containerMenu instanceof SkyNodeMenu menu) {
                menu.applyAction(player, packet.action);
            } else if (player.containerMenu instanceof SkyNecklaceMenu menu) {
                menu.applyAction(player, packet.action);
            } else if (player.containerMenu instanceof FilterListMenu menu) {
                menu.applyAction(player, packet.action);
            } else if (player.containerMenu instanceof TagFilterListMenu menu) {
                menu.applyAction(player, packet.action);
            }
        });
        context.setPacketHandled(true);
    }
}
