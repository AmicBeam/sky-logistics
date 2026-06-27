package com.skylogistics.compat.ae2;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
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

    public static GridNodeHandle createGridNodeHandle(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyGridNodeHandle.INSTANCE;
        }
        try {
            return AppliedEnergisticsApiBridge.createGridNodeHandle(host);
        } catch (LinkageError error) {
            warn(error);
            return EmptyGridNodeHandle.INSTANCE;
        }
    }

    private static void warn(LinkageError error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn("AE2 compat is disabled because the loaded AE2 API is not compatible.", error);
        }
    }

    public interface GridNodeOwner {
        GridNodeHandle ae2GridNodeHandle();
    }

    public interface GridNodeHandle {
        void load(CompoundTag tag);

        void save(CompoundTag tag);

        void onLoad(BlockEntity host);

        void onRemove();

        <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side);

        void invalidateCaps();
    }

    private enum EmptyGridNodeHandle implements GridNodeHandle {
        INSTANCE;

        @Override
        public void load(CompoundTag tag) {
        }

        @Override
        public void save(CompoundTag tag) {
        }

        @Override
        public void onLoad(BlockEntity host) {
        }

        @Override
        public void onRemove() {
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return LazyOptional.empty();
        }

        @Override
        public void invalidateCaps() {
        }
    }
}
