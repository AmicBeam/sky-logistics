package com.skylogistics.block.entity;

import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
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

    private int itemCursor;
    private int fluidCursor;
    private int targetCursor;

    protected NetworkEndpointBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
        return null;
    }

    public IFluidHandler getEndpointFluidHandler(Direction direction, long gameTime) {
        return null;
    }

    public ChemicalHandlerBridge getEndpointChemicalHandler(Direction direction, long gameTime) {
        return null;
    }

    public IEnergyStorage getEndpointEnergyHandler(Direction direction, long gameTime) {
        return null;
    }

    public ManaHandlerBridge getEndpointManaHandler(Direction direction, long gameTime) {
        return null;
    }

    public SourceHandlerBridge getEndpointSourceHandler(Direction direction, long gameTime) {
        return null;
    }

    public boolean allowsItem(Direction direction, ItemStack stack) {
        return true;
    }

    public boolean allowsFluid(Direction direction, FluidStack stack) {
        return true;
    }

    public ItemStack getFaceFilter(Direction direction, int slot) {
        return ItemStack.EMPTY;
    }

    public boolean isFaceRedstoneAllowed(Direction direction) {
        return true;
    }

    public RedstoneControl getRedstoneControl(Direction direction) {
        return RedstoneControl.IGNORE;
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

    public long limitItemTransfer(long amount) {
        return amount;
    }

    public long limitFluidTransfer(long amount) {
        return amount;
    }

    public long limitEnergyTransfer(long amount) {
        return amount;
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

    public int nextTargetCursor() {
        int start = targetCursor;
        targetCursor = targetCursor == Integer.MAX_VALUE ? 0 : targetCursor + 1;
        return start;
    }

    public void lineNameChanged(UUID targetLineId) {
    }
}
