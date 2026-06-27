package com.skylogistics.compat.beyonddimensions;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

public final class BeyondDimensionsCompat {
    private static final String BEYOND_DIMENSIONS = "beyonddimensions";
    private static boolean warned;

    private BeyondDimensionsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(BEYOND_DIMENSIONS);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyExternalHandlers.Items.INSTANCE;
        }
        try {
            return BeyondDimensionsApiBridge.createItemHandler(host);
        } catch (LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Items.INSTANCE;
        }
    }

    public static IFluidHandler createFluidHandler(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
        try {
            return BeyondDimensionsApiBridge.createFluidHandler(host);
        } catch (LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    public static void bindOnPlaced(Level level, BlockPos pos, LivingEntity placer) {
        if (!isLoaded() || level.isClientSide) {
            return;
        }
        try {
            BeyondDimensionsApiBridge.bindOnPlaced(level, pos, placer);
        } catch (LinkageError error) {
            warn(error);
        }
    }

    public static boolean handleBindingUse(Level level, BlockPos pos, Player player) {
        if (!isLoaded() || !player.getMainHandItem().isEmpty() || !player.isShiftKeyDown()) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        try {
            BeyondDimensionsApiBridge.handleBindingUse(level, pos, player);
        } catch (LinkageError error) {
            warn(error);
        }
        return true;
    }

    private static void warn(LinkageError error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn(
                    "Beyond Dimensions compat is disabled because the loaded Beyond Dimensions API is not compatible.",
                    error);
        }
    }

    public interface NetworkBoundHost {
        int getDimensionNetworkId();

        void setDimensionNetworkId(int netId);

        void clearDimensionNetworkId();
    }
}
