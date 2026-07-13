package com.skylogistics.compat.beyonddimensions;

import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.EnergyUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.ItemUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler.TypeBucket;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import java.lang.reflect.Constructor;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

final class BeyondDimensionsApiBridge {
    private static final String CHEMICAL_HANDLER_CLASS =
            "com.wintercogs.beyonddimensions.integration.module.mekanism.storage.ChemicalUnifiedStorageHandler";
    private static final String SOURCE_HANDLER_CLASS =
            "com.wintercogs.beyonddimensions.integration.module.ars.storage.SourceUnifiedStorageHandler";
    private static final String SOURCE_KEY_CLASS =
            "com.wintercogs.beyonddimensions.integration.module.ars.storage.SourceStackKey";

    private BeyondDimensionsApiBridge() {
    }

    static IItemHandler itemHandler(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? EmptyExternalHandlers.Items.INSTANCE : new ItemUnifiedStorageHandler(storage);
    }

    static IFluidHandler fluidHandler(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? EmptyExternalHandlers.Fluids.INSTANCE : new FluidUnifiedStorageHandler(storage);
    }

    static ChemicalHandlerBridge chemicalHandler(BlockEntity host) {
        if (!SkyLogisticsConfig.allowFluidChemicalTransfer()
                || !SkyLogisticsConfig.allowBeyondDimensionsMekanismChemicalTransfer()
                || !MekanismCompat.isLoaded()) {
            return null;
        }
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName(CHEMICAL_HANDLER_CLASS);
            Constructor<?> constructor = type.getConstructor(UnifiedStorage.class);
            return MekanismCompat.wrapChemicalHandler(constructor.newInstance(storage));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    static IEnergyStorage energyHandler(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? EmptyExternalHandlers.Energy.INSTANCE : new EnergyUnifiedStorageHandler(storage);
    }

    static SourceHandlerBridge sourceHandler(BlockEntity host) {
        if (!ArsNouveauCompat.isLoaded()) {
            return null;
        }
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName(SOURCE_HANDLER_CLASS);
            Constructor<?> constructor = type.getConstructor(UnifiedStorage.class);
            return ArsNouveauCompat.wrapSourceHandler(constructor.newInstance(storage));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    static BeyondDimensionsCompat.ItemResource itemResourceInSlot(BlockEntity host, int slot) {
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return BeyondDimensionsCompat.ItemResource.EMPTY;
        }
        IStackKey<?> key = keyInBucket(storage.getBucket(ItemStackKey.ID), slot);
        if (key == null) {
            return BeyondDimensionsCompat.ItemResource.EMPTY;
        }
        return itemResourceByKey(storage, key);
    }

    static int itemTypeCount(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return 0;
        }
        return storage.getBucket(ItemStackKey.ID).map(TypeBucket::size).orElse(0);
    }

    static BeyondDimensionsCompat.ItemResource itemResourceForStack(BlockEntity host, ItemStack stack) {
        UnifiedStorage storage = storage(host);
        if (storage == null || stack.isEmpty()) {
            return BeyondDimensionsCompat.ItemResource.EMPTY;
        }
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        return itemResourceByKey(storage, new ItemStackKey(normalized));
    }

    static BeyondDimensionsCompat.ItemResource itemResourceForTag(BlockEntity host, TagKey<Item> tag) {
        UnifiedStorage storage = storage(host);
        if (storage == null || tag == null) {
            return BeyondDimensionsCompat.ItemResource.EMPTY;
        }
        KeyAmount extracted = storage.extract(tag, Long.MAX_VALUE, true);
        return extracted.isEmpty() ? BeyondDimensionsCompat.ItemResource.EMPTY : itemResourceByKey(storage,
                extracted.key());
    }

    static BeyondDimensionsCompat.FluidResource fluidResourceInTank(BlockEntity host, int tank) {
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return BeyondDimensionsCompat.FluidResource.EMPTY;
        }
        IStackKey<?> key = keyInBucket(storage.getBucket(FluidStackKey.ID), tank);
        if (key == null) {
            return BeyondDimensionsCompat.FluidResource.EMPTY;
        }
        Object outStack = storage.getOutStackByKey(key);
        if (!(outStack instanceof FluidStack stack) || stack.isEmpty()) {
            return BeyondDimensionsCompat.FluidResource.EMPTY;
        }
        long amount = Math.max(0L, storage.getStackByKey(key).amount());
        if (amount <= 0L) {
            return BeyondDimensionsCompat.FluidResource.EMPTY;
        }
        FluidStack copy = stack.copy();
        copy.setAmount((int) Math.min(Integer.MAX_VALUE, amount));
        return new BeyondDimensionsCompat.FluidResource(copy, amount);
    }

    static long insertItem(BlockEntity host, ItemStack stack, long amount, boolean simulate) {
        UnifiedStorage storage = storage(host);
        if (storage == null || stack.isEmpty() || amount <= 0L) {
            return 0L;
        }
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        KeyAmount remainder = storage.insert(new ItemStackKey(normalized), amount, simulate);
        return insertedAmount(amount, remainder);
    }

    static long extractItem(BlockEntity host, ItemStack stack, long amount, boolean simulate) {
        UnifiedStorage storage = storage(host);
        if (storage == null || stack.isEmpty() || amount <= 0L) {
            return 0L;
        }
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        return Math.max(0L, storage.extract(new ItemStackKey(normalized), amount, simulate, false).amount());
    }

    static long insertFluid(BlockEntity host, FluidStack stack, long amount, boolean simulate) {
        UnifiedStorage storage = storage(host);
        if (storage == null || stack.isEmpty() || amount <= 0L) {
            return 0L;
        }
        FluidStack normalized = stack.copy();
        normalized.setAmount(1);
        KeyAmount remainder = storage.insert(new FluidStackKey(normalized), amount, simulate);
        return insertedAmount(amount, remainder);
    }

    static long extractFluid(BlockEntity host, FluidStack stack, long amount, boolean simulate) {
        UnifiedStorage storage = storage(host);
        if (storage == null || stack.isEmpty() || amount <= 0L) {
            return 0L;
        }
        FluidStack normalized = stack.copy();
        normalized.setAmount(1);
        return Math.max(0L, storage.extract(new FluidStackKey(normalized), amount, simulate, false).amount());
    }

    static long energyStored(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? 0L : Math.max(0L, storage.getStackByKey(EnergyStackKey.INSTANCE).amount());
    }

    static long insertEnergy(BlockEntity host, long amount, boolean simulate) {
        return insertLong(host, EnergyStackKey.INSTANCE, amount, simulate);
    }

    static long extractEnergy(BlockEntity host, long amount, boolean simulate) {
        return extractLong(host, EnergyStackKey.INSTANCE, amount, simulate);
    }

    static long sourceStored(BlockEntity host) {
        try {
            return storedAmount(host, sourceStackKey());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    static long insertSource(BlockEntity host, long amount, boolean simulate) {
        try {
            return insertLong(host, sourceStackKey(), amount, simulate);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    static long extractSource(BlockEntity host, long amount, boolean simulate) {
        try {
            return extractLong(host, sourceStackKey(), amount, simulate);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    static void bindOnPlaced(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost host)
                || host.getDimensionNetworkId() >= 0) {
            return;
        }
        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net != null && net.isManager(player)) {
            host.setDimensionNetworkId(net.getId());
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.block_net_bound", net.getId()));
        }
    }

    static void handleBindingUse(Level level, BlockPos pos, Player player) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost host)) {
            return;
        }
        int currentNetId = host.getDimensionNetworkId();
        if (currentNetId < 0) {
            bindFromPlayerNetwork(level, player, host);
        } else {
            unbindFromPlayerNetwork(level, player, host, currentNetId);
        }
    }

    private static void bindFromPlayerNetwork(Level level, Player player,
            BeyondDimensionsCompat.NetworkBoundHost host) {
        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net == null) {
            return;
        }
        if (net.isManager(player)) {
            host.setDimensionNetworkId(net.getId());
            playClick(level, player);
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.block_net_bound", net.getId()));
        } else {
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.no_right_to_bound_block"));
        }
    }

    private static void unbindFromPlayerNetwork(Level level, Player player,
            BeyondDimensionsCompat.NetworkBoundHost host, int currentNetId) {
        DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
        if (net == null) {
            return;
        }
        DimensionsNet currentNet = DimensionsNet.getNetFromId(currentNetId);
        if (net.getId() != currentNetId && currentNet != null) {
            return;
        }
        if (net.isManager(player)) {
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.block_net_unbound", currentNetId));
            host.clearDimensionNetworkId();
            playClick(level, player);
        } else {
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.no_right_to_bound_block"));
        }
    }

    private static void playClick(Level level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(),
                SoundSource.PLAYERS, 0.5F, 1.0F);
    }

    private static UnifiedStorage storage(BlockEntity host) {
        if (!(host instanceof BeyondDimensionsCompat.NetworkBoundHost networkHost)) {
            return null;
        }
        int netId = networkHost.getDimensionNetworkId();
        if (netId < 0) {
            return null;
        }
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        return net == null || net.deleted ? null : net.getUnifiedStorage();
    }

    private static IStackKey<?> keyInBucket(Optional<TypeBucket> bucket, int slot) {
        if (bucket.isEmpty() || slot < 0 || slot >= bucket.get().size()) {
            return null;
        }
        return bucket.get().get(slot);
    }

    private static BeyondDimensionsCompat.ItemResource itemResourceByKey(UnifiedStorage storage, IStackKey<?> key) {
        if (key == null) {
            return BeyondDimensionsCompat.ItemResource.EMPTY;
        }
        Object outStack = storage.getOutStackByKey(key);
        if (!(outStack instanceof ItemStack stack) || stack.isEmpty()) {
            return BeyondDimensionsCompat.ItemResource.EMPTY;
        }
        long amount = Math.max(0L, storage.getStackByKey(key).amount());
        if (amount <= 0L) {
            return BeyondDimensionsCompat.ItemResource.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount((int) Math.min(Integer.MAX_VALUE, amount));
        return new BeyondDimensionsCompat.ItemResource(copy, amount);
    }

    private static IStackKey<?> sourceStackKey() throws ReflectiveOperationException {
        return (IStackKey<?>) Class.forName(SOURCE_KEY_CLASS).getField("INSTANCE").get(null);
    }

    private static long storedAmount(BlockEntity host, IStackKey<?> key) {
        UnifiedStorage storage = storage(host);
        return storage == null ? 0L : Math.max(0L, storage.getStackByKey(key).amount());
    }

    private static long insertLong(BlockEntity host, IStackKey<?> key, long amount, boolean simulate) {
        UnifiedStorage storage = storage(host);
        if (storage == null || amount <= 0L) {
            return 0L;
        }
        KeyAmount remainder = storage.insert(key, amount, simulate);
        return insertedAmount(amount, remainder);
    }

    private static long extractLong(BlockEntity host, IStackKey<?> key, long amount, boolean simulate) {
        UnifiedStorage storage = storage(host);
        if (storage == null || amount <= 0L) {
            return 0L;
        }
        return Math.max(0L, storage.extract(key, amount, simulate, false).amount());
    }

    private static long insertedAmount(long requested, KeyAmount remainder) {
        long leftover = Math.max(0L, remainder.amount());
        return leftover >= requested ? 0L : requested - leftover;
    }
}
