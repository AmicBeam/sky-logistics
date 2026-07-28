package com.skylogistics.block.entity;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeType;
import com.skylogistics.util.TransferCompat;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class SimplePipeBlockEntity extends SkyNodeBlockEntity {
    private static final String DISCONNECTED_SIDES_TAG = "DisconnectedSides";
    private static final String REMEMBERED_EXTRACT_SIDES_TAG = "RememberedExtractSides";
    private UUID networkLineId;
    private int disconnectedSides;
    private int rememberedExtractSides;

    public SimplePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIMPLE_PIPE.get(), pos, state);
        networkLineId = UUID.nameUUIDFromBytes(
                ("skylogistics:unassigned_simple_pipe:" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasCapability(Level level, BlockPos pos, Direction side, SimplePipeType type) {
        return switch (type) {
            case ITEM -> {
                IItemHandler handler = itemHandler(level, pos, side);
                yield handler != null && handler.getSlots() > 0;
            }
            case FLUID -> {
                IFluidHandler handler = fluidHandler(level, pos, side);
                yield handler != null && handler.getTanks() > 0;
            }
            case ENERGY -> {
                IEnergyStorage storage = energyHandler(level, pos, side);
                yield storage != null
                        && (storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive());
            }
        };
    }

    private static IItemHandler itemHandler(Level level, BlockPos pos, Direction side) {
        return TransferCompat.legacyItemHandler(level.getCapability(Capabilities.Item.BLOCK, pos, side));
    }

    private static IFluidHandler fluidHandler(Level level, BlockPos pos, Direction side) {
        return TransferCompat.legacyFluidHandler(level.getCapability(Capabilities.Fluid.BLOCK, pos, side));
    }

    private static IEnergyStorage energyHandler(Level level, BlockPos pos, Direction side) {
        return TransferCompat.legacyEnergyHandler(level.getCapability(Capabilities.Energy.BLOCK, pos, side));
    }

    public SimplePipeType pipeType() {
        return getBlockState().getBlock() instanceof SimplePipeBlock pipe ? pipe.pipeType() : SimplePipeType.ITEM;
    }

    public void assignNetworkLineId(UUID lineId) {
        networkLineId = lineId;
    }

    public boolean isSideDisconnected(Direction direction) {
        return (disconnectedSides & sideMask(direction)) != 0;
    }

    public SimplePipeConnection rememberedContainerConnection(Direction direction) {
        return (rememberedExtractSides & sideMask(direction)) != 0
                ? SimplePipeConnection.EXTRACT
                : SimplePipeConnection.INSERT;
    }

    public void setSideDisconnected(Direction direction, boolean disconnected,
            SimplePipeConnection previousConnection) {
        int mask = sideMask(direction);
        if (disconnected) {
            disconnectedSides |= mask;
            if (previousConnection == SimplePipeConnection.EXTRACT) {
                rememberedExtractSides |= mask;
            } else {
                rememberedExtractSides &= ~mask;
            }
        } else {
            disconnectedSides &= ~mask;
        }
        setChanged();
    }

    private static int sideMask(Direction direction) {
        return 1 << direction.ordinal();
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

    @Override
    protected void saveNodeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveNodeData(tag, registries);
        tag.putInt(DISCONNECTED_SIDES_TAG, disconnectedSides);
        tag.putInt(REMEMBERED_EXTRACT_SIDES_TAG, rememberedExtractSides);
    }

    @Override
    protected void loadNodeData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadNodeData(tag, registries);
        disconnectedSides = tag.getIntOr(DISCONNECTED_SIDES_TAG, 0);
        rememberedExtractSides = tag.getIntOr(REMEMBERED_EXTRACT_SIDES_TAG, 0);
    }

    private boolean enabled() {
        return switch (pipeType()) {
            case ITEM -> SkyLogisticsConfig.enableSimpleItemPipe();
            case FLUID -> SkyLogisticsConfig.enableSimpleFluidPipe();
            case ENERGY -> SkyLogisticsConfig.enableSimpleEnergyPipe();
        };
    }
}
