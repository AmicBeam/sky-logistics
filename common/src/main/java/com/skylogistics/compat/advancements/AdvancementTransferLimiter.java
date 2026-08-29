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
    // Forge 1.20.1 production uses SRG names; Mojmap names are used in dev and newer NeoForge versions.
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
            Method lookup = findMethod(manager.getClass(), "get", "getAdvancement", "m_136041_");
            if (lookup == null) return Set.of();
            for (String configuredId : cachedRules.stages().keySet()) {
                Object id = parseResourceId(lookup.getParameterTypes()[0], configuredId);
                if (id == null) continue;
                Object advancement = lookup.invoke(manager, id);
                if (advancement == null) continue;
                Method progressMethod = findCompatibleMethod(playerAdvancements.getClass(), advancement.getClass(),
                        "getOrStartProgress", "m_135996_");
                if (progressMethod == null) continue;
                Object progress = progressMethod.invoke(playerAdvancements, advancement);
                Method isDone = findNoArgMethod(progress.getClass(), "isDone", "m_8193_");
                if (isDone == null) continue;
                if (Boolean.TRUE.equals(isDone.invoke(progress))) completed.add(configuredId);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Set.of();
        }
        return Set.copyOf(completed);
    }

    private static Method findMethod(Class<?> owner, String... names) {
        for (String name : names) {
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1) {
                    String parameterName = method.getParameterTypes()[0].getSimpleName();
                    if (parameterName.equals("ResourceLocation") || parameterName.equals("Identifier")) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static Object parseResourceId(Class<?> type, String value) throws ReflectiveOperationException {
        for (String methodName : new String[] {"tryParse", "parse"}) {
            try {
                return type.getMethod(methodName, String.class).invoke(null, value);
            } catch (NoSuchMethodException ignored) {
            }
        }
        try {
            return type.getConstructor(String.class).newInstance(value);
        } catch (NoSuchMethodException ignored) {
            int separator = value.indexOf(':');
            String namespace = separator < 0 ? "minecraft" : value.substring(0, separator);
            String path = separator < 0 ? value : value.substring(separator + 1);
            return type.getMethod("fromNamespaceAndPath", String.class, String.class)
                    .invoke(null, namespace, path);
        }
    }

    private static Method findCompatibleMethod(Class<?> owner, Class<?> argument, String... names) {
        for (String name : names) {
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(argument)) {
                    return method;
                }
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                return owner.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private record CachedRates(long gameTime, TransferRates rates) {
    }
}
