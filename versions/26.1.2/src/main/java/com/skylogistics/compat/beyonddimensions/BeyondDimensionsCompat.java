package com.skylogistics.compat.beyonddimensions;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class BeyondDimensionsCompat {
    private static final String BEYOND_DIMENSIONS = "beyonddimensions";
    private static boolean warned;

    private BeyondDimensionsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(BEYOND_DIMENSIONS);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        return isLoaded() && SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()
                ? new ItemHandler(host) : EmptyExternalHandlers.Items.INSTANCE;
    }

    public static IFluidHandler createFluidHandler(BlockEntity host) {
        return isLoaded() ? new FluidHandler(host) : EmptyExternalHandlers.Fluids.INSTANCE;
    }

    public static ChemicalHandlerBridge createChemicalHandler(BlockEntity host) {
        return isLoaded() && MekanismCompat.isLoaded() ? new ChemicalHandler(host) : null;
    }

    public static IEnergyStorage createEnergyHandler(BlockEntity host) {
        return isLoaded() ? new EnergyHandler(host) : EmptyExternalHandlers.Energy.INSTANCE;
    }

    public static SourceHandlerBridge createSourceHandler(BlockEntity host) {
        return isLoaded() && ArsNouveauCompat.isLoaded() ? new SourceHandler(host) : null;
    }

    public static ItemResource itemResourceInSlot(BlockEntity host, int slot) {
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()) {
            return ItemResource.EMPTY;
        }
        try {
            return BeyondDimensionsApiBridge.itemResourceInSlot(host, slot);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return ItemResource.EMPTY;
        }
    }

    public static int itemTypeCount(BlockEntity host) {
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()) {
            return 0;
        }
        try {
            return BeyondDimensionsApiBridge.itemTypeCount(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0;
        }
    }

    public static ItemResource itemResourceForStack(BlockEntity host, ItemStack stack) {
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()) {
            return ItemResource.EMPTY;
        }
        try {
            return BeyondDimensionsApiBridge.itemResourceForStack(host, stack);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return ItemResource.EMPTY;
        }
    }

    public static ItemResource itemResourceForTag(BlockEntity host, TagKey<Item> tag) {
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()) {
            return ItemResource.EMPTY;
        }
        try {
            return BeyondDimensionsApiBridge.itemResourceForTag(host, tag);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return ItemResource.EMPTY;
        }
    }

    public static FluidResource fluidResourceInTank(BlockEntity host, int tank) {
        if (!isLoaded()) {
            return FluidResource.EMPTY;
        }
        try {
            return BeyondDimensionsApiBridge.fluidResourceInTank(host, tank);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return FluidResource.EMPTY;
        }
    }

    public static long insertItem(BlockEntity host, ItemStack stack, long amount, boolean simulate) {
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.insertItem(host, stack, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long extractItem(BlockEntity host, ItemStack stack, long amount, boolean simulate) {
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.extractItem(host, stack, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long insertFluid(BlockEntity host, FluidStack stack, long amount, boolean simulate) {
        if (!isLoaded()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.insertFluid(host, stack, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long extractFluid(BlockEntity host, FluidStack stack, long amount, boolean simulate) {
        if (!isLoaded()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.extractFluid(host, stack, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long energyStored(BlockEntity host) {
        if (!isLoaded()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.energyStored(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long insertEnergy(BlockEntity host, long amount, boolean simulate) {
        if (!isLoaded()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.insertEnergy(host, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long extractEnergy(BlockEntity host, long amount, boolean simulate) {
        if (!isLoaded()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.extractEnergy(host, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long sourceStored(BlockEntity host) {
        if (!isLoaded() || !ArsNouveauCompat.isLoaded()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.sourceStored(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long insertSource(BlockEntity host, long amount, boolean simulate) {
        if (!isLoaded() || !ArsNouveauCompat.isLoaded()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.insertSource(host, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long extractSource(BlockEntity host, long amount, boolean simulate) {
        if (!isLoaded() || !ArsNouveauCompat.isLoaded()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.extractSource(host, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static void bindOnPlaced(Level level, BlockPos pos, LivingEntity placer) {
        if (!isLoaded() || level.isClientSide() || !(placer instanceof Player player)) {
            return;
        }
        try {
            BeyondDimensionsApiBridge.bindOnPlaced(level, pos, player);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    public static boolean handleBindingUse(Level level, BlockPos pos, Player player) {
        if (!isLoaded() || !player.getMainHandItem().isEmpty() || !player.isShiftKeyDown()) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        try {
            BeyondDimensionsApiBridge.handleBindingUse(level, pos, player);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
        return true;
    }

    private static IItemHandler itemHandler(BlockEntity host) {
        try {
            return isLoaded() && SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()
                    ? BeyondDimensionsApiBridge.itemHandler(host) : EmptyExternalHandlers.Items.INSTANCE;
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Items.INSTANCE;
        }
    }

    private static IFluidHandler fluidHandler(BlockEntity host) {
        try {
            return isLoaded() ? BeyondDimensionsApiBridge.fluidHandler(host) : EmptyExternalHandlers.Fluids.INSTANCE;
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    private static ChemicalHandlerBridge chemicalHandler(BlockEntity host) {
        try {
            return isLoaded() && MekanismCompat.isLoaded() ? BeyondDimensionsApiBridge.chemicalHandler(host) : null;
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static IEnergyStorage energyHandler(BlockEntity host) {
        try {
            return isLoaded() ? BeyondDimensionsApiBridge.energyHandler(host) : EmptyExternalHandlers.Energy.INSTANCE;
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Energy.INSTANCE;
        }
    }

    private static SourceHandlerBridge sourceHandler(BlockEntity host) {
        try {
            return isLoaded() && ArsNouveauCompat.isLoaded() ? BeyondDimensionsApiBridge.sourceHandler(host) : null;
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn(
                    "Beyond Dimensions compat is disabled because the loaded Beyond Dimensions API is not compatible.",
                    error);
        }
    }

    private record ChemicalHandler(BlockEntity host) implements ChemicalHandlerBridge {
        @Override
        public int getTanks() {
            ChemicalHandlerBridge handler = chemicalHandler(host);
            return handler == null ? 0 : handler.getTanks();
        }

        @Override
        public ChemicalStackView getChemicalInTank(int tank) {
            ChemicalHandlerBridge handler = chemicalHandler(host);
            return handler == null ? EmptyChemicalStackView.INSTANCE : handler.getChemicalInTank(tank);
        }

        @Override
        public ChemicalStackView extractChemical(int tank, long amount, boolean simulate) {
            ChemicalHandlerBridge handler = chemicalHandler(host);
            return handler == null ? EmptyChemicalStackView.INSTANCE : handler.extractChemical(tank, amount, simulate);
        }

        @Override
        public long insertChemical(ChemicalStackView stack, boolean simulate) {
            ChemicalHandlerBridge handler = chemicalHandler(host);
            return handler == null ? 0L : handler.insertChemical(stack, simulate);
        }
    }

    private enum EmptyChemicalStackView implements ChemicalStackView {
        INSTANCE;

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public long getAmount() {
            return 0L;
        }

        @Override
        public ChemicalStackView copyWithAmount(long amount) {
            return this;
        }

        @Override
        public boolean isSameChemical(ChemicalStackView other) {
            return other != null && other.isEmpty();
        }
    }

    public interface NetworkBoundHost {
        int getDimensionNetworkId();

        void setDimensionNetworkId(int netId);

        void clearDimensionNetworkId();
    }

    public record ItemResource(ItemStack stack, long amount) {
        public static final ItemResource EMPTY = new ItemResource(ItemStack.EMPTY, 0L);

        public boolean isEmpty() {
            return stack.isEmpty() || amount <= 0L;
        }
    }

    public record FluidResource(FluidStack stack, long amount) {
        public static final FluidResource EMPTY = new FluidResource(FluidStack.EMPTY, 0L);

        public boolean isEmpty() {
            return stack.isEmpty() || amount <= 0L;
        }
    }

    private record ItemHandler(BlockEntity host) implements IItemHandler {
        @Override
        public int getSlots() {
            return itemHandler(host).getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return itemHandler(host).getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return itemHandler(host).insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return itemHandler(host).extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return itemHandler(host).getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return itemHandler(host).isItemValid(slot, stack);
        }
    }

    private record FluidHandler(BlockEntity host) implements IFluidHandler {
        @Override
        public int getTanks() {
            return fluidHandler(host).getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fluidHandler(host).getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return fluidHandler(host).getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return fluidHandler(host).isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fluidHandler(host).fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return fluidHandler(host).drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return fluidHandler(host).drain(maxDrain, action);
        }
    }

    private record EnergyHandler(BlockEntity host) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return energyHandler(host).receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return energyHandler(host).extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return energyHandler(host).getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return energyHandler(host).getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return energyHandler(host).canExtract();
        }

        @Override
        public boolean canReceive() {
            return energyHandler(host).canReceive();
        }
    }

    private record SourceHandler(BlockEntity host) implements SourceHandlerBridge {
        @Override
        public int getCurrentSource() {
            SourceHandlerBridge handler = sourceHandler(host);
            return handler == null ? 0 : handler.getCurrentSource();
        }

        @Override
        public int getMaxSource() {
            SourceHandlerBridge handler = sourceHandler(host);
            return handler == null ? 0 : handler.getMaxSource();
        }

        @Override
        public boolean canExtract() {
            SourceHandlerBridge handler = sourceHandler(host);
            return handler != null && handler.canExtract();
        }

        @Override
        public boolean canReceive() {
            SourceHandlerBridge handler = sourceHandler(host);
            return handler != null && handler.canReceive();
        }

        @Override
        public int extractSource(int amount, boolean simulate) {
            SourceHandlerBridge handler = sourceHandler(host);
            return handler == null ? 0 : handler.extractSource(amount, simulate);
        }

        @Override
        public int insertSource(int amount, boolean simulate) {
            SourceHandlerBridge handler = sourceHandler(host);
            return handler == null ? 0 : handler.insertSource(amount, simulate);
        }
    }
}
