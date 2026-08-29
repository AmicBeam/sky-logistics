package com.skylogistics.compat.advancements;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;

/** Version-neutral reflective access to advancement APIs. */
final class AdvancementAccess {
    private static final String[] LOOKUP_NAMES = {"get", "getAdvancement", "m_136041_"};
    private static final String[] PROGRESS_NAMES = {"getOrStartProgress", "m_135996_"};
    private static final String[] IS_DONE_NAMES = {"isDone", "m_8193_"};
    private static final String[] AWARD_NAMES = {"award", "m_135988_"};
    private static final String[] REVOKE_NAMES = {"revoke", "m_135998_"};
    private static final String[] FLUSH_NAMES = {"flushDirty", "m_135992_"};

    private AdvancementAccess() {
    }

    static Method findLookup(Object manager) {
        for (String name : LOOKUP_NAMES) {
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                String parameter = method.getParameterTypes()[0].getSimpleName();
                if (parameter.equals("ResourceLocation") || parameter.equals("Identifier")) return method;
            }
        }
        return null;
    }

    static Object findAdvancement(Object manager, Method lookup, String id) throws ReflectiveOperationException {
        return lookup.invoke(manager, parseResourceId(lookup.getParameterTypes()[0], id));
    }

    static Object progress(Object playerAdvancements, Object advancement) throws ReflectiveOperationException {
        Method method = findCompatibleMethod(playerAdvancements.getClass(), PROGRESS_NAMES, advancement.getClass());
        return method == null ? null : method.invoke(playerAdvancements, advancement);
    }

    static boolean isDone(Object progress) throws ReflectiveOperationException {
        if (progress == null) return false;
        Method method = findNoArgMethod(progress.getClass(), IS_DONE_NAMES);
        return method != null && Boolean.TRUE.equals(method.invoke(progress));
    }

    static void setAwarded(Object playerAdvancements, Object advancement, boolean awarded)
            throws ReflectiveOperationException {
        Object progress = progress(playerAdvancements, advancement);
        if (progress == null || isDone(progress) == awarded) return;
        Method update = findCompatibleMethod(playerAdvancements.getClass(),
                awarded ? AWARD_NAMES : REVOKE_NAMES, advancement.getClass(), String.class);
        if (update != null) update.invoke(playerAdvancements, advancement, "unlocked");
    }

    static void flush(Object playerAdvancements, ServerPlayer player) throws ReflectiveOperationException {
        Method method = findCompatibleMethod(playerAdvancements.getClass(), FLUSH_NAMES, player.getClass());
        if (method != null) method.invoke(playerAdvancements, player);
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

    private static Method findCompatibleMethod(Class<?> owner, String[] names, Class<?>... arguments) {
        for (String name : names) {
            for (Method method : owner.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean compatible = true;
                for (int i = 0; i < parameters.length; i++) {
                    compatible &= parameters[i].isAssignableFrom(arguments[i]);
                }
                if (compatible) return method;
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> owner, String[] names) {
        for (String name : names) {
            try {
                return owner.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
