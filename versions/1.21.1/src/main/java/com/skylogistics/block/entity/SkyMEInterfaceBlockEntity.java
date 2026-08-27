package com.skylogistics.block.entity;

import com.skylogistics.compat.ae2.AppliedEnergisticsCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.industrialforegoingsouls.SoulHandlerBridge;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

public class SkyMEInterfaceBlockEntity extends ExternalNetworkInterfaceBlockEntity
        implements AppliedEnergisticsCompat.GridNodeOwner {
    private final AppliedEnergisticsCompat.GridNodeHandle ae2GridNode =
            AppliedEnergisticsCompat.createGridNodeHandle(this);
    private final ChemicalHandlerBridge chemicalHandler = AppliedEnergisticsCompat.createChemicalHandler(this);
    private final SoulHandlerBridge soulHandler = AppliedEnergisticsCompat.createSoulHandler(this);
    private final ManaHandlerBridge manaHandler = AppliedEnergisticsCompat.createManaHandler(this);
    private final SourceHandlerBridge sourceHandler = AppliedEnergisticsCompat.createSourceHandler(this);

    public SkyMEInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SKY_ME_INTERFACE.get(), pos, state);
    }

    @Override
    public ChemicalHandlerBridge getEndpointChemicalHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION && AppliedEnergisticsCompat.supportsChemicalEndpoint()
                ? chemicalHandler
                : null;
    }

    @Override
    public SoulHandlerBridge getEndpointSoulHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION && AppliedEnergisticsCompat.supportsSoulEndpoint()
                ? soulHandler
                : null;
    }

    public SoulHandlerBridge exposedSoulHandler() {
        return soulHandler;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ae2GridNode.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ae2GridNode.load(tag, registries);
    }

    @Override
    protected Component externalNetworkName() {
        return Component.translatable("screen.skylogistics.ae2_network");
    }
}
