package com.skylogistics.compat.ae2;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public final class AppliedEnergisticsCompat {
    private static final String AE2 = "ae2";
    private static final String APPFLUX = "appflux";
    private static final String APPMEK = "appmek";
    private static final String APPBOT = "appbot";
    private static final String ARSENG = "arseng";
    private static boolean warned;

    private AppliedEnergisticsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(AE2);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        if (!isLoaded() || !SkyLogisticsConfig.allowAe2ItemTransfer()) {
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
        if (!isLoaded() || !SkyLogisticsConfig.allowAe2FluidTransfer()) {
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
        try {
            return AppliedEnergisticsApiBridge.createFluidHandler(host);
        } catch (LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    public static IEnergyStorage createEnergyHandler(BlockEntity host) {
        if (!canUseAppFlux()) {
            return EmptyExternalHandlers.Energy.INSTANCE;
        }
        try {
            return AppliedEnergisticsApiBridge.createEnergyHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Energy.INSTANCE;
        }
    }

    public static ChemicalHandlerBridge createChemicalHandler(BlockEntity host) {
        if (!canUseAppliedMekanistics()) {
            return null;
        }
        try {
            return AppliedEnergisticsApiBridge.createChemicalHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static ManaHandlerBridge createManaHandler(BlockEntity host) {
        if (!canUseAppliedBotanics()) {
            return null;
        }
        try {
            return AppliedEnergisticsApiBridge.createManaHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static SourceHandlerBridge createSourceHandler(BlockEntity host) {
        if (!canUseArsEnergistique()) {
            return null;
        }
        try {
            return AppliedEnergisticsApiBridge.createSourceHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static boolean supportsEnergyEndpoint() {
        return canUseAppFlux() || canUseAppliedBotanics() || canUseArsEnergistique();
    }

    public static boolean supportsChemicalEndpoint() {
        return canUseAppliedMekanistics();
    }

    public static boolean supportsManaEndpoint() {
        return canUseAppliedBotanics();
    }

    public static boolean supportsSourceEndpoint() {
        return canUseArsEnergistique();
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

    private static boolean canUseAppFlux() {
        return isLoaded() && SkyLogisticsConfig.allowAe2AppFluxEnergyTransfer()
                && ModList.get().isLoaded(APPFLUX);
    }

    private static boolean canUseAppliedMekanistics() {
        return isLoaded() && SkyLogisticsConfig.allowAe2AppliedMekanisticsChemicalTransfer()
                && SkyLogisticsConfig.allowFluidChemicalTransfer()
                && ModList.get().isLoaded(APPMEK);
    }

    private static boolean canUseAppliedBotanics() {
        return isLoaded() && SkyLogisticsConfig.allowAe2AppliedBotanicsManaTransfer()
                && SkyLogisticsConfig.allowEnergyManaTransfer()
                && ModList.get().isLoaded(APPBOT);
    }

    private static boolean canUseArsEnergistique() {
        return isLoaded() && SkyLogisticsConfig.allowAe2ArsEnergistiqueSourceTransfer()
                && SkyLogisticsConfig.allowEnergySourceTransfer()
                && ModList.get().isLoaded(ARSENG);
    }

    private static void warn(Throwable error) {
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
