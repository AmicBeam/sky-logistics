package com.skylogistics.network;

import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.industrialforegoingsouls.IndustrialForegoingSoulsCompat;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

record KleisTargetCapabilities(boolean items, boolean fluids, boolean energy) {
    static KleisTargetCapabilities detect(Level level, BlockPos pos, Direction face) {
        if (level == null || !level.isLoaded(pos)) return new KleisTargetCapabilities(false, false, false);
        if (level.getBlockEntity(pos) instanceof SkyDistributorBlockEntity distributor) {
            return new KleisTargetCapabilities(
                    distributor.hasItemTargets(face),
                    distributor.hasFluidTargets(face)
                            || SkyLogisticsConfig.allowFluidChemicalTransfer() && distributor.hasChemicalTargets(face)
                            || SkyLogisticsConfig.allowFluidSoulTransfer() && distributor.hasSoulTargets(face),
                    distributor.hasEnergyTargets(face)
                            || SkyLogisticsConfig.allowEnergyManaTransfer() && distributor.hasManaTargets(face)
                            || SkyLogisticsConfig.allowEnergySourceTransfer() && distributor.hasSourceTargets(face));
        }
        var itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        boolean items = itemHandler != null && itemHandler.getSlots() > 0;
        var fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        boolean fluids = fluidHandler != null && fluidHandler.getTanks() > 0;
        if (!fluids && SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()) {
            var handler = MekanismCompat.chemicalHandler(level, pos, face);
            fluids = handler != null && handler.getTanks() > 0;
        }
        if (!fluids && SkyLogisticsConfig.allowFluidSoulTransfer()
                && IndustrialForegoingSoulsCompat.isLoaded()) {
            var handler = IndustrialForegoingSoulsCompat.soulHandler(level, pos, face);
            fluids = handler != null && handler.getSoulTanks() > 0;
        }
        var energyStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
        boolean energy = energyStorage != null && (energyStorage.getMaxEnergyStored() > 0
                || energyStorage.canExtract() || energyStorage.canReceive());
        if (!energy && SkyLogisticsConfig.allowEnergyManaTransfer() && BotaniaCompat.isLoaded()) {
            var handler = BotaniaCompat.manaHandler(level, pos, face);
            energy = handler != null && (handler.canExtract() || handler.canReceive() || handler.getMaxMana() > 0);
        }
        if (!energy && SkyLogisticsConfig.allowEnergySourceTransfer() && ArsNouveauCompat.isLoaded()) {
            var handler = ArsNouveauCompat.sourceHandler(level, pos, face);
            energy = handler != null && (handler.canExtract() || handler.canReceive() || handler.getMaxSource() > 0);
        }
        return new KleisTargetCapabilities(items, fluids, energy);
    }
}
