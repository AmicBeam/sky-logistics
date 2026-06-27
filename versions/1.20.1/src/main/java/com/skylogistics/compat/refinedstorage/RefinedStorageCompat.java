package com.skylogistics.compat.refinedstorage;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

public final class RefinedStorageCompat {
    private static final String REFINED_STORAGE = "refinedstorage";
    private static boolean warned;

    private RefinedStorageCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(REFINED_STORAGE);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyExternalHandlers.Items.INSTANCE;
        }
        try {
            return RefinedStorageApiBridge.createItemHandler(host);
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
            return RefinedStorageApiBridge.createFluidHandler(host);
        } catch (LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    private static void warn(LinkageError error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn("Refined Storage compat is disabled because the loaded RS API is not compatible.",
                    error);
        }
    }
}
