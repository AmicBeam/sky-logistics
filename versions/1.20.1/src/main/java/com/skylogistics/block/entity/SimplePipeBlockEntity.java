package com.skylogistics.block.entity;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeType;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

public class SimplePipeBlockEntity extends SkyNodeBlockEntity {
    private UUID networkLineId;

    public SimplePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIMPLE_PIPE.get(), pos, state);
        networkLineId = UUID.nameUUIDFromBytes(
                ("skylogistics:unassigned_simple_pipe:" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasCapability(Level level, BlockPos pos, Direction side, SimplePipeType type) {
        BlockEntity target = level.getBlockEntity(pos);
        if (target == null) {
            return false;
        }
        return switch (type) {
            case ITEM -> target.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                    .map(handler -> handler.getSlots() > 0).orElse(false);
            case FLUID -> target.getCapability(ForgeCapabilities.FLUID_HANDLER, side)
                    .map(handler -> handler.getTanks() > 0).orElse(false);
            case ENERGY -> target.getCapability(ForgeCapabilities.ENERGY, side)
                    .map(storage -> storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive())
                    .orElse(false);
        };
    }

    public SimplePipeType pipeType() {
        return getBlockState().getBlock() instanceof SimplePipeBlock pipe ? pipe.pipeType() : SimplePipeType.ITEM;
    }

    public void assignNetworkLineId(UUID lineId) {
        networkLineId = lineId;
    }

    @Override
    public UUID getLineId() {
        return networkLineId;
    }

    @Override
    public NodeFaceMode getFaceMode(Direction direction) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SimplePipeBlock)) {
            return NodeFaceMode.NONE;
        }
        return switch (state.getValue(SimplePipeBlock.connectionProperty(direction))) {
            case EXTRACT -> NodeFaceMode.INPUT;
            case INSERT -> NodeFaceMode.OUTPUT;
            default -> NodeFaceMode.NONE;
        };
    }

    @Override
    public boolean isItemsEnabled(Direction direction) {
        return enabled() && pipeType() == SimplePipeType.ITEM && getFaceMode(direction) != NodeFaceMode.NONE;
    }

    @Override
    public boolean isFluidsEnabled(Direction direction) {
        return enabled() && pipeType() == SimplePipeType.FLUID && getFaceMode(direction) != NodeFaceMode.NONE;
    }

    @Override
    public boolean isEnergyEnabled(Direction direction) {
        return enabled() && pipeType() == SimplePipeType.ENERGY && getFaceMode(direction) != NodeFaceMode.NONE;
    }

    @Override
    public int getOperationRate() {
        return 1;
    }

    @Override
    public boolean hasDimensionUpgrade() {
        return false;
    }

    @Override
    public boolean isFaceRedstoneAllowed(Direction direction) {
        return true;
    }

    @Override
    public int getPriority(Direction direction) {
        return 0;
    }

    @Override
    public int getItemSlotLimit(Direction direction) {
        return ITEM_SLOT_LIMIT_UNLIMITED;
    }

    @Override
    public boolean allowsItem(Direction direction, ItemStack stack) {
        return true;
    }

    @Override
    public boolean allowsFluid(Direction direction, FluidStack stack) {
        return true;
    }

    @Override
    public long limitItemTransfer(long amount) {
        return Math.min(amount, SkyLogisticsConfig.simpleItemPipeTransferRate());
    }

    @Override
    public long limitFluidTransfer(long amount) {
        return Math.min(amount, SkyLogisticsConfig.simpleFluidPipeTransferRate());
    }

    @Override
    public long limitEnergyTransfer(long amount) {
        return Math.min(amount, SkyLogisticsConfig.simpleEnergyPipeTransferRate());
    }

    @Override
    public boolean supportsChemicalEndpoint(Direction direction) {
        return false;
    }

    @Override
    public boolean supportsManaEndpoint(Direction direction) {
        return false;
    }

    @Override
    public boolean supportsSourceEndpoint(Direction direction) {
        return false;
    }

    private boolean enabled() {
        return switch (pipeType()) {
            case ITEM -> SkyLogisticsConfig.enableSimpleItemPipe();
            case FLUID -> SkyLogisticsConfig.enableSimpleFluidPipe();
            case ENERGY -> SkyLogisticsConfig.enableSimpleEnergyPipe();
        };
    }
}
