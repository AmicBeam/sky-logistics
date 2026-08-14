package com.skylogistics.block.entity;

import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeModelData;
import com.skylogistics.util.SimplePipeType;
import com.skylogistics.util.StackData;
import java.util.EnumMap;
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
    private final EnumMap<Direction, FilterListItem.CompiledFilter> compiledEndpointFilters =
            new EnumMap<>(Direction.class);

    public SimplePipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIMPLE_PIPE.get(), pos, state);
        networkLineId = UUID.nameUUIDFromBytes(
                ("skylogistics:unassigned_simple_pipe:" + pos.asLong()).getBytes(StandardCharsets.UTF_8));
        for (Direction direction : Direction.values()) endpointFilters.put(direction, ItemStack.EMPTY);
    }

    public static boolean hasCapability(Level level, BlockPos pos, Direction side, SimplePipeType type) {
        if (level.getBlockEntity(pos) instanceof SkyDistributorBlockEntity distributor) {
            return switch (type) {
                case ITEM -> distributor.hasItemTargets();
                case FLUID -> distributor.hasFluidTargets();
                case ENERGY -> distributor.hasEnergyTargets();
            };
        }
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
        if (level != null && !level.isClientSide) {
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
    public boolean allowsChemical(Direction direction,
            com.skylogistics.compat.mekanism.ChemicalStackView stack) {
        FilterListItem.CompiledFilter filter = endpointFilter(direction);
        return !filter.hasChemicalRules() || filter.matchesChemical(stack);
    }

    @Override
    public ItemStack getFaceFilter(Direction direction, int slot) {
        return slot == 0 ? endpointFilters.getOrDefault(direction, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public boolean setEndpointFilter(Direction direction, ItemStack stack) {
        if (direction == null || pipeType() == SimplePipeType.ENERGY || !FilterListItem.isFilterItem(stack)) return false;
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
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
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
        CompoundTag filters = new CompoundTag();
        for (Direction direction : Direction.values()) {
            ItemStack filter = getFaceFilter(direction, 0);
            if (!filter.isEmpty()) filters.put(direction.getSerializedName(), StackData.saveItem(filter, registries));
        }
        if (!filters.isEmpty()) tag.put(PIPE_FILTERS_TAG, filters);
        if (ownerId != null) tag.putString(OWNER_ID_TAG, ownerId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        endpointFilters.replaceAll((direction, ignored) -> ItemStack.EMPTY);
        compiledEndpointFilters.clear();
        if (tag.contains(PIPE_FILTERS_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            CompoundTag filters = tag.getCompound(PIPE_FILTERS_TAG);
            for (Direction direction : Direction.values()) {
                if (filters.contains(direction.getSerializedName(), net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                    endpointFilters.put(direction, StackData.loadItem(
                            filters.getCompound(direction.getSerializedName()), registries));
                }
            }
        }
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
        ownerId = parseOwnerId(tag.getString(OWNER_ID_TAG));
        requestModelDataUpdate();
        if (level != null && level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        }
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

    private static UUID parseOwnerId(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
