package com.skylogistics.compat.mekanism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class MekanismCompat {
    private static final boolean SUPPORTED_IN_26_1_2 = false;
    private static final String MEKANISM = "mekanism";

    private MekanismCompat() {
    }

    public static boolean isLoaded() {
        return SUPPORTED_IN_26_1_2 && ModList.get().isLoaded(MEKANISM);
    }

    public static ChemicalHandlerBridge chemicalHandler(Level level, BlockPos pos, Direction side) {
        return null;
    }

    public static ChemicalHandlerBridge wrapChemicalHandler(Object handler) {
        return null;
    }
}
