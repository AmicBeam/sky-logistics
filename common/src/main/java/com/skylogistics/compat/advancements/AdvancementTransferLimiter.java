package com.skylogistics.compat.advancements;

import com.skylogistics.compat.astages.StageRateRules;
import com.skylogistics.compat.astages.TransferRates;
import com.skylogistics.compat.astages.TransferResource;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.network.SkyPlayerLines;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Limits transfer amounts according to vanilla advancements completed by the line owner. */
public final class AdvancementTransferLimiter {
    private static final long FALLBACK_REFRESH_INTERVAL_TICKS = 5L * 20L;
    private static final Map<UUID, CachedRates> PLAYER_RATES = new HashMap<>();
    private static StageRateRules cachedRules;
    private static MinecraftServer cachedServer;
    private static long rulesGameTime = Long.MIN_VALUE;

    private AdvancementTransferLimiter() {
    }

    public static long limit(MinecraftServer server, UUID ownerId, TransferResource resource, long amount,
            long gameTime) {
        if (amount <= 0L || !SkyLogisticsConfig.enableAdvancementTransferRates()) {
            return amount;
        }
        refreshRules(server, gameTime);
        if (!cachedRules.hasUnlocks()) {
            return amount;
        }
        if (ownerId == null || server == null) {
            return initialLimit(resource, amount);
        }
        CachedRates cached = PLAYER_RATES.get(ownerId);
        if (cached == null || cacheExpired(cached.gameTime(), gameTime)) {
            ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
            Set<String> completed;
            if (player == null) {
                completed = SkyPlayerLines.advancementSnapshot(server, ownerId);
            } else {
                completed = completedConfiguredAdvancements(server, player);
                SkyPlayerLines.recordAdvancementSnapshot(server, ownerId, completed);
            }
            cached = new CachedRates(gameTime, cachedRules.ratesFor(completed));
            PLAYER_RATES.put(ownerId, cached);
        }
        return Math.min(amount, cached.rates().get(resource));
    }

    static boolean cacheExpired(long cachedAt, long gameTime) {
        return gameTime < cachedAt || gameTime - cachedAt >= FALLBACK_REFRESH_INTERVAL_TICKS;
    }

    /** Refreshes the persisted snapshot while the player is known to be online. */
    public static void refresh(ServerPlayer player) {
        if (player == null || !SkyLogisticsConfig.enableAdvancementTransferRates()) return;
        MinecraftServer server = player.level().getServer();
        long gameTime = player.level().getGameTime();
        refreshRules(server, gameTime);
        if (!cachedRules.hasUnlocks()) return;
        Set<String> completed = completedConfiguredAdvancements(server, player);
        SkyPlayerLines.recordAdvancementSnapshot(server, player.getUUID(), completed);
        PLAYER_RATES.put(player.getUUID(), new CachedRates(gameTime, cachedRules.ratesFor(completed)));
    }

    private static void refreshRules(MinecraftServer server, long gameTime) {
        if (cachedServer == server && rulesGameTime == gameTime) return;
        StageRateRules rules = SkyLogisticsConfig.advancementTransferRateRules();
        if (cachedServer != server || rules != cachedRules) PLAYER_RATES.clear();
        cachedServer = server;
        cachedRules = rules;
        rulesGameTime = gameTime;
    }

    private static long initialLimit(TransferResource resource, long amount) {
        return Math.min(amount, cachedRules.initial().get(resource));
    }

    private static Set<String> completedConfiguredAdvancements(MinecraftServer server, ServerPlayer player) {
        Set<String> completed = new HashSet<>();
        try {
            Object manager = server.getAdvancements();
            Object playerAdvancements = player.getAdvancements();
            Method lookup = AdvancementAccess.findLookup(manager);
            if (lookup == null) return Set.of();
            for (String configuredId : cachedRules.stages().keySet()) {
                Object advancement = AdvancementAccess.findAdvancement(manager, lookup, configuredId);
                if (advancement == null) continue;
                if (AdvancementAccess.isDone(AdvancementAccess.progress(playerAdvancements, advancement))) {
                    completed.add(configuredId);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Set.of();
        }
        return Set.copyOf(completed);
    }

    private record CachedRates(long gameTime, TransferRates rates) {
    }
}
