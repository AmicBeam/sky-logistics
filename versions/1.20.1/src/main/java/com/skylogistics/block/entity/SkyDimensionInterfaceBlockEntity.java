package com.skylogistics.block.entity;

import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public class SkyDimensionInterfaceBlockEntity extends ExternalNetworkInterfaceBlockEntity
        implements BeyondDimensionsCompat.NetworkBoundHost {
    private static final String NET_ID_TAG = "netId";

    private final IItemHandler itemHandler = BeyondDimensionsCompat.createItemHandler(this);
    private final IFluidHandler fluidHandler = BeyondDimensionsCompat.createFluidHandler(this);
    private final IEnergyStorage energyHandler = BeyondDimensionsCompat.createEnergyHandler(this);
    private final ManaHandlerBridge manaHandler = BeyondDimensionsCompat.createManaHandler(this);
    private int dimensionNetworkId = -1;

    public SkyDimensionInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_DIMENSION_INTERFACE.get(), pos, state);
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
    protected IEnergyStorage getEnergyHandler() {
        return energyHandler;
    }

    @Override
    protected boolean supportsEnergyEndpoint() {
        return true;
    }

    @Override
    public ManaHandlerBridge getEndpointManaHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION ? manaHandler : null;
    }

    @Override
    public int getDimensionNetworkId() {
        return dimensionNetworkId;
    }

    @Override
    public void setDimensionNetworkId(int netId) {
        if (dimensionNetworkId == netId) {
            return;
        }
        dimensionNetworkId = netId;
        markRuntimeChanged();
    }

    @Override
    public void clearDimensionNetworkId() {
        setDimensionNetworkId(-1);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(NET_ID_TAG, dimensionNetworkId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        dimensionNetworkId = tag.contains(NET_ID_TAG) ? tag.getInt(NET_ID_TAG) : -1;
    }

    @Override
    protected Component externalNetworkName() {
        return Component.translatable("screen.skylogistics.bd_network");
    }
}
