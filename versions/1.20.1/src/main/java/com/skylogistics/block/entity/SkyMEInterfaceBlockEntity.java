package com.skylogistics.block.entity;

import com.skylogistics.compat.ae2.AppliedEnergisticsCompat;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public class SkyMEInterfaceBlockEntity extends ExternalNetworkInterfaceBlockEntity {
    private final IItemHandler itemHandler = AppliedEnergisticsCompat.createItemHandler(this);
    private final IFluidHandler fluidHandler = AppliedEnergisticsCompat.createFluidHandler(this);

    public SkyMEInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_ME_INTERFACE.get(), pos, state);
    }

    @Override
    protected IItemHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    protected IFluidHandler getFluidHandler() {
        return fluidHandler;
    }
}
