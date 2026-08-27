package com.skylogistics.block.entity;

import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.astages.AStagesTransferLimiter;
import com.skylogistics.compat.astages.TransferResource;
import com.skylogistics.compat.advancements.AdvancementTransferLimiter;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.network.SkyPlayerLines;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Minimal scheduler-facing base shared by configurable nodes and simple pipes.
 * It intentionally contains no line-name, filter, upgrade, or per-face configuration data.
 */
public abstract class NetworkEndpointBlockEntity extends BlockEntity {
    public static final int ITEM_SLOT_LIMIT_UNLIMITED = 0;

    public enum TargetResource {
        ITEM,
        FLUID,
        CHEMICAL,
        ENERGY,
        MANA,
        SOURCE
    }

    private int itemCursor;
    private int fluidCursor;
    private final int[] targetCursors = new int[TargetResource.values().length];
    private long lastTransferGameTime = Long.MIN_VALUE;
    private final long[] lastTransferGameTimeByDirection = new long[Direction.values().length];

    protected NetworkEndpointBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Arrays.fill(lastTransferGameTimeByDirection, Long.MIN_VALUE);
    }

    public void recordRecentTransfer() {
        if (level != null) lastTransferGameTime = level.getGameTime();
    }

    public boolean hasRecentTransfer() {
        return level != null && lastTransferGameTime != Long.MIN_VALUE
                && level.getGameTime() - lastTransferGameTime <= 40L;
    }

    public void recordRecentTransfer(Direction direction) {
        recordRecentTransfer();
        if (level != null && direction != null) {
            lastTransferGameTimeByDirection[direction.ordinal()] = level.getGameTime();
        }
    }

    public boolean hasRecentTransfer(Direction direction) {
        if (level == null || direction == null) return false;
        long lastTransfer = lastTransferGameTimeByDirection[direction.ordinal()];
        return lastTransfer != Long.MIN_VALUE && level.getGameTime() - lastTransfer <= 40L;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            SkyNetworkRegistry.register(serverLevel, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            SkyNetworkRegistry.unregister(serverLevel, worldPosition);
        }
        super.setRemoved();
    }

    public abstract UUID getLineId();

    public abstract NodeFaceMode getFaceMode(Direction direction);

    public abstract boolean isItemsEnabled(Direction direction);

    public abstract boolean isFluidsEnabled(Direction direction);

    public abstract boolean isEnergyEnabled(Direction direction);

    public BlockPos getTargetPos(Direction direction) {
        return worldPosition.relative(direction);
    }

    public Direction getAccessSide(Direction direction) {
        return direction.getOpposite();
    }

    public IItemHandler getEndpointItemHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity distributor = distributor(direction);
        return distributor == null ? null : distributor.itemHandler(getAccessSide(direction));
    }

    public IFluidHandler getEndpointFluidHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity distributor = distributor(direction);
        return distributor == null ? null : distributor.fluidHandler(getAccessSide(direction));
    }

    public ChemicalHandlerBridge getEndpointChemicalHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity distributor = distributor(direction);
        return distributor == null ? null : distributor.chemicalHandler(getAccessSide(direction));
    }

    public IEnergyStorage getEndpointEnergyHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity distributor = distributor(direction);
        return distributor == null ? null : distributor.energyHandler(getAccessSide(direction));
    }

    public ManaHandlerBridge getEndpointManaHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity distributor = distributor(direction);
        return distributor == null ? null : distributor.manaHandler(getAccessSide(direction));
    }

    public SourceHandlerBridge getEndpointSourceHandler(Direction direction, long gameTime) {
        SkyDistributorBlockEntity distributor = distributor(direction);
        return distributor == null ? null : distributor.sourceHandler(getAccessSide(direction));
    }

    protected SkyDistributorBlockEntity distributor(Direction direction) {
        if (level == null || !level.isLoaded(getTargetPos(direction))) return null;
        return level.getBlockEntity(getTargetPos(direction)) instanceof SkyDistributorBlockEntity distributor
                ? distributor : null;
    }

    public boolean allowsItem(Direction direction, ItemStack stack) {
        return true;
    }

    public boolean allowsFluid(Direction direction, FluidStack stack) {
        return true;
    }

    public boolean allowsChemical(Direction direction, ChemicalStackView stack) {
        return true;
    }

    public boolean allowsEnergy(Direction direction) { return true; }
    public boolean allowsMana(Direction direction) { return true; }
    public boolean allowsSource(Direction direction) { return true; }

    public ItemStack getFaceFilter(Direction direction, int slot) {
        return ItemStack.EMPTY;
    }

    public boolean isFaceRedstoneAllowed(Direction direction) {
        return true;
    }

    public RedstoneControl getRedstoneControl(Direction direction) {
        return RedstoneControl.IGNORE;
    }

    public boolean consumeRedstonePulse(Direction direction) {
        return false;
    }

    public int getPriority(Direction direction) {
        return 0;
    }

    public int getItemSlotLimit(Direction direction) {
        return ITEM_SLOT_LIMIT_UNLIMITED;
    }

    public int getOperationRate() {
        return 1;
    }

    public boolean hasDimensionUpgrade() {
        return false;
    }

    public UUID getTransferOwnerId() {
        return level instanceof ServerLevel serverLevel
                ? SkyPlayerLines.ownerOf(serverLevel.getServer(), getLineId()) : null;
    }

    public long limitItemTransfer(long amount) {
        return limitProgression(TransferResource.ITEMS, amount);
    }

    public long limitFluidTransfer(long amount) {
        return limitProgression(TransferResource.FLUIDS, amount);
    }

    public long limitEnergyTransfer(long amount) {
        return limitProgression(TransferResource.ENERGY, amount);
    }

    public long limitChemicalTransfer(long amount) {
        return limitProgression(TransferResource.CHEMICALS, amount);
    }

    public long limitManaTransfer(long amount) {
        return limitProgression(TransferResource.MANA, amount);
    }

    public long limitSourceTransfer(long amount) {
        return limitProgression(TransferResource.SOURCE, amount);
    }

    public boolean supportsChemicalEndpoint(Direction direction) {
        return false;
    }

    public boolean supportsManaEndpoint(Direction direction) {
        return false;
    }

    public boolean supportsSourceEndpoint(Direction direction) {
        return false;
    }

    private long limitProgression(TransferResource resource, long amount) {
        if (!(level instanceof ServerLevel serverLevel)) return amount;
        UUID ownerId = getTransferOwnerId();
        long limited = AStagesTransferLimiter.limit(ownerId, resource, amount, serverLevel.getGameTime());
        return AdvancementTransferLimiter.limit(serverLevel.getServer(), ownerId, resource, limited,
                serverLevel.getGameTime());
    }

    public TransferResource firstEnabledTransferResource(Direction direction) {
        return null;
    }

    public long getProgressionTransferLimit(TransferResource resource) {
        return limitProgression(resource, Long.MAX_VALUE);
    }

    public long getConfiguredTransferLimit(TransferResource resource) {
        return switch (resource) {
            case ITEMS -> SkyLogisticsConfig.nodeItemTransferLimit();
            case ENERGY, MANA, SOURCE -> SkyLogisticsConfig.nodeEnergyTransferLimit();
            default -> Long.MAX_VALUE;
        };
    }

    public int nextItemStart(int slots) {
        if (slots <= 0) {
            return 0;
        }
        int start = Math.floorMod(itemCursor, slots);
        itemCursor = (start + 1) % slots;
        return start;
    }

    public int nextFluidStart(int tanks) {
        if (tanks <= 0) {
            return 0;
        }
        int start = Math.floorMod(fluidCursor, tanks);
        fluidCursor = (start + 1) % tanks;
        return start;
    }

    public int targetCursor(TargetResource resource) {
        return targetCursors[resource.ordinal()];
    }

    public void advanceTargetCursor(TargetResource resource) {
        int index = resource.ordinal();
        int cursor = targetCursors[index];
        targetCursors[index] = cursor == Integer.MAX_VALUE ? 0 : cursor + 1;
    }

    public void lineNameChanged(UUID targetLineId) {
    }
}
