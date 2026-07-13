package com.skylogistics.compat.beyonddimensions;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
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
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()) {
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
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsFluidTransfer()) {
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
        try {
            return BeyondDimensionsApiBridge.createFluidHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    public static ChemicalHandlerBridge createChemicalHandler(BlockEntity host) {
        if (!isLoaded() || !MekanismCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsMekanismChemicalTransfer()) {
            return null;
        }
        try {
            return BeyondDimensionsApiBridge.createChemicalHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static IEnergyStorage createEnergyHandler(BlockEntity host) {
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsEnergyTransfer()) {
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
        if (!isLoaded() || !BotaniaCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsManaTransfer()) {
            return null;
        }
        try {
            return BeyondDimensionsApiBridge.createManaHandler(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static SourceHandlerBridge createSourceHandler(BlockEntity host) {
        if (!isLoaded() || !ArsNouveauCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsSourceTransfer()) {
            return null;
        }
        try {
            return BeyondDimensionsApiBridge.createSourceHandler(host);
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
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsFluidTransfer()) {
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
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsFluidTransfer()) {
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
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsFluidTransfer()) {
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
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsEnergyTransfer()) {
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
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsEnergyTransfer()) {
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
        if (!isLoaded() || !SkyLogisticsConfig.allowBeyondDimensionsEnergyTransfer()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.extractEnergy(host, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long manaStored(BlockEntity host) {
        if (!isLoaded() || !BotaniaCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsManaTransfer()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.manaStored(host);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long insertMana(BlockEntity host, long amount, boolean simulate) {
        if (!isLoaded() || !BotaniaCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsManaTransfer()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.insertMana(host, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long extractMana(BlockEntity host, long amount, boolean simulate) {
        if (!isLoaded() || !BotaniaCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsManaTransfer()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.extractMana(host, amount, simulate);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long sourceStored(BlockEntity host) {
        if (!isLoaded() || !ArsNouveauCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsSourceTransfer()) {
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
        if (!isLoaded() || !ArsNouveauCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsSourceTransfer()) {
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
        if (!isLoaded() || !ArsNouveauCompat.isLoaded()
                || !SkyLogisticsConfig.allowBeyondDimensionsSourceTransfer()) {
            return 0L;
        }
        try {
            return BeyondDimensionsApiBridge.extractSource(host, amount, simulate);
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
