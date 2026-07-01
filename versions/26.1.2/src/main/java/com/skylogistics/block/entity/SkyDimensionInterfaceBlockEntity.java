package com.skylogistics.block.entity;

import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class SkyDimensionInterfaceBlockEntity extends ExternalNetworkInterfaceBlockEntity
        implements BeyondDimensionsCompat.NetworkBoundHost {
    private static final String DATA_TAG = "SkyLogisticsSkyDimensionInterface";
    private static final String NET_ID_TAG = "netId";

    private final IItemHandler itemHandler = BeyondDimensionsCompat.createItemHandler(this);
    private final IFluidHandler fluidHandler = BeyondDimensionsCompat.createFluidHandler(this);
    private final ChemicalHandlerBridge chemicalHandler = BeyondDimensionsCompat.createChemicalHandler(this);
    private final IEnergyStorage energyHandler = BeyondDimensionsCompat.createEnergyHandler(this);
    private final SourceHandlerBridge sourceHandler = BeyondDimensionsCompat.createSourceHandler(this);
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
    public ChemicalHandlerBridge getEndpointChemicalHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION ? chemicalHandler : null;
    }

    @Override
    protected boolean supportsEnergyEndpoint() {
        return true;
    }

    @Override
    public SourceHandlerBridge getEndpointSourceHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION ? sourceHandler : null;
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        tag.putInt(NET_ID_TAG, dimensionNetworkId);
        output.store(DATA_TAG, CompoundTag.CODEC, tag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = input.read(DATA_TAG, CompoundTag.CODEC).orElse(new CompoundTag());
        dimensionNetworkId = tag.contains(NET_ID_TAG) ? tag.getIntOr(NET_ID_TAG, 0) : -1;
    }

    @Override
    protected Component externalNetworkName() {
        return Component.translatable("screen.skylogistics.bd_network");
    }
}
