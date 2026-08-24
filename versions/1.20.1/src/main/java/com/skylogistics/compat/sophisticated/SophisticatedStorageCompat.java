package com.skylogistics.compat.sophisticated;

import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

public final class SophisticatedStorageCompat {
    private static final String MOD_ID = "sophisticatedstorage";
    private static final String PACKAGE_PREFIX = "net.p3pp3rf1y.sophisticatedstorage.";

    private SophisticatedStorageCompat() {
    }

    public static boolean supports(BlockEntity blockEntity) {
        return blockEntity != null
                && SkyLogisticsConfig.allowSophisticatedStorageStackUpgradeTransfer()
                && ModList.get().isLoaded(MOD_ID)
                && blockEntity.getClass().getName().startsWith(PACKAGE_PREFIX);
    }
}
