package com.skylogistics.compat;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class ManualCompat {
    private ManualCompat() {
    }

    public static Optional<ItemStack> createManualStack() {
        Optional<ItemStack> guideMeManual = GuideMeCompat.createManualStack();
        return guideMeManual.isPresent() ? guideMeManual : PatchouliCompat.createManualStack();
    }
}
