package com.skylogistics.block.entity;

import com.skylogistics.compat.refinedstorage.RefinedStorageCompat;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class SkyRSInterfaceBlockEntity extends ExternalNetworkInterfaceBlockEntity {
    private final IItemHandler itemHandler = RefinedStorageCompat.createItemHandler(this);
    private final IFluidHandler fluidHandler = RefinedStorageCompat.createFluidHandler(this);

    public SkyRSInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_RS_INTERFACE.get(), pos, state);
    }

    @Override
    protected IItemHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    protected IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    @Override
    protected Component externalNetworkName() {
        return Component.translatable("screen.skylogistics.rs_network");
    }
}
