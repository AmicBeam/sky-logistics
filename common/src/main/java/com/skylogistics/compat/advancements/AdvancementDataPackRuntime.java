package com.skylogistics.compat.advancements;

import com.skylogistics.config.SkyLogisticsConfig;
import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Rebuilds and activates the world datapack after SERVER config has loaded. */
public final class AdvancementDataPackRuntime {
    private AdvancementDataPackRuntime() {
    }

    public static void rebuild(MinecraftServer server, int packFormat, boolean legacyIconFormat) {
        if (!SkyLogisticsConfig.enableAdvancementTransferRates()) return;
        try {
            var entries = SkyLogisticsConfig.advancementDisplayEntries();
            boolean changed = AdvancementDataPackGenerator.generate(
                    server.getWorldPath(LevelResource.DATAPACK_DIR), packFormat, legacyIconFormat, entries);
            var repository = server.getPackRepository();
            if (!changed && repository.getSelectedIds().contains(AdvancementDataPackGenerator.PACK_ID)) return;
            repository.reload();
            var selected = new ArrayList<>(repository.getSelectedIds());
            if (!selected.contains(AdvancementDataPackGenerator.PACK_ID)) {
                selected.add(AdvancementDataPackGenerator.PACK_ID);
            }
            server.reloadResources(selected).thenRun(() -> server.execute(() -> {
                for (var player : server.getPlayerList().getPlayers()) AdvancementDisplaySync.sync(player);
            }));
        } catch (Exception exception) {
            System.err.println("[Sky Logistics] Failed to rebuild configured advancement datapack: " + exception);
        }
    }
}
