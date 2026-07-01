package com.skylogistics.event;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.ManualCompat;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

public final class ManualGiftHandler {
    private static final Identifier EULOGIA_MANUAL_ADVANCEMENT =
            Identifier.fromNamespaceAndPath(SkyLogistics.MOD_ID, "eulogia_manual");

    private ManualGiftHandler() {
    }

    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EULOGIA_MANUAL_ADVANCEMENT.equals(event.getAdvancement().id())) {
            return;
        }

        ManualCompat.createManualStack().ifPresent(manual -> {
            ItemStack toGive = manual.copy();
            if (!player.addItem(toGive)) {
                player.drop(toGive, false);
            }
        });
    }
}
