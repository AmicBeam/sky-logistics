package com.skylogistics.compat.arsnouveau;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;

public final class ArsNouveauCompat {
    private static final boolean SUPPORTED_IN_26_1_2 = false;
    private static final String ARS_NOUVEAU = "ars_nouveau";
    @SuppressWarnings("rawtypes")
    private static BlockCapability sourceCapability;
    private static boolean sourceCapabilityResolved;
    private static Class<?> sourceTileType;
    private static boolean sourceTileTypeResolved;

    private ArsNouveauCompat() {
    }

    public static boolean isLoaded() {
        return SUPPORTED_IN_26_1_2 && ModList.get().isLoaded(ARS_NOUVEAU);
    }

    public static SourceHandlerBridge sourceHandler(Level level, BlockPos pos, Direction side) {
        if (!isLoaded() || level == null || pos == null) {
            return null;
        }
        Object sourceCap = getCapability(level, sourceCapability(), pos, side);
        if (sourceCap != null) {
            SourceHandlerBridge bridge = ReflectiveSourceHandlerBridge.create(sourceCap);
            if (bridge != null) {
                return bridge;
            }
        }
        BlockEntity target = level.getBlockEntity(pos);
        if (target == null || !isSourceTile(target)) {
            return null;
        }
        return ReflectiveSourceHandlerBridge.create(target);
    }

    public static SourceHandlerBridge wrapSourceHandler(Object handler) {
        if (!isLoaded()) {
            return null;
        }
        return ReflectiveSourceHandlerBridge.create(handler);
    }

    @SuppressWarnings("rawtypes")
    private static BlockCapability sourceCapability() {
        resolveSourceCapability();
        return sourceCapability;
    }

    private static void resolveSourceCapability() {
        if (sourceCapabilityResolved) {
            return;
        }
        sourceCapabilityResolved = true;
        try {
            Object value = Class.forName("com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry")
                    .getField("SOURCE_CAPABILITY")
                    .get(null);
            sourceCapability = value instanceof BlockCapability capability ? capability : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            sourceCapability = null;
        }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getCapability(Level level, BlockCapability capability, BlockPos pos, Direction side) {
        return capability == null ? null : level.getCapability(capability, pos, side);
    }
}
