package com.skylogistics.compat.arsnouveau;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

public final class ArsNouveauCompat {
    private static final String ARS_NOUVEAU = "ars_nouveau";
    private static Class<?> sourceTileType;
    private static boolean sourceTileTypeResolved;

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
        if (target == null || !isSourceTile(target)) {
            return null;
        }
        return ReflectiveSourceHandlerBridge.create(target);
    }

    public static SourceHandlerBridge wrapSourceHandler(Object handler) {
        if (!isLoaded() || !isSourceTile(handler)) {
            return null;
        }
        return ReflectiveSourceHandlerBridge.create(handler);
    }

    private static boolean isSourceTile(Object target) {
        Class<?> type = sourceTileType();
        return type != null && type.isInstance(target);
    }

    private static Class<?> sourceTileType() {
        if (sourceTileTypeResolved) {
            return sourceTileType;
        }
        sourceTileTypeResolved = true;
        try {
            sourceTileType = Class.forName("com.hollingsworth.arsnouveau.api.source.ISourceTile");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            sourceTileType = null;
        }
        return sourceTileType;
    }
}
