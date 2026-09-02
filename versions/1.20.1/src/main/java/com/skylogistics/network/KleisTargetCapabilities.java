package com.skylogistics.network;

import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

record KleisTargetCapabilities(boolean items, boolean fluids, boolean energy) {
    static KleisTargetCapabilities detect(Level level, BlockPos pos, Direction face) {
        if (level == null || !level.isLoaded(pos)) return new KleisTargetCapabilities(false, false, false);
        BlockEntity target = level.getBlockEntity(pos);
        if (target instanceof SkyDistributorBlockEntity distributor) {
            return new KleisTargetCapabilities(
                    distributor.hasItemTargets(face),
                    distributor.hasFluidTargets(face)
                            || SkyLogisticsConfig.allowFluidChemicalTransfer() && distributor.hasChemicalTargets(face),
                    distributor.hasEnergyTargets(face)
                            || SkyLogisticsConfig.allowEnergyManaTransfer() && distributor.hasManaTargets(face)
                            || SkyLogisticsConfig.allowEnergySourceTransfer() && distributor.hasSourceTargets(face));
        }
        boolean items = target != null && target.getCapability(ForgeCapabilities.ITEM_HANDLER, face)
                .map(handler -> handler.getSlots() > 0).orElse(false);
        boolean fluids = target != null && target.getCapability(ForgeCapabilities.FLUID_HANDLER, face)
                .map(handler -> handler.getTanks() > 0).orElse(false);
        if (!fluids && SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()) {
            var handler = MekanismCompat.chemicalHandler(level, pos, face);
            fluids = handler != null && handler.getTanks() > 0;
        }
        boolean energy = target != null && target.getCapability(ForgeCapabilities.ENERGY, face)
                .map(storage -> storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive())
                .orElse(false);
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
