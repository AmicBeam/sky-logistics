package com.skylogistics.compat.mekanism;

import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.world.item.ItemStack;
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

    public static String containerChemical(ItemStack stack) {
        if (stack.isEmpty() || !isLoaded() || !SkyLogisticsConfig.allowFilterContainerChemicals()) {
            return "";
        }
        ChemicalHandlerBridge handler = MekanismChemicalCompat.chemicalHandler(stack.copy());
        if (handler != null) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                ChemicalStackView chemical = handler.getChemicalInTank(tank);
                if (!chemical.isEmpty()) {
                    return chemical.chemicalKey();
                }
            }
        }
        return "";
    }

    public static ChemicalHandlerBridge chemicalHandler(Level level, BlockPos pos, Direction side) {
        if (!isLoaded()) {
            return null;
        }
        return MekanismChemicalCompat.chemicalHandler(level, pos, side);
    }

    public static ChemicalHandlerBridge wrapChemicalHandlers(Object... handlers) {
        if (!isLoaded()) {
            return null;
        }
        return MekanismChemicalCompat.wrapChemicalHandlers(handlers);
    }
}
