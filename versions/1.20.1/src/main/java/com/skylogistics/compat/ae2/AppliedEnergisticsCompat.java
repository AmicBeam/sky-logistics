package com.skylogistics.compat.ae2;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public final class AppliedEnergisticsCompat {
    private static final String AE2 = "ae2";
    private static boolean warned;

    private AppliedEnergisticsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(AE2);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyExternalHandlers.Items.INSTANCE;
        }
        try {
            return AppliedEnergisticsApiBridge.createItemHandler(host);
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
            return AppliedEnergisticsApiBridge.createFluidHandler(host);
        } catch (LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    private static void warn(LinkageError error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn("AE2 compat is disabled because the loaded AE2 API is not compatible.", error);
        }
    }
}
