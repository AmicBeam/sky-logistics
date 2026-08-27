package com.skylogistics.compat.advancements;

import com.skylogistics.config.SkyLogisticsConfig;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Mirrors the default configured vanilla milestones into the Sky Logistics advancement chain. */
public final class AdvancementDisplaySync {
    private static final String ROOT = "skylogistics:transfer_rates/root";
    private static final Set<UUID> SYNCING = new HashSet<>();

    private AdvancementDisplaySync() {
    }

    public static void sync(ServerPlayer player) {
        if (player == null || !SYNCING.add(player.getUUID())) return;
        try {
            boolean enabled = SkyLogisticsConfig.enableAdvancementTransferRates();
            var configured = SkyLogisticsConfig.advancementDisplayEntries();
            Object manager = player.level().getServer().getAdvancements();
            Object playerAdvancements = player.getAdvancements();
            Method lookup = findLookup(manager.getClass());
            if (lookup == null) return;
            setAwarded(manager, playerAdvancements, lookup, ROOT, enabled && !configured.isEmpty());
            for (int index = 0; index < configured.size(); index++) {
                AdvancementDisplayEntry milestone = configured.get(index);
                boolean completed = enabled && isCompleted(manager, playerAdvancements, lookup,
                        milestone.advancement());
                setAwarded(manager, playerAdvancements, lookup,
                        "skylogistics:transfer_rates/entry_" + index, completed);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        } finally {
            SYNCING.remove(player.getUUID());
        }
    }

    private static boolean isCompleted(Object manager, Object playerAdvancements, Method lookup, String id)
            throws ReflectiveOperationException {
        Object advancement = lookup.invoke(manager, parseResourceId(lookup.getParameterTypes()[0], id));
        if (advancement == null) return false;
        Object progress = progress(playerAdvancements, advancement);
        return progress != null && Boolean.TRUE.equals(progress.getClass().getMethod("isDone").invoke(progress));
    }

    private static void setAwarded(Object manager, Object playerAdvancements, Method lookup, String id,
            boolean awarded) throws ReflectiveOperationException {
        Object advancement = lookup.invoke(manager, parseResourceId(lookup.getParameterTypes()[0], id));
        if (advancement == null) return;
        Object progress = progress(playerAdvancements, advancement);
        if (progress == null) return;
        boolean done = Boolean.TRUE.equals(progress.getClass().getMethod("isDone").invoke(progress));
        if (done == awarded) return;
        Method update = findCompatibleMethod(playerAdvancements.getClass(), awarded ? "award" : "revoke",
                advancement.getClass(), String.class);
        if (update != null) update.invoke(playerAdvancements, advancement, "unlocked");
    }

    private static Object progress(Object playerAdvancements, Object advancement) throws ReflectiveOperationException {
        Method method = findCompatibleMethod(playerAdvancements.getClass(), "getOrStartProgress",
                advancement.getClass());
        return method == null ? null : method.invoke(playerAdvancements, advancement);
    }

    private static Method findLookup(Class<?> owner) {
        for (String name : new String[] {"get", "getAdvancement"}) {
            for (Method method : owner.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1) {
                    String parameter = method.getParameterTypes()[0].getSimpleName();
                    if (parameter.equals("ResourceLocation") || parameter.equals("Identifier")) return method;
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

    private static Method findCompatibleMethod(Class<?> owner, String name, Class<?>... arguments) {
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) compatible &= parameters[i].isAssignableFrom(arguments[i]);
            if (compatible) return method;
        }
        return null;
    }
}
