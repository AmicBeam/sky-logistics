package com.skylogistics.compat.astages;

import com.skylogistics.config.SkyLogisticsConfig;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AStagesTransferLimiter {
    private static final Map<UUID, CachedRates> PLAYER_RATES = new HashMap<>();
    private static StageRateRules cachedRules;
    private static long rulesGameTime = Long.MIN_VALUE;
    private static Api api;
    private static boolean apiResolved;

    private AStagesTransferLimiter() {
    }

    public static long limit(UUID ownerId, TransferResource resource, long amount, long gameTime) {
        if (amount <= 0L || !SkyLogisticsConfig.enableAStagesTransferRates()) {
            return amount;
        }
        if (rulesGameTime != gameTime) {
            StageRateRules rules = SkyLogisticsConfig.aStagesTransferRateRules();
            if (rules != cachedRules) {
                PLAYER_RATES.clear();
                cachedRules = rules;
            }
            rulesGameTime = gameTime;
        }
        Api resolvedApi = api();
        if (ownerId == null || resolvedApi == null) {
            return Math.min(amount, cachedRules.initial().get(resource));
        }
        CachedRates cached = PLAYER_RATES.get(ownerId);
        if (cached == null || cached.gameTime() != gameTime) {
            cached = new CachedRates(gameTime, cachedRules.ratesFor(resolvedApi.stages(ownerId)));
            PLAYER_RATES.put(ownerId, cached);
        }
        return Math.min(amount, cached.rates().get(resource));
    }

    private static Api api() {
        if (!apiResolved) {
            apiResolved = true;
            try {
                Class<?> holderClass = Class.forName("com.alessandro.astages.api.holder.AHolder");
                Class<?> utilsClass = Class.forName("com.alessandro.astages.api.util.AStagesUtils");
                api = new Api(holderClass.getMethod("player", UUID.class),
                        utilsClass.getMethod("getStages", holderClass));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                api = null;
            }
        }
        return api;
    }

    private record CachedRates(long gameTime, TransferRates rates) {
    }

    private record Api(Method playerHolder, Method getStages) {
        Set<String> stages(UUID playerId) {
            try {
                Object holder = playerHolder.invoke(null, playerId);
                Object value = getStages.invoke(null, holder);
                if (!(value instanceof Set<?> values)) {
                    return Set.of();
                }
                Map<String, Boolean> strings = new HashMap<>();
                for (Object entry : values) {
                    if (entry instanceof String stage) {
                        strings.put(stage, Boolean.TRUE);
                    }
                }
                return Collections.unmodifiableSet(strings.keySet());
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return Set.of();
            }
        }
    }
}
