package com.skylogistics.event;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.PatchouliCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AdvancementEvent;

public final class ManualGiftHandler {
    private static final ResourceLocation EULOGIA_MANUAL_ADVANCEMENT =
            new ResourceLocation(SkyLogistics.MOD_ID, "eulogia_manual");

    private ManualGiftHandler() {
    }

    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EULOGIA_MANUAL_ADVANCEMENT.equals(event.getAdvancement().getId())) {
            return;
        }

        PatchouliCompat.createManualStack().ifPresent(manual -> {
            ItemStack toGive = manual.copy();
            if (!player.addItem(toGive)) {
                player.drop(toGive, false);
            }
        });
    }
}
