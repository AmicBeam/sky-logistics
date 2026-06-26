package com.skylogistics.network;

import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.SkyNecklaceMenu;
import com.skylogistics.menu.SkyNodeMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record LineRenamePacket(String lineName) {
    public static void encode(LineRenamePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.lineName, 128);
    }

    public static LineRenamePacket decode(FriendlyByteBuf buffer) {
        return new LineRenamePacket(buffer.readUtf(128));
    }

    public static void handle(LineRenamePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu instanceof ConfiguratorMenu menu) {
                menu.renameCurrentLine(player, packet.lineName);
            } else if (player.containerMenu instanceof SkyNodeMenu menu) {
                menu.renameCurrentLine(player, packet.lineName);
            } else if (player.containerMenu instanceof SkyNecklaceMenu menu) {
                menu.renameCurrentLine(player, packet.lineName);
            }
        });
        context.setPacketHandled(true);
    }
}
