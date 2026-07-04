package com.skylogistics.compat;

import com.skylogistics.registry.ModItems;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ManualCompat {
    private ManualCompat() {
    }

    public static boolean isLoaded() {
        return GuideMeCompat.isLoaded() || PatchouliCompat.isLoaded();
    }

    public static Optional<ItemStack> createManualStack() {
        return isLoaded() ? Optional.of(new ItemStack(ModItems.SKY_LOGISTICS_MANUAL.get())) : Optional.empty();
    }

    public static boolean openManual(Player player) {
        return GuideMeCompat.openManual(player) || PatchouliCompat.openManual(player);
    }
}
