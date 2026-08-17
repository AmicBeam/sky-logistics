package com.skylogistics.client;

import com.skylogistics.network.DistributorTargetsPacket;
import com.skylogistics.network.ModNetworking;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ClientDistributorHighlights {
    private static final long REFRESH_TICKS = 20L;
    private static AimKey requested;
    private static long lastRequestTick = Long.MIN_VALUE;
    private static List<DistributorTargetsPacket.Entry> targets = List.of();

    private ClientDistributorHighlights() {}

    public static List<DistributorTargetsPacket.Entry> targets(BlockPos distributorPos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        AimKey aim = new AimKey(minecraft.level.dimension(), distributorPos.immutable());
        long now = minecraft.level.getGameTime();
        if (!aim.equals(requested)) {
            requested = aim;
            targets = List.of();
            lastRequestTick = Long.MIN_VALUE;
        }
        if (lastRequestTick == Long.MIN_VALUE || now - lastRequestTick >= REFRESH_TICKS) {
            lastRequestTick = now;
            ModNetworking.requestDistributorTargets(distributorPos);
        }
        return targets;
    }

    public static void apply(DistributorTargetsPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && requested != null
                && requested.dimension.equals(minecraft.level.dimension())
                && requested.pos.equals(packet.distributorPos())) {
            targets = packet.entries();
        }
    }

    public static void clear() {
        requested = null;
        targets = List.of();
        lastRequestTick = Long.MIN_VALUE;
    }

    private record AimKey(ResourceKey<Level> dimension, BlockPos pos) {}
}
