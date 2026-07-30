package com.skylogistics.block.entity;

import com.skylogistics.compat.refinedstorage.RefinedStorageCompat;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.FluidHandler;
import com.skylogistics.util.ItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public class SkyRSInterfaceBlockEntity extends ExternalNetworkInterfaceBlockEntity {
    private final ItemHandler itemHandler = RefinedStorageCompat.createItemHandler(this);
    private final FluidHandler fluidHandler = RefinedStorageCompat.createFluidHandler(this);

    public SkyRSInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_RS_INTERFACE.get(), pos, state);
    }

    @Override
    protected ItemHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    protected FluidHandler getFluidHandler() {
        return fluidHandler;
    }

    @Override
    protected Component externalNetworkName() {
        return Component.translatable("screen.skylogistics.rs_network");
    }
}
