package com.skylogistics.block.entity;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeModelData;
import com.skylogistics.util.SimplePipeType;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public class SimplePipeBlockEntity extends NetworkEndpointBlockEntity {
    private static final String PIPE_SIDES_TAG = "PipeSides";
    private static final String LEGACY_DISCONNECTED_SIDES_TAG = "DisconnectedSides";
    private static final String LEGACY_REMEMBERED_EXTRACT_SIDES_TAG = "RememberedExtractSides";
    private static final int SIDE_MASK = 0x3F;
    private static final int EXTRACT_SHIFT = 6;
    private static final int REMEMBERED_SHIFT = 12;
    private UUID networkLineId;
    private int disconnectedSides;
    private int extractSides;
    private int rememberedExtractSides;

    public SimplePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIMPLE_PIPE.get(), pos, state);
        networkLineId = UUID.nameUUIDFromBytes(
                ("skylogistics:unassigned_simple_pipe:" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean hasCapability(Level level, BlockPos pos, Direction side, SimplePipeType type) {
        return switch (type) {
            case ITEM -> {
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                yield handler != null && handler.getSlots() > 0;
            }
            case FLUID -> {
                IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
                yield handler != null && handler.getTanks() > 0
                        || SkyLogisticsConfig.allowFluidChemicalTransfer()
                        && MekanismCompat.isLoaded()
                        && MekanismCompat.chemicalHandler(level, pos, side) != null;
            }
            case ENERGY -> {
                IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
                yield storage != null
                        && (storage.getMaxEnergyStored() > 0 || storage.canExtract() || storage.canReceive())
                        || SkyLogisticsConfig.allowEnergyManaTransfer()
                        && BotaniaCompat.isLoaded()
                        && BotaniaCompat.manaHandler(level, pos, side) != null
                        || SkyLogisticsConfig.allowEnergySourceTransfer()
                        && ArsNouveauCompat.isLoaded()
                        && ArsNouveauCompat.sourceHandler(level, pos, side) != null;
            }
        };
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

    public boolean isExtracting(Direction direction) {
        return (extractSides & sideMask(direction)) != 0;
    }

    public void setExtracting(Direction direction, boolean extracting) {
        int mask = sideMask(direction);
        int updated = extracting ? extractSides | mask : extractSides & ~mask;
        if (updated == extractSides) {
            return;
        }
        extractSides = updated;
        setChanged();
        requestModelDataUpdate();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
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
            extractSides &= ~mask;
        } else {
            disconnectedSides &= ~mask;
            if (previousConnection == SimplePipeConnection.EXTRACT) {
                extractSides |= mask;
            }
        }
        setChanged();
        requestModelDataUpdate();
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
        if (!(state.getBlock() instanceof SimplePipeBlock) || level == null) {
            return NodeFaceMode.NONE;
        }
        return switch (SimplePipeBlock.connectionFromState(level, worldPosition, state, direction)) {
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
        return enabled() && pipeType() == SimplePipeType.FLUID && level != null
                && MekanismCompat.chemicalHandler(level, getTargetPos(direction), getAccessSide(direction)) != null;
    }

    @Override
    public boolean supportsManaEndpoint(Direction direction) {
        return enabled() && pipeType() == SimplePipeType.ENERGY && level != null
                && BotaniaCompat.manaHandler(level, getTargetPos(direction), getAccessSide(direction)) != null;
    }

    @Override
    public boolean supportsSourceEndpoint(Direction direction) {
        return enabled() && pipeType() == SimplePipeType.ENERGY && level != null
                && ArsNouveauCompat.sourceHandler(level, getTargetPos(direction), getAccessSide(direction)) != null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        int packed = (disconnectedSides & SIDE_MASK)
                | ((extractSides & SIDE_MASK) << EXTRACT_SHIFT)
                | ((rememberedExtractSides & SIDE_MASK) << REMEMBERED_SHIFT);
        if (packed != 0) {
            tag.putInt(PIPE_SIDES_TAG, packed);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(PIPE_SIDES_TAG)) {
            int packed = tag.getInt(PIPE_SIDES_TAG);
            disconnectedSides = packed & SIDE_MASK;
            extractSides = (packed >>> EXTRACT_SHIFT) & SIDE_MASK;
            rememberedExtractSides = (packed >>> REMEMBERED_SHIFT) & SIDE_MASK;
        } else {
            disconnectedSides = tag.getInt(LEGACY_DISCONNECTED_SIDES_TAG) & SIDE_MASK;
            extractSides = 0;
            rememberedExtractSides = tag.getInt(LEGACY_REMEMBERED_EXTRACT_SIDES_TAG) & SIDE_MASK;
        }
        requestModelDataUpdate();
    }

    @Override
    public ModelData getModelData() {
        return extractSides == 0
                ? ModelData.EMPTY
                : ModelData.builder().with(SimplePipeModelData.EXTRACT_SIDES, extractSides).build();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private boolean enabled() {
        return switch (pipeType()) {
            case ITEM -> SkyLogisticsConfig.enableSimpleItemPipe();
            case FLUID -> SkyLogisticsConfig.enableSimpleFluidPipe();
            case ENERGY -> SkyLogisticsConfig.enableSimpleEnergyPipe();
        };
    }
}
