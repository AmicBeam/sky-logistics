package com.skylogistics.compat.mekanism;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

final class MekanismChemicalCompat {
    private MekanismChemicalCompat() {
    }

    static ChemicalHandlerBridge chemicalHandler(Level level, BlockPos pos, Direction side) {
        return null;
    }

    static ChemicalHandlerBridge wrapChemicalHandler(Object handler) {
        return null;
    }
}
