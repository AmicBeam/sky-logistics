package com.skylogistics.block.entity;

import com.skylogistics.compat.ae2.AppliedEnergisticsCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public class SkyMEInterfaceBlockEntity extends ExternalNetworkInterfaceBlockEntity
        implements AppliedEnergisticsCompat.GridNodeOwner {
    private final AppliedEnergisticsCompat.GridNodeHandle ae2GridNode =
            AppliedEnergisticsCompat.createGridNodeHandle(this);
    private final IItemHandler itemHandler = AppliedEnergisticsCompat.createItemHandler(this);
    private final IFluidHandler fluidHandler = AppliedEnergisticsCompat.createFluidHandler(this);
    private final IEnergyStorage energyHandler = AppliedEnergisticsCompat.createEnergyHandler(this);
    private final ChemicalHandlerBridge chemicalHandler = AppliedEnergisticsCompat.createChemicalHandler(this);
    private final ManaHandlerBridge manaHandler = AppliedEnergisticsCompat.createManaHandler(this);
    private final SourceHandlerBridge sourceHandler = AppliedEnergisticsCompat.createSourceHandler(this);

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

    @Override
    protected IEnergyStorage getEnergyHandler() {
        return energyHandler;
    }

    @Override
    protected boolean supportsEnergyEndpoint() {
        return AppliedEnergisticsCompat.supportsEnergyEndpoint();
    }

    @Override
    public ChemicalHandlerBridge getEndpointChemicalHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION && AppliedEnergisticsCompat.supportsChemicalEndpoint()
                ? chemicalHandler
                : null;
    }

    @Override
    public ManaHandlerBridge getEndpointManaHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION && AppliedEnergisticsCompat.supportsManaEndpoint()
                ? manaHandler
                : null;
    }

    @Override
    public SourceHandlerBridge getEndpointSourceHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION && AppliedEnergisticsCompat.supportsSourceEndpoint()
                ? sourceHandler
                : null;
    }

    @Override
    public AppliedEnergisticsCompat.GridNodeHandle ae2GridNodeHandle() {
        return ae2GridNode;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ae2GridNode.onLoad(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ae2GridNode.onRemove();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ae2GridNode.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ae2GridNode.load(tag);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        LazyOptional<T> ae2Capability = ae2GridNode.getCapability(capability, side);
        if (ae2Capability.isPresent()) {
            return ae2Capability;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        ae2GridNode.invalidateCaps();
    }

    @Override
    protected Component externalNetworkName() {
        return Component.translatable("screen.skylogistics.ae2_network");
    }
}
