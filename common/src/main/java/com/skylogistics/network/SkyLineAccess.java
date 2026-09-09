package com.skylogistics.network;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

public final class SkyLineAccess {
    private SkyLineAccess() {}

    public static boolean canUse(Player player, UUID line) {
        if (player == null) return false;
        MinecraftServer server = player.level().getServer();
        if (server == null) return true; // Client prediction; the server is authoritative.
        if (line == null) return true; // Unbound endpoint.
        UUID owner = SkyPlayerLines.ownerOf(server, line);
        return SkyPlayerLines.get(server).permissions().allows(owner, player.getUUID());
    }

    public static boolean check(Player player, UUID line) {
        if (canUse(player, line)) return true;
        if (player != null) player.sendSystemMessage(Component.translatable("message.skylogistics.permission.denied"));
        return false;
    }

    public static void rememberPlayers(MinecraftServer server) {
        SkyPlayerLines data = SkyPlayerLines.get(server);
        for (Player player : server.getPlayerList().getPlayers()) {
            if (data.permissions().remember(player.getUUID(), player.getName().getString())) data.setDirty();
        }
    }
}
