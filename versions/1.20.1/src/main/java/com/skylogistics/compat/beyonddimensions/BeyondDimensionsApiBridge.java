package com.skylogistics.compat.beyonddimensions;

import com.skylogistics.compat.EmptyExternalHandlers;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
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
import com.wintercogs.beyonddimensions.api.util.CapCtx;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.integration.module.ars.storage.SourceStackKey;
import com.wintercogs.beyonddimensions.integration.module.ars.storage.SourceUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.integration.module.botania.storage.ManaStackKey;
import com.wintercogs.beyonddimensions.integration.module.mekanism.storage.GasUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.integration.module.mekanism.storage.InfusionUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.integration.module.mekanism.storage.PigmentUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.integration.module.mekanism.storage.SlurryUnifiedStorageHandler;
import java.lang.reflect.Constructor;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    static ChemicalHandlerBridge createChemicalHandler(BlockEntity host) {
        return new ChemicalHandler(host);
    }

    static IEnergyStorage createEnergyHandler(BlockEntity host) {
        return new EnergyHandler(host);
    }

    static ManaHandlerBridge createManaHandler(BlockEntity host) {
        return new ManaHandler(host);
    }

    static SourceHandlerBridge createSourceHandler(BlockEntity host) {
        return new SourceHandler(host);
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

    static long energyStored(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? 0L : Math.max(0L, storage.getStackByKey(EnergyStackKey.INSTANCE).amount());
    }

    static long insertEnergy(BlockEntity host, long amount, boolean simulate) {
        UnifiedStorage storage = storage(host);
        if (storage == null || amount <= 0L) {
            return 0L;
        }
        KeyAmount remainder = storage.insert(EnergyStackKey.INSTANCE, amount, simulate);
        return insertedAmount(amount, remainder);
    }

    static long extractEnergy(BlockEntity host, long amount, boolean simulate) {
        UnifiedStorage storage = storage(host);
        if (storage == null || amount <= 0L) {
            return 0L;
        }
        KeyAmount extracted = storage.extract(EnergyStackKey.INSTANCE, amount, simulate, false);
        return Math.max(0L, extracted.amount());
    }

    static long manaStored(BlockEntity host) {
        return storedAmount(host, ManaStackKey.INSTANCE);
    }

    static long insertMana(BlockEntity host, long amount, boolean simulate) {
        return insertLong(host, ManaStackKey.INSTANCE, amount, simulate);
    }

    static long extractMana(BlockEntity host, long amount, boolean simulate) {
        return extractLong(host, ManaStackKey.INSTANCE, amount, simulate);
    }

    static long sourceStored(BlockEntity host) {
        return storedAmount(host, SourceStackKey.INSTANCE);
    }

    static long insertSource(BlockEntity host, long amount, boolean simulate) {
        return insertLong(host, SourceStackKey.INSTANCE, amount, simulate);
    }

    static long extractSource(BlockEntity host, long amount, boolean simulate) {
        return extractLong(host, SourceStackKey.INSTANCE, amount, simulate);
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
        if (!SkyLogisticsConfig.allowBeyondDimensionsItemTransfer()) {
            return EmptyExternalHandlers.Items.INSTANCE;
        }
        UnifiedStorage storage = storage(host);
        return storage == null ? EmptyExternalHandlers.Items.INSTANCE : new ItemUnifiedStorageHandler(storage);
    }

    private static IFluidHandler fluidHandler(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? EmptyExternalHandlers.Fluids.INSTANCE : new FluidUnifiedStorageHandler(storage);
    }

    private static ChemicalHandlerBridge chemicalHandler(BlockEntity host) {
        if (!SkyLogisticsConfig.allowFluidChemicalTransfer()
                || !SkyLogisticsConfig.allowBeyondDimensionsMekanismChemicalTransfer()
                || !MekanismCompat.isLoaded()) {
            return null;
        }
        UnifiedStorage storage = storage(host);
        return storage == null ? null : MekanismCompat.wrapChemicalHandlers(
                new GasUnifiedStorageHandler(storage),
                new InfusionUnifiedStorageHandler(storage),
                new PigmentUnifiedStorageHandler(storage),
                new SlurryUnifiedStorageHandler(storage));
    }

    private static IEnergyStorage energyHandler(BlockEntity host) {
        UnifiedStorage storage = storage(host);
        return storage == null ? EmptyExternalHandlers.Energy.INSTANCE : new EnergyUnifiedStorageHandler(storage);
    }

    private static ManaHandlerBridge manaHandler(BlockEntity host) {
        if (!BotaniaCompat.isLoaded() || host.getLevel() == null) {
            return null;
        }
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName(
                    "com.wintercogs.beyonddimensions.integration.module.botania.storage.ManaUnifiedStorageHandler");
            Constructor<?> constructor = type.getConstructor(UnifiedStorage.class, CapCtx.class);
            Object handler = constructor.newInstance(storage, new CapCtx(host.getLevel(), host.getBlockPos(), host));
            return BotaniaCompat.wrapManaHandler(handler, handler);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static SourceHandlerBridge sourceHandler(BlockEntity host) {
        if (!ArsNouveauCompat.isLoaded()) {
            return null;
        }
        UnifiedStorage storage = storage(host);
        if (storage == null) {
            return null;
        }
        try {
            return ArsNouveauCompat.wrapSourceHandler(new SourceUnifiedStorageHandler(storage));
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static IStackKey<?> keyInBucket(UnifiedStorage storage, ResourceLocation typeId, int slot) {
        Optional<TypeBucket> bucket = storage.getBucket(typeId);
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
        KeyAmount extracted = storage.extract(key, amount, simulate, false);
        return Math.max(0L, extracted.amount());
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

    private record ManaHandler(BlockEntity host) implements ManaHandlerBridge {
        @Override
        public int getCurrentMana() {
            ManaHandlerBridge handler = manaHandler(host);
            return handler == null ? 0 : handler.getCurrentMana();
        }

        @Override
        public int getMaxMana() {
            ManaHandlerBridge handler = manaHandler(host);
            return handler == null ? 0 : handler.getMaxMana();
        }

        @Override
        public boolean canExtract() {
            ManaHandlerBridge handler = manaHandler(host);
            return handler != null && handler.canExtract();
        }

        @Override
        public boolean canReceive() {
            ManaHandlerBridge handler = manaHandler(host);
            return handler != null && handler.canReceive();
        }

        @Override
        public int extractMana(int amount, boolean simulate) {
            ManaHandlerBridge handler = manaHandler(host);
            return handler == null ? 0 : handler.extractMana(amount, simulate);
        }

        @Override
        public int insertMana(int amount, boolean simulate) {
            ManaHandlerBridge handler = manaHandler(host);
            return handler == null ? 0 : handler.insertMana(amount, simulate);
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
