package com.skylogistics.compat.beyonddimensions;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

public final class BeyondDimensionsCompat {
    private static final String BEYOND_DIMENSIONS = "beyonddimensions";
    private static boolean warned;

    private BeyondDimensionsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(BEYOND_DIMENSIONS);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyExternalHandlers.Items.INSTANCE;
        }
        try {
            return BeyondDimensionsApiBridge.createItemHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Items.INSTANCE;
        }
    }

    public static IFluidHandler createFluidHandler(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
        try {
            return BeyondDimensionsApiBridge.createFluidHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    public static IEnergyStorage createEnergyHandler(BlockEntity host) {
        if (!isLoaded()) {
            return EmptyExternalHandlers.Energy.INSTANCE;
        }
        try {
            return BeyondDimensionsApiBridge.createEnergyHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Energy.INSTANCE;
        }
    }

    public static ManaHandlerBridge createManaHandler(BlockEntity host) {
        if (!isLoaded() || !BotaniaCompat.isLoaded()) {
            return null;
        }
        try {
            return BeyondDimensionsApiBridge.createManaHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static void bindOnPlaced(Level level, BlockPos pos, LivingEntity placer) {
        if (!isLoaded() || level.isClientSide) {
            return;
        }
        try {
            BeyondDimensionsApiBridge.bindOnPlaced(level, pos, placer);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    public static boolean handleBindingUse(Level level, BlockPos pos, Player player) {
        if (!isLoaded() || !player.getMainHandItem().isEmpty() || !player.isShiftKeyDown()) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        try {
            BeyondDimensionsApiBridge.handleBindingUse(level, pos, player);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
        }
        return true;
    }

    public static ItemResource itemResourceInSlot(BlockEntity host, int slot) {
        if (!isLoaded()) {
            return ItemResource.EMPTY;
        }
        try {
            return BeyondDimensionsApiBridge.itemResourceInSlot(host, slot);
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
        if (!isLoaded()) {
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
        if (!isLoaded()) {
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

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn(
                    "Beyond Dimensions compat is disabled because the loaded Beyond Dimensions API is not compatible.",
                    error);
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
}
