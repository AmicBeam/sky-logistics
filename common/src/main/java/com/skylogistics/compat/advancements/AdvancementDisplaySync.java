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
            AdvancementTransferLimiter.refresh(player);
            boolean enabled = SkyLogisticsConfig.enableAdvancementTransferRates();
            var configured = SkyLogisticsConfig.advancementDisplayEntries();
            Object manager = player.level().getServer().getAdvancements();
            Object playerAdvancements = player.getAdvancements();
            Method lookup = AdvancementAccess.findLookup(manager);
            if (lookup == null) return;
            setAwarded(manager, playerAdvancements, lookup, ROOT, enabled && !configured.isEmpty());
            for (int index = 0; index < configured.size(); index++) {
                AdvancementDisplayEntry milestone = configured.get(index);
                boolean completed = enabled && isCompleted(manager, playerAdvancements, lookup,
                        milestone.advancement());
                setAwarded(manager, playerAdvancements, lookup,
                        "skylogistics:transfer_rates/entry_" + index, completed);
            }
            AdvancementAccess.flush(playerAdvancements, player);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        } finally {
            SYNCING.remove(player.getUUID());
        }
    }

    private static boolean isCompleted(Object manager, Object playerAdvancements, Method lookup, String id)
            throws ReflectiveOperationException {
        Object advancement = AdvancementAccess.findAdvancement(manager, lookup, id);
        return advancement != null && AdvancementAccess.isDone(AdvancementAccess.progress(playerAdvancements,
                advancement));
    }

    private static void setAwarded(Object manager, Object playerAdvancements, Method lookup, String id,
            boolean awarded) throws ReflectiveOperationException {
        Object advancement = AdvancementAccess.findAdvancement(manager, lookup, id);
        if (advancement == null) return;
        AdvancementAccess.setAwarded(playerAdvancements, advancement, awarded);
    }
}
