package com.skylogistics.block.entity;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.util.NodeFaceMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public abstract class ExternalNetworkInterfaceBlockEntity extends SkyNodeBlockEntity {
    protected static final Direction ENDPOINT_DIRECTION = Direction.NORTH;

    private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(this::getItemHandler);
    private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(this::getFluidHandler);

    protected ExternalNetworkInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        normalizeEndpoint(NodeFaceMode.NONE, false);
    }

    protected abstract IItemHandler getItemHandler();

    protected abstract IFluidHandler getFluidHandler();

    protected abstract Component externalNetworkName();

    @Override
    public boolean usesSingleEndpoint() {
        return true;
    }

    @Override
    public Direction getSingleEndpointDirection() {
        return ENDPOINT_DIRECTION;
    }

    @Override
    public boolean canConfigureFace(Direction direction) {
        return direction == ENDPOINT_DIRECTION;
    }

    @Override
    public BlockPos getTargetPos(Direction direction) {
        return worldPosition;
    }

    @Override
    public Direction getAccessSide(Direction direction) {
        return null;
    }

    @Override
    public boolean hasConfigurableTarget(Direction direction) {
        return direction == ENDPOINT_DIRECTION;
    }

    @Override
    public Component getTargetName(Direction direction) {
        return direction == ENDPOINT_DIRECTION ? externalNetworkName() : super.getTargetName(direction);
    }

    @Override
    public IItemHandler getEndpointItemHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION ? getItemHandler() : null;
    }

    @Override
    public IFluidHandler getEndpointFluidHandler(Direction direction, long gameTime) {
        return direction == ENDPOINT_DIRECTION ? getFluidHandler() : null;
    }

    @Override
    public void setFaceMode(Direction direction, NodeFaceMode faceMode) {
        if (direction == ENDPOINT_DIRECTION) {
            super.setFaceMode(direction, faceMode);
            normalizeEndpoint(faceMode, false);
        }
    }

    @Override
    public void setItemsEnabled(boolean itemsEnabled) {
        super.setItemsEnabled(ENDPOINT_DIRECTION, itemsEnabled);
        normalizeEndpoint(NodeFaceMode.NONE, false);
    }

    @Override
    public void setItemsEnabled(Direction direction, boolean itemsEnabled) {
        if (direction == ENDPOINT_DIRECTION) {
            super.setItemsEnabled(direction, itemsEnabled);
            normalizeEndpoint(NodeFaceMode.NONE, false);
        }
    }

    @Override
    public void setFluidsEnabled(boolean fluidsEnabled) {
        super.setFluidsEnabled(ENDPOINT_DIRECTION, fluidsEnabled);
        normalizeEndpoint(NodeFaceMode.NONE, false);
    }

    @Override
    public void setFluidsEnabled(Direction direction, boolean fluidsEnabled) {
        if (direction == ENDPOINT_DIRECTION) {
            super.setFluidsEnabled(direction, fluidsEnabled);
            normalizeEndpoint(NodeFaceMode.NONE, false);
        }
    }

    @Override
    public void setEnergyEnabled(boolean energyEnabled) {
        super.setEnergyEnabled(ENDPOINT_DIRECTION, false);
        normalizeEndpoint(NodeFaceMode.NONE, false);
    }

    @Override
    public void setEnergyEnabled(Direction direction, boolean energyEnabled) {
        if (direction == ENDPOINT_DIRECTION) {
            super.setEnergyEnabled(direction, false);
            normalizeEndpoint(NodeFaceMode.NONE, false);
        }
    }

    @Override
    public boolean isEnergyEnabled(Direction direction) {
        return false;
    }

    @Override
    public void configureTargetResourcesFromCapabilities() {
        configureTargetResourcesFromCapabilities(ENDPOINT_DIRECTION);
    }

    @Override
    public void configureTargetResourcesFromCapabilities(Direction direction) {
        if (direction != ENDPOINT_DIRECTION) {
            return;
        }
        setItemsEnabled(ENDPOINT_DIRECTION, true);
        setFluidsEnabled(ENDPOINT_DIRECTION, true);
        setEnergyEnabled(ENDPOINT_DIRECTION, false);
    }

    @Override
    public void applyPlacementToolConfig(ConfiguratorItem.ToolConfig config, boolean includeMode) {
        super.applyPlacementToolConfig(config, includeMode);
        applyEndpointFaceConfig(config.placement(), includeMode);
        normalizeEndpoint(includeMode ? config.placement().mode() : NodeFaceMode.NONE, true);
    }

    @Override
    public void applyCopiedToolConfig(ConfiguratorItem.ToolConfig config, Player player) {
        super.applyCopiedToolConfig(config, player);
        applyEndpointFaceConfig(config.placement(), true);
        normalizeEndpoint(config.placement().mode(), true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        normalizeEndpoint(NodeFaceMode.NONE, false);
    }

    private void applyEndpointFaceConfig(ConfiguratorItem.FaceConfig face, boolean includeMode) {
        if (includeMode) {
            setFaceMode(ENDPOINT_DIRECTION, face.mode());
        }
        setItemsEnabled(ENDPOINT_DIRECTION, face.itemsEnabled());
        setFluidsEnabled(ENDPOINT_DIRECTION, face.fluidsEnabled());
        setEnergyEnabled(ENDPOINT_DIRECTION, false);
        while (getRedstoneControl(ENDPOINT_DIRECTION) != face.redstoneControl()) {
            cycleRedstoneControl(ENDPOINT_DIRECTION);
        }
        adjustPriority(ENDPOINT_DIRECTION, face.priority() - getPriority(ENDPOINT_DIRECTION));
        for (int slot = 0; slot < FACE_FILTER_SLOTS; slot++) {
            setFaceFilter(ENDPOINT_DIRECTION, slot, face.filter(slot));
        }
    }

    private void normalizeEndpoint(NodeFaceMode fallbackMode, boolean notify) {
        if (forceSingleEndpointState(ENDPOINT_DIRECTION, fallbackMode, false) && notify) {
            markTopologyChanged();
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        fluidCapability.invalidate();
    }
}
