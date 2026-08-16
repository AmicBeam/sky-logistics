package com.skylogistics.block.entity;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.ModFilterListItem;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.EnergyStorage;
import com.skylogistics.util.FluidHandler;
import com.skylogistics.util.ItemHandler;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeModelData;
import com.skylogistics.util.SimplePipeType;
import com.skylogistics.util.TransferCompat;
import com.skylogistics.util.StackData;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelData;

public class SimplePipeBlockEntity extends NetworkEndpointBlockEntity {
    private static final String PIPE_SIDES_TAG = "PipeSides";
    private static final String PIPE_FILTERS_TAG = "PipeFilters";
    private static final String LEGACY_DISCONNECTED_SIDES_TAG = "DisconnectedSides";
    private static final String LEGACY_REMEMBERED_EXTRACT_SIDES_TAG = "RememberedExtractSides";
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final int SIDE_MASK = 0x3F;
    private static final int EXTRACT_SHIFT = 6;
    private static final int REMEMBERED_SHIFT = 12;
    private UUID networkLineId;
    private UUID ownerId;
    private int disconnectedSides;
    private int extractSides;
    private int rememberedExtractSides;
    private final EnumMap<Direction, ItemStack> endpointFilters = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, FilterListItem.CompiledFilter> compiledEndpointFilters = new EnumMap<>(Direction.class);

    public SimplePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIMPLE_PIPE.get(), pos, state);
        networkLineId = UUID.nameUUIDFromBytes(
                ("skylogistics:unassigned_simple_pipe:" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
        for (Direction direction : Direction.values()) endpointFilters.put(direction, ItemStack.EMPTY);
    }

    public static boolean hasCapability(Level level, BlockPos pos, Direction side, SimplePipeType type) {
        if (level.getBlockEntity(pos) instanceof SkyDistributorBlockEntity distributor) {
            return switch (type) {
                case ITEM -> distributor.hasItemTargets(side);
                case FLUID -> distributor.hasFluidTargets(side) || distributor.hasChemicalTargets(side);
                case ENERGY -> distributor.hasEnergyTargets(side) || distributor.hasManaTargets(side)
                        || distributor.hasSourceTargets(side);
            };
        }
        return switch (type) {
            case ITEM -> {
                ItemHandler handler = itemHandler(level, pos, side);
                yield handler != null && handler.getSlots() > 0;
            }
            case FLUID -> {
                FluidHandler handler = fluidHandler(level, pos, side);
                yield handler != null && handler.getTanks() > 0
                        || SkyLogisticsConfig.allowFluidChemicalTransfer()
                        && MekanismCompat.isLoaded()
                        && MekanismCompat.chemicalHandler(level, pos, side) != null;
            }
            case ENERGY -> {
                EnergyStorage storage = energyHandler(level, pos, side);
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

    private static ItemHandler itemHandler(Level level, BlockPos pos, Direction side) {
        return TransferCompat.itemHandler(level.getCapability(Capabilities.Item.BLOCK, pos, side));
    }

    private static FluidHandler fluidHandler(Level level, BlockPos pos, Direction side) {
        return TransferCompat.fluidHandler(level.getCapability(Capabilities.Fluid.BLOCK, pos, side));
    }

    private static EnergyStorage energyHandler(Level level, BlockPos pos, Direction side) {
        return TransferCompat.energyStorage(level.getCapability(Capabilities.Energy.BLOCK, pos, side));
    }

    public SimplePipeType pipeType() {
        return getBlockState().getBlock() instanceof SimplePipeBlock pipe ? pipe.pipeType() : SimplePipeType.ITEM;
    }

    public void assignNetworkLineId(UUID lineId) {
        networkLineId = lineId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public void assignOwnerId(UUID ownerId) {
        if (ownerId != null && !ownerId.equals(this.ownerId)) {
            this.ownerId = ownerId;
            setChanged();
        }
    }

    @Override
    public UUID getTransferOwnerId() {
        return ownerId;
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
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
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
        FilterListItem.CompiledFilter filter = endpointFilter(direction);
        return !filter.hasItemRules() || filter.matches(stack);
    }

    @Override
    public boolean allowsFluid(Direction direction, FluidStack stack) {
        FilterListItem.CompiledFilter filter = endpointFilter(direction);
        return !filter.hasFluidRules() || filter.matchesFluid(stack);
    }

    @Override
    public boolean allowsEnergy(Direction direction) {
        FilterListItem.CompiledFilter filter = endpointFilter(direction);
        return !filter.hasEnergyRules() || filter.matchesEnergy(ModFilterListItem.FORGE_ENERGY_MOD_ID);
    }

    @Override
    public ItemStack getFaceFilter(Direction direction, int slot) {
        return slot == 0 ? endpointFilters.getOrDefault(direction, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public boolean setEndpointFilter(Direction direction, ItemStack stack) {
        if (direction == null || !FilterListItem.isFilterItem(stack)
                || pipeType() == SimplePipeType.ENERGY && !ModFilterListItem.isModFilterList(stack)) return false;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        if (StackData.sameItemAndComponents(getFaceFilter(direction, 0), copy)) return true;
        endpointFilters.put(direction, copy);
        compiledEndpointFilters.remove(direction);
        syncFilterChange();
        return true;
    }

    public boolean clearEndpointFilter(Direction direction) {
        if (direction == null || getFaceFilter(direction, 0).isEmpty()) return false;
        endpointFilters.put(direction, ItemStack.EMPTY);
        compiledEndpointFilters.remove(direction);
        syncFilterChange();
        return true;
    }

    private FilterListItem.CompiledFilter endpointFilter(Direction direction) {
        return compiledEndpointFilters.computeIfAbsent(direction,
                key -> FilterListItem.compile(endpointFilters.getOrDefault(key, ItemStack.EMPTY)));
    }

    private void syncFilterChange() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
            if (level instanceof ServerLevel serverLevel) {
                SkyNetworkRegistry.markPipeTopologyDirty(serverLevel, worldPosition);
            }
        }
    }

    @Override
    public long limitItemTransfer(long amount) {
        return Math.min(super.limitItemTransfer(amount), SkyLogisticsConfig.simpleItemPipeTransferRate());
    }

    @Override
    public long limitFluidTransfer(long amount) {
        return Math.min(super.limitFluidTransfer(amount), SkyLogisticsConfig.simpleFluidPipeTransferRate());
    }

    @Override
    public long limitEnergyTransfer(long amount) {
        return Math.min(super.limitEnergyTransfer(amount), SkyLogisticsConfig.simpleEnergyPipeTransferRate());
    }

    @Override
    public long limitChemicalTransfer(long amount) {
        return Math.min(super.limitChemicalTransfer(amount), SkyLogisticsConfig.simpleChemicalPipeTransferRate());
    }

    @Override
    public long limitManaTransfer(long amount) {
        return Math.min(super.limitManaTransfer(amount), SkyLogisticsConfig.simpleManaPipeTransferRate());
    }

    @Override
    public long limitSourceTransfer(long amount) {
        return Math.min(super.limitSourceTransfer(amount), SkyLogisticsConfig.simpleSourcePipeTransferRate());
    }

    @Override
    public boolean supportsChemicalEndpoint(Direction direction) {
        if (level != null && level.getBlockEntity(getTargetPos(direction)) instanceof SkyDistributorBlockEntity distributor) {
            return enabled() && pipeType() == SimplePipeType.FLUID
                    && distributor.hasChemicalTargets(getAccessSide(direction));
        }
        return enabled() && pipeType() == SimplePipeType.FLUID && level != null
                && MekanismCompat.chemicalHandler(level, getTargetPos(direction), getAccessSide(direction)) != null;
    }

    @Override
    public boolean supportsManaEndpoint(Direction direction) {
        if (level != null && level.getBlockEntity(getTargetPos(direction)) instanceof SkyDistributorBlockEntity distributor) {
            return enabled() && pipeType() == SimplePipeType.ENERGY
                    && distributor.hasManaTargets(getAccessSide(direction));
        }
        return enabled() && pipeType() == SimplePipeType.ENERGY && level != null
                && BotaniaCompat.manaHandler(level, getTargetPos(direction), getAccessSide(direction)) != null;
    }

    @Override
    public boolean supportsSourceEndpoint(Direction direction) {
        if (level != null && level.getBlockEntity(getTargetPos(direction)) instanceof SkyDistributorBlockEntity distributor) {
            return enabled() && pipeType() == SimplePipeType.ENERGY
                    && distributor.hasSourceTargets(getAccessSide(direction));
        }
        return enabled() && pipeType() == SimplePipeType.ENERGY && level != null
                && ArsNouveauCompat.sourceHandler(level, getTargetPos(direction), getAccessSide(direction)) != null;
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        int packed = (disconnectedSides & SIDE_MASK)
                | ((extractSides & SIDE_MASK) << EXTRACT_SHIFT)
                | ((rememberedExtractSides & SIDE_MASK) << REMEMBERED_SHIFT);
        if (packed != 0) {
            output.putInt(PIPE_SIDES_TAG, packed);
        }
        CompoundTag filters = new CompoundTag();
        for (Direction direction : Direction.values()) {
            ItemStack filter = getFaceFilter(direction, 0);
            if (!filter.isEmpty()) filters.put(direction.getSerializedName(), StackData.saveItem(filter));
        }
        if (!filters.isEmpty()) output.store(PIPE_FILTERS_TAG, CompoundTag.CODEC, filters);
        if (ownerId != null) output.putString(OWNER_ID_TAG, ownerId.toString());
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        endpointFilters.replaceAll((direction, ignored) -> ItemStack.EMPTY);
        compiledEndpointFilters.clear();
        CompoundTag filters = input.read(PIPE_FILTERS_TAG, CompoundTag.CODEC).orElse(new CompoundTag());
        for (Direction direction : Direction.values()) {
            if (filters.contains(direction.getSerializedName())) {
                endpointFilters.put(direction, StackData.loadItem(filters.getCompoundOrEmpty(direction.getSerializedName())));
            }
        }
        if (input.getInt(PIPE_SIDES_TAG).isPresent()) {
            int packed = input.getIntOr(PIPE_SIDES_TAG, 0);
            disconnectedSides = packed & SIDE_MASK;
            extractSides = (packed >>> EXTRACT_SHIFT) & SIDE_MASK;
            rememberedExtractSides = (packed >>> REMEMBERED_SHIFT) & SIDE_MASK;
        } else {
            disconnectedSides = input.getIntOr(LEGACY_DISCONNECTED_SIDES_TAG, 0) & SIDE_MASK;
            extractSides = 0;
            rememberedExtractSides = input.getIntOr(LEGACY_REMEMBERED_EXTRACT_SIDES_TAG, 0) & SIDE_MASK;
        }
        ownerId = parseOwnerId(input.getStringOr(OWNER_ID_TAG, ""));
        requestModelDataUpdate();
        if (level != null && level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    public ModelData getModelData() {
        return extractSides == 0
                ? ModelData.EMPTY
                : ModelData.of(SimplePipeModelData.EXTRACT_SIDES, extractSides);
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

    private static UUID parseOwnerId(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
