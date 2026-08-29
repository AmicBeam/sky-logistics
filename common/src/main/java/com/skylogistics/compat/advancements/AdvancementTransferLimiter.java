package com.skylogistics.compat.advancements;

import com.skylogistics.compat.astages.StageRateRules;
import com.skylogistics.compat.astages.TransferRates;
import com.skylogistics.compat.astages.TransferResource;
import com.skylogistics.config.SkyLogisticsConfig;
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
    private static final Map<UUID, CachedRates> PLAYER_RATES = new HashMap<>();
    private static StageRateRules cachedRules;
    private static long rulesGameTime = Long.MIN_VALUE;

    private AdvancementTransferLimiter() {
    }

    public static long limit(MinecraftServer server, UUID ownerId, TransferResource resource, long amount,
            long gameTime) {
        if (amount <= 0L || !SkyLogisticsConfig.enableAdvancementTransferRates()) {
            return amount;
        }
        if (rulesGameTime != gameTime) {
            StageRateRules rules = SkyLogisticsConfig.advancementTransferRateRules();
            if (rules != cachedRules) {
                PLAYER_RATES.clear();
                cachedRules = rules;
            }
            rulesGameTime = gameTime;
        }
        if (ownerId == null || server == null) {
            return initialLimit(resource, amount);
        }
        CachedRates cached = PLAYER_RATES.get(ownerId);
        if (cached == null || cached.gameTime() != gameTime) {
            ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
            Set<String> completed = player == null ? Set.of() : completedConfiguredAdvancements(server, player);
            cached = new CachedRates(gameTime, cachedRules.ratesFor(completed));
            PLAYER_RATES.put(ownerId, cached);
        }
        return Math.min(amount, cached.rates().get(resource));
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
