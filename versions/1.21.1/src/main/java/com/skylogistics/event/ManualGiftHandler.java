package com.skylogistics.event;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.ManualCompat;
import com.skylogistics.compat.advancements.AdvancementDisplaySync;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ManualGiftHandler {
    private static final ResourceLocation EULOGIA_MANUAL_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(SkyLogistics.MOD_ID, "eulogia_manual");

    private ManualGiftHandler() {
    }

    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AdvancementDisplaySync.sync(player);
        if (!EULOGIA_MANUAL_ADVANCEMENT.equals(event.getAdvancement().id())) {
            return;
        }

        ManualCompat.createManualStack().ifPresent(manual -> {
            ItemStack toGive = manual.copy();
            if (!player.addItem(toGive)) player.drop(toGive, false);
        });
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AdvancementDisplaySync.sync(player);
    }
}
