package com.skylogistics.compat.arsnouveau;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

public final class ArsNouveauCompat {
    private static final String ARS_NOUVEAU = "ars_nouveau";

    private ArsNouveauCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(ARS_NOUVEAU);
    }

    public static SourceHandlerBridge sourceHandler(Level level, BlockPos pos, Direction side) {
        if (!isLoaded() || level == null || pos == null) {
            return null;
        }
        BlockEntity target = level.getBlockEntity(pos);
        if (target == null) {
            return null;
        }
        SourceHandlerBridge direct = ArsNouveauApiBridge.create(target);
        if (direct != null) {
            return direct;
        }
        // Addons may expose Source-compatible proxies without implementing the
        // Ars Nouveau interface, so retain reflection as a fallback.
        return ReflectiveSourceHandlerBridge.create(target);
    }

    public static SourceHandlerBridge wrapSourceHandler(Object handler) {
        if (!isLoaded()) {
            return null;
        }
        return ReflectiveSourceHandlerBridge.create(handler);
    }

}
