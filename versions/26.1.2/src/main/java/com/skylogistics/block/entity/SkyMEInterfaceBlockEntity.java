package com.skylogistics.block.entity;

import com.skylogistics.compat.ae2.AppliedEnergisticsCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class SkyMEInterfaceBlockEntity extends ExternalNetworkInterfaceBlockEntity
        implements AppliedEnergisticsCompat.GridNodeOwner {
    private static final String DATA_TAG = "SkyLogisticsSkyMEInterface";
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

    public Object ae2GridNodeHost(Direction side) {
        return ae2GridNode.hostCapability(side);
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        ae2GridNode.save(tag, com.skylogistics.util.StackData.builtinRegistries());
        output.store(DATA_TAG, CompoundTag.CODEC, tag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CompoundTag tag = input.read(DATA_TAG, CompoundTag.CODEC).orElse(new CompoundTag());
        ae2GridNode.load(tag, input.lookup());
    }

    @Override
    protected Component externalNetworkName() {
        return Component.translatable("screen.skylogistics.ae2_network");
    }
}
