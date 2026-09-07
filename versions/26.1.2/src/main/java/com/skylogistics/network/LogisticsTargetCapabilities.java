package com.skylogistics.network;

import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.util.TransferCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

public record LogisticsTargetCapabilities(int itemSlots, boolean fluid, boolean chemical,
        boolean nativeEnergy, boolean mana, boolean source) {
    public static LogisticsTargetCapabilities detect(Level level, BlockPos pos, Direction face) {
        if (level == null || !level.isLoaded(pos)) return empty();
        if (level.getBlockEntity(pos) instanceof SkyDistributorBlockEntity distributor) {
            return new LogisticsTargetCapabilities(distributor.hasItemTargets(face) ? 1 : 0,
                    distributor.hasFluidTargets(face), distributor.hasChemicalTargets(face),
                    distributor.hasEnergyTargets(face), distributor.hasManaTargets(face),
                    distributor.hasSourceTargets(face));
        }
        return detectDirect(level, pos, face);
    }

    public static LogisticsTargetCapabilities detectDirect(Level level, BlockPos pos, Direction face) {
        if (level == null || !level.isLoaded(pos)) return empty();
        var itemHandler = TransferCompat.itemHandler(level.getCapability(Capabilities.Item.BLOCK, pos, face));
        int itemSlots = itemHandler == null ? 0 : Math.max(0, itemHandler.getSlots());
        var fluidHandler = TransferCompat.fluidHandler(level.getCapability(Capabilities.Fluid.BLOCK, pos, face));
        boolean fluid = fluidHandler != null && fluidHandler.getTanks() > 0;
        var chemicalHandler = SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()
                ? MekanismCompat.chemicalHandler(level, pos, face) : null;
        boolean chemical = chemicalHandler != null && chemicalHandler.getTanks() > 0;
        var energyStorage = TransferCompat.energyStorage(level.getCapability(Capabilities.Energy.BLOCK, pos, face));
        boolean nativeEnergy = energyStorage != null && (energyStorage.getMaxEnergyStored() > 0
                || energyStorage.canExtract() || energyStorage.canReceive());
        var manaHandler = SkyLogisticsConfig.allowEnergyManaTransfer() && BotaniaCompat.isLoaded()
                ? BotaniaCompat.manaHandler(level, pos, face) : null;
        boolean mana = manaHandler != null
                && (manaHandler.canExtract() || manaHandler.canReceive() || manaHandler.getMaxMana() > 0);
        var sourceHandler = SkyLogisticsConfig.allowEnergySourceTransfer() && ArsNouveauCompat.isLoaded()
                ? ArsNouveauCompat.sourceHandler(level, pos, face) : null;
        boolean source = sourceHandler != null
                && (sourceHandler.canExtract() || sourceHandler.canReceive() || sourceHandler.getMaxSource() > 0);
        return new LogisticsTargetCapabilities(itemSlots, fluid, chemical, nativeEnergy, mana, source);
    }

    public boolean items() { return itemSlots > 0; }
    public boolean fluids() { return fluid || chemical; }
    public boolean energy() { return nativeEnergy || mana || source; }

    private static LogisticsTargetCapabilities empty() {
        return new LogisticsTargetCapabilities(0, false, false, false, false, false);
    }
}
