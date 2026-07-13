package com.skylogistics.compat.refinedstorage;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.config.SkyLogisticsConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

public final class RefinedStorageCompat {
    private static final String REFINED_STORAGE = "refinedstorage";
    private static boolean warned;

    private RefinedStorageCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(REFINED_STORAGE);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        if (!isLoaded() || !SkyLogisticsConfig.allowRefinedStorageItemTransfer()) {
            return EmptyExternalHandlers.Items.INSTANCE;
        }
        try {
            return RefinedStorageApiBridge.createItemHandler(host);
        } catch (LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Items.INSTANCE;
        }
    }

    public static IFluidHandler createFluidHandler(BlockEntity host) {
        if (!isLoaded() || !SkyLogisticsConfig.allowRefinedStorageFluidTransfer()) {
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
        try {
            return RefinedStorageApiBridge.createFluidHandler(host);
        } catch (LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    public static ItemResource itemResourceForStack(BlockEntity host, ItemStack stack) {
        if (!isLoaded() || !SkyLogisticsConfig.allowRefinedStorageItemTransfer() || stack.isEmpty()) {
            return ItemResource.EMPTY;
        }
        try {
            return RefinedStorageApiBridge.itemResourceForStack(host, stack);
        } catch (LinkageError error) {
            warn(error);
            return ItemResource.EMPTY;
        }
    }

    public static long insertItem(BlockEntity host, ItemStack stack, long amount, boolean simulate) {
        if (!isLoaded() || !SkyLogisticsConfig.allowRefinedStorageItemTransfer() || stack.isEmpty()
                || amount <= 0L) {
            return 0L;
        }
        try {
            return RefinedStorageApiBridge.insertItem(host, stack, amount, simulate);
        } catch (LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long extractItem(BlockEntity host, ItemStack stack, long amount, boolean simulate) {
        if (!isLoaded() || !SkyLogisticsConfig.allowRefinedStorageItemTransfer() || stack.isEmpty()
                || amount <= 0L) {
            return 0L;
        }
        try {
            return RefinedStorageApiBridge.extractItem(host, stack, amount, simulate);
        } catch (LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static FluidResource fluidResourceForStack(BlockEntity host, FluidStack stack) {
        if (!isLoaded() || !SkyLogisticsConfig.allowRefinedStorageFluidTransfer() || stack.isEmpty()) {
            return FluidResource.EMPTY;
        }
        try {
            return RefinedStorageApiBridge.fluidResourceForStack(host, stack);
        } catch (LinkageError error) {
            warn(error);
            return FluidResource.EMPTY;
        }
    }

    public static long insertFluid(BlockEntity host, FluidStack stack, long amount, boolean simulate) {
        if (!isLoaded() || !SkyLogisticsConfig.allowRefinedStorageFluidTransfer() || stack.isEmpty()
                || amount <= 0L) {
            return 0L;
        }
        try {
            return RefinedStorageApiBridge.insertFluid(host, stack, amount, simulate);
        } catch (LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static long extractFluid(BlockEntity host, FluidStack stack, long amount, boolean simulate) {
        if (!isLoaded() || !SkyLogisticsConfig.allowRefinedStorageFluidTransfer() || stack.isEmpty()
                || amount <= 0L) {
            return 0L;
        }
        try {
            return RefinedStorageApiBridge.extractFluid(host, stack, amount, simulate);
        } catch (LinkageError error) {
            warn(error);
            return 0L;
        }
    }

    public static boolean sameNetwork(BlockEntity first, BlockEntity second) {
        if (first == second) {
            return true;
        }
        if (!isLoaded() || first == null || second == null) {
            return false;
        }
        try {
            return RefinedStorageApiBridge.sameNetwork(first, second);
        } catch (LinkageError error) {
            warn(error);
            return false;
        }
    }

    private static void warn(LinkageError error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn("Refined Storage compat is disabled because the loaded RS API is not compatible.",
                    error);
        }
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
