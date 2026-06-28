package com.skylogistics.compat.beyonddimensions;

import com.skylogistics.compat.EmptyExternalHandlers;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.ItemUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler.TypeBucket;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

final class BeyondDimensionsApiBridge {
    private BeyondDimensionsApiBridge() {
    }

    static IItemHandler createItemHandler(BlockEntity host) {
        return new ItemHandler(host);
    }

    static IFluidHandler createFluidHandler(BlockEntity host) {
        return new FluidHandler(host);
    }

    static BeyondDimensionsCompat.ItemResource itemResourceInSlot(BlockEntity host, int slot) {
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return BeyondDimensionsCompat.ItemResource.EMPTY;
        }
        IStackKey<?> key = keyInBucket(storage, ItemStackKey.ID, slot);
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

    static BeyondDimensionsCompat.FluidResource fluidResourceInTank(BlockEntity host, int tank) {
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return BeyondDimensionsCompat.FluidResource.EMPTY;
        }
        IStackKey<?> key = keyInBucket(storage, FluidStackKey.ID, tank);
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
        KeyAmount extracted = storage.extract(new ItemStackKey(normalized), amount, simulate, false);
        return Math.max(0L, extracted.amount());
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
        KeyAmount extracted = storage.extract(new FluidStackKey(normalized), amount, simulate, false);
        return Math.max(0L, extracted.amount());
    }

    static void bindOnPlaced(Level level, BlockPos pos, LivingEntity placer) {
        if (!(placer instanceof Player player)) {
            return;
        }
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

    private static IItemHandler itemHandler(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? EmptyExternalHandlers.Items.INSTANCE : new ItemUnifiedStorageHandler(storage);
    }

    private static IFluidHandler fluidHandler(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? EmptyExternalHandlers.Fluids.INSTANCE : new FluidUnifiedStorageHandler(storage);
    }

    private static IStackKey<?> keyInBucket(UnifiedStorage storage, ResourceLocation typeId, int slot) {
        Optional<TypeBucket> bucket = storage.getBucket(typeId);
        if (bucket.isEmpty() || slot < 0 || slot >= bucket.get().size()) {
            return null;
        }
        return bucket.get().get(slot);
    }

    private static long insertedAmount(long requested, KeyAmount remainder) {
        long leftover = Math.max(0L, remainder.amount());
        return leftover >= requested ? 0L : requested - leftover;
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
}
