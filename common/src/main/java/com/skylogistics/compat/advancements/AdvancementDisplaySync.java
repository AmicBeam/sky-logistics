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
    private static final String[][] MILESTONES = {
            {"minecraft:story/smelt_iron", "skylogistics:transfer_rates/smelt_iron"},
            {"minecraft:story/mine_diamond", "skylogistics:transfer_rates/mine_diamond"},
            {"minecraft:story/enchant_item", "skylogistics:transfer_rates/enchant_item"},
            {"minecraft:adventure/trade_at_world_height", "skylogistics:transfer_rates/trade_at_world_height"},
            {"minecraft:nether/create_beacon", "skylogistics:transfer_rates/create_beacon"},
            {"minecraft:nether/create_full_beacon", "skylogistics:transfer_rates/create_full_beacon"},
            {"minecraft:end/elytra", "skylogistics:transfer_rates/elytra"}
    };
    private static final Set<UUID> SYNCING = new HashSet<>();

    private AdvancementDisplaySync() {
    }

    public static void sync(ServerPlayer player) {
        if (player == null || !SYNCING.add(player.getUUID())) return;
        try {
            boolean enabled = SkyLogisticsConfig.enableAdvancementTransferRates();
            Set<String> configured = SkyLogisticsConfig.advancementTransferRateRules().stages().keySet();
            Object manager = player.level().getServer().getAdvancements();
            Object playerAdvancements = player.getAdvancements();
            Method lookup = findLookup(manager.getClass());
            if (lookup == null) return;
            setAwarded(manager, playerAdvancements, lookup, ROOT, enabled && !configured.isEmpty());
            for (String[] milestone : MILESTONES) {
                boolean completed = enabled && configured.contains(milestone[0])
                        && isCompleted(manager, playerAdvancements, lookup, milestone[0]);
                setAwarded(manager, playerAdvancements, lookup, milestone[1], completed);
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
