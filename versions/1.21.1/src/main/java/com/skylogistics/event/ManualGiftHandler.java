package com.skylogistics.event;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.PatchouliCompat;
import com.skylogistics.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

public final class ManualGiftHandler {
    private static final ResourceLocation EULOGIA_MANUAL_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(SkyLogistics.MOD_ID, "eulogia_manual");

    private ManualGiftHandler() {
    }

    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !EULOGIA_MANUAL_ADVANCEMENT.equals(event.getAdvancement().id())) {
            return;
        }

        if (!PatchouliCompat.isLoaded()) {
            return;
        }
        ItemStack toGive = new ItemStack(ModItems.SKY_LOGISTICS_MANUAL.get());
        if (!player.addItem(toGive)) {
            player.drop(toGive, false);
        }
    }
}
