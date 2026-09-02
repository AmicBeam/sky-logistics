package com.skylogistics.network;

import com.skylogistics.block.entity.NetworkEndpointBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.astages.TransferResource;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.industrialforegoingsouls.SoulHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.OrderedMatchingMode;
import com.skylogistics.util.RedstoneControl;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Scheduler and menu contract shared by placed nodes and SavedData-backed virtual endpoints. */
public interface LogisticsEndpoint {
    Level getLevel();
    BlockPos getBlockPos();
    boolean isRemoved();
    UUID getLineId();
    NodeFaceMode getFaceMode(Direction direction);
    boolean isItemsEnabled(Direction direction);
    boolean isFluidsEnabled(Direction direction);
    boolean isEnergyEnabled(Direction direction);
    BlockPos getTargetPos(Direction direction);
    Direction getAccessSide(Direction direction);
    IItemHandler getEndpointItemHandler(Direction direction, long gameTime);
    IFluidHandler getEndpointFluidHandler(Direction direction, long gameTime);
    ChemicalHandlerBridge getEndpointChemicalHandler(Direction direction, long gameTime);
    SoulHandlerBridge getEndpointSoulHandler(Direction direction, long gameTime);
    IEnergyStorage getEndpointEnergyHandler(Direction direction, long gameTime);
    ManaHandlerBridge getEndpointManaHandler(Direction direction, long gameTime);
    SourceHandlerBridge getEndpointSourceHandler(Direction direction, long gameTime);
    boolean allowsItem(Direction direction, ItemStack stack);
    boolean allowsFluid(Direction direction, FluidStack stack);
    boolean allowsChemical(Direction direction, ChemicalStackView stack);
    boolean allowsSoul(Direction direction);
    boolean allowsEnergy(Direction direction);
    boolean allowsMana(Direction direction);
    boolean allowsSource(Direction direction);
    ItemStack getFaceFilter(Direction direction, int slot);
    boolean isFaceRedstoneAllowed(Direction direction);
    RedstoneControl getRedstoneControl(Direction direction);
    boolean consumeRedstonePulse(Direction direction);
    int getPriority(Direction direction);
    int getItemSlotLimit(Direction direction);
    long getMaintainAmount(Direction direction);
    boolean isMaintainByAmount(Direction direction);
    int getOperationRate();
    boolean hasDimensionUpgrade();
    UUID getTransferOwnerId();
    long limitItemTransfer(long amount);
    long limitFluidTransfer(long amount);
    long limitEnergyTransfer(long amount);
    long limitChemicalTransfer(long amount);
    long limitSoulTransfer(long amount);
    long limitManaTransfer(long amount);
    long limitSourceTransfer(long amount);
    boolean supportsChemicalEndpoint(Direction direction);
    boolean supportsSoulEndpoint(Direction direction);
    boolean supportsManaEndpoint(Direction direction);
    boolean supportsSourceEndpoint(Direction direction);
    TransferResource firstEnabledTransferResource(Direction direction);
    long getProgressionTransferLimit(TransferResource resource);
    long getConfiguredTransferLimit(TransferResource resource);
    int nextItemStart(int slots);
    int nextFluidStart(int tanks);
    int targetCursor(NetworkEndpointBlockEntity.TargetResource resource);
    void advanceTargetCursor(NetworkEndpointBlockEntity.TargetResource resource);
    void recordRecentTransfer();
    void recordRecentTransfer(Direction direction);
    boolean hasRecentTransfer();
    boolean hasRecentTransfer(Direction direction);

    default boolean hasOrderedMatchingUpgrade() { return false; }
    default OrderedMatchingMode getOrderedMatchingMode() { return OrderedMatchingMode.PER_ITEM; }
    default int getOrderedMatchingOffset() { return 0; }
    default int getOrderedMatchingCursor(Direction direction, int targetCount) { return 0; }
    default void setOrderedMatchingCursor(Direction direction, int cursor, int targetCount) {}
    default SkyNodeBlockEntity.OrderedMatchingBatch prepareOrderedMatchingBatch(Direction direction,
            int sourceSlot, ItemStack stack, int targetCount, int startCursor) { return null; }
    default boolean hasOrderedMatchingBatch(Direction direction) { return false; }
    default void clearOrderedMatchingBatch(Direction direction) {}

}
