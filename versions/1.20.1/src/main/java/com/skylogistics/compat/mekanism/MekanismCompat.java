package com.skylogistics.compat.mekanism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

public final class MekanismCompat {
    private static final String MEKANISM = "mekanism";

    private MekanismCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MEKANISM);
    }

    public static ChemicalHandlerBridge chemicalHandler(Level level, BlockPos pos, Direction side) {
        if (!isLoaded()) {
            return null;
        }
        return MekanismChemicalCompat.chemicalHandler(level, pos, side);
    }
}
