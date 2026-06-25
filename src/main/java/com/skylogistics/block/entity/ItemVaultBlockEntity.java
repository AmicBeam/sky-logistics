package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.menu.ItemVaultMenu;
import com.skylogistics.network.ItemVaultSnapshotPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.storage.ItemStackKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

public class ItemVaultBlockEntity extends BlockEntity {
    private static final int SNAPSHOT_ENTRY_LIMIT = 256;
    private static final int VIEWER_SYNC_INTERVAL_TICKS = 10;

    private final List<ItemStack> items = new ArrayList<>();
    private final LinkedHashMap<ItemStackKey, Long> clientStored = new LinkedHashMap<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final IItemHandler itemHandler = new VaultItemHandler();
    private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> itemHandler);
    private int typeLimit = 1;
    private int clientUsedTypes = -1;
    private long clientTotalAmount = -1L;
    private long clientSnapshotVersion;
    private long lastViewerSyncTime = Long.MIN_VALUE;

    public ItemVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_VAULT.get(), pos, state);
        ensureItemCapacity();
    }

    public int getTypeLimit() {
        ensureItemCapacity();
        return Math.min(typeLimit, SkyLogisticsConfig.maxVaultTypes());
    }

    public int getConfiguredMaxTypes() {
        return SkyLogisticsConfig.maxVaultTypes();
    }

    public boolean increaseTypeLimit() {
        int maxTypes = getConfiguredMaxTypes();
        if (typeLimit >= maxTypes) {
            return false;
        }
        typeLimit++;
        ensureItemCapacity();
        markMetadataChanged();
        return true;
    }

    public int getUsedTypes() {
        if (level != null && level.isClientSide && clientUsedTypes >= 0) {
            return clientUsedTypes;
        }
        return occupiedSlots();
    }

    public long getTotalAmount() {
        if (level != null && level.isClientSide && clientTotalAmount >= 0) {
            return clientTotalAmount;
        }
        return totalAmount();
    }

    public long getClientSnapshotVersion() {
        return clientSnapshotVersion;
    }

    public List<StoredItem> getStoredItems(int limit) {
        List<StoredItem> result = new ArrayList<>();
        int added = 0;
        for (Map.Entry<ItemStackKey, Long> entry : displayContents().entrySet()) {
            if (added++ >= limit) {
                break;
            }
            result.add(new StoredItem(entry.getKey().toStack((int) Math.min(Integer.MAX_VALUE, entry.getValue())),
                    entry.getValue()));
        }
        return result;
    }

    public boolean insertFromPlayer(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack remainder = insertStack(stack, false);
        int inserted = stack.getCount() - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }
        stack.setCount(remainder.getCount());
        return true;
    }

    public ItemStack extractFirstForPlayer(int amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack extracted = itemHandler.extractItem(slot, amount, false);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack extractForPlayer(ItemStack template, int amount) {
        if (template.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int remaining = amount;
        ItemStack result = ItemStack.EMPTY;
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack stack = stackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, template)) {
                continue;
            }
            int toExtract = Math.min(remaining, stack.getCount());
            if (result.isEmpty()) {
                result = stack.copy();
                result.setCount(0);
            }
            result.grow(toExtract);
            stack.shrink(toExtract);
            if (stack.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
            }
            remaining -= toExtract;
        }
        if (!result.isEmpty()) {
            markContentsChanged();
        }
        return result;
    }

    public long transferTo(ItemVaultBlockEntity target, int sourceSlot, long maxAmount) {
        if (target == this || sourceSlot < 0 || sourceSlot >= getTypeLimit() || maxAmount <= 0) {
            return 0L;
        }
        ItemStack source = stackInSlot(sourceSlot);
        if (source.isEmpty()) {
            return 0L;
        }
        ItemStack moving = source.copy();
        moving.setCount((int) Math.min(Math.min(Integer.MAX_VALUE, maxAmount), source.getCount()));
        ItemStack remainder = target.insertStack(moving, false);
        int moved = moving.getCount() - remainder.getCount();
        if (moved <= 0) {
            return 0L;
        }
        source.shrink(moved);
        if (source.isEmpty()) {
            items.set(sourceSlot, ItemStack.EMPTY);
        }
        markContentsChanged();
        return moved;
    }

    public void syncTo(ServerPlayer player) {
        List<ItemVaultSnapshotPacket.Entry> entries = new ArrayList<>();
        for (StoredItem item : getStoredItems(SNAPSHOT_ENTRY_LIMIT)) {
            entries.add(new ItemVaultSnapshotPacket.Entry(item.stack(), item.amount()));
        }
        ModNetworking.sendToPlayer(player, new ItemVaultSnapshotPacket(worldPosition, getTypeLimit(), getUsedTypes(),
                getTotalAmount(), entries));
    }

    public void syncToPlayerIfPresent(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            syncTo(serverPlayer);
        }
    }

    public void addViewer(Player player) {
        if (player instanceof ServerPlayer) {
            viewers.add(player.getUUID());
        }
    }

    public void removeViewer(Player player) {
        viewers.remove(player.getUUID());
    }

    public void applyClientSnapshot(int typeLimit, int usedTypes, long totalAmount,
            List<ItemVaultSnapshotPacket.Entry> entries) {
        this.typeLimit = Math.max(1, typeLimit);
        this.clientUsedTypes = Math.max(0, usedTypes);
        this.clientTotalAmount = Math.max(0L, totalAmount);
        clientSnapshotVersion++;
        clientStored.clear();
        for (ItemVaultSnapshotPacket.Entry entry : entries) {
            ItemStack stack = entry.stack();
            if (entry.amount() <= 0 || stack.isEmpty()) {
                continue;
            }
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            clientStored.put(ItemStackKey.of(normalized), entry.amount());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ensureItemCapacity();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveMetadata(tag);
        ListTag itemTags = new ListTag();
        int slots = Math.min(items.size(), getConfiguredMaxTypes());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.put("Stack", stack.save(new CompoundTag()));
            itemTags.add(entry);
        }
        if (!itemTags.isEmpty()) {
            tag.put("Items", itemTags);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        typeLimit = tag.contains("TypeLimit") ? Math.max(1, tag.getInt("TypeLimit")) : 1;
        ensureItemCapacity();
        clearItems();
        if (tag.contains("Items", Tag.TAG_LIST)) {
            ListTag itemTags = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < itemTags.size(); i++) {
                CompoundTag entry = itemTags.getCompound(i);
                int slot = entry.getInt("Slot");
                if (slot < 0 || slot >= items.size()) {
                    continue;
                }
                ItemStack stack = ItemStack.of(entry.getCompound("Stack"));
                items.set(slot, stack);
            }
        }
        if (tag.contains("UsedTypes")) {
            clientUsedTypes = Math.max(0, tag.getInt("UsedTypes"));
        }
        if (tag.contains("TotalAmount")) {
            clientTotalAmount = Math.max(0L, tag.getLong("TotalAmount"));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveMetadata(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, net.minecraft.core.Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
    }

    private void saveMetadata(CompoundTag tag) {
        tag.putInt("TypeLimit", typeLimit);
        tag.putInt("UsedTypes", getUsedTypes());
        tag.putLong("TotalAmount", getTotalAmount());
    }

    private void markContentsChanged() {
        setChanged();
        if (level instanceof ServerLevel) {
            syncToViewingPlayers(false);
        }
    }

    private void markMetadataChanged() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            syncToViewingPlayers(true);
        }
    }

    private void syncToViewingPlayers(boolean immediate) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (viewers.isEmpty()) {
            return;
        }
        long gameTime = serverLevel.getGameTime();
        if (!immediate && gameTime - lastViewerSyncTime < VIEWER_SYNC_INTERVAL_TICKS) {
            return;
        }
        boolean synced = false;
        Iterator<UUID> iterator = viewers.iterator();
        while (iterator.hasNext()) {
            UUID viewer = iterator.next();
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(viewer);
            if (player == null || !(player.containerMenu instanceof ItemVaultMenu menu)
                    || !menu.getPos().equals(worldPosition)) {
                iterator.remove();
                continue;
            }
            syncTo(player);
            synced = true;
        }
        if (synced) {
            lastViewerSyncTime = gameTime;
        }
    }

    private ItemStack insertStack(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            ItemStack existing = stackInSlot(slot);
            if (!existing.isEmpty() && ItemHandlerHelper.canItemStacksStack(existing, remainder)) {
                remainder = insertIntoSlot(slot, remainder, simulate);
            }
        }
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            if (stackInSlot(slot).isEmpty()) {
                remainder = insertIntoSlot(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    private ItemStack insertIntoSlot(int slot, ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= getTypeLimit() || stack.isEmpty()) {
            return stack;
        }
        ItemStack existing = stackInSlot(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty()) {
            if (!ItemHandlerHelper.canItemStacksStack(existing, stack)) {
                return stack;
            }
            limit -= existing.getCount();
        }
        if (limit <= 0) {
            return stack;
        }
        int accepted = Math.min(limit, stack.getCount());
        if (!simulate) {
            if (existing.isEmpty()) {
                ItemStack inserted = stack.copy();
                inserted.setCount(accepted);
                items.set(slot, inserted);
            } else {
                existing.grow(accepted);
            }
            markContentsChanged();
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(accepted);
        return remainder;
    }

    private ItemStack stackInSlot(int slot) {
        ensureItemCapacity();
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    private int getSlotLimit(int slot) {
        return slot >= 0 && slot < getTypeLimit() ? 64 : 0;
    }

    private LinkedHashMap<ItemStackKey, Long> displayContents() {
        if (level != null && level.isClientSide) {
            return clientStored;
        }
        LinkedHashMap<ItemStackKey, Long> contents = new LinkedHashMap<>();
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = stackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            ItemStackKey key = ItemStackKey.of(normalized);
            contents.put(key, saturatingAdd(contents.getOrDefault(key, 0L), stack.getCount()));
        }
        return contents;
    }

    private int occupiedSlots() {
        int occupied = 0;
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots; slot++) {
            if (!stackInSlot(slot).isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    private long totalAmount() {
        long total = 0L;
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = stackInSlot(slot);
            if (!stack.isEmpty()) {
                total = saturatingAdd(total, stack.getCount());
            }
        }
        return total;
    }

    private void ensureItemCapacity() {
        int max = Math.max(1, SkyLogisticsConfig.maxVaultTypes());
        while (items.size() < max) {
            items.add(ItemStack.EMPTY);
        }
        while (items.size() > max) {
            items.remove(items.size() - 1);
        }
        typeLimit = Math.max(1, Math.min(typeLimit, max));
    }

    private void clearItems() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public record StoredItem(ItemStack stack, long amount) {
    }

    private final class VaultItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return getTypeLimit();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= getTypeLimit()) {
                return ItemStack.EMPTY;
            }
            return stackInSlot(slot).copy();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return insertIntoSlot(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= getTypeLimit() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = stackInSlot(slot);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int extractedAmount = Math.min(amount, existing.getCount());
            ItemStack extracted = existing.copy();
            extracted.setCount(extractedAmount);
            if (!simulate) {
                existing.shrink(extractedAmount);
                if (existing.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
                }
                markContentsChanged();
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return ItemVaultBlockEntity.this.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= getTypeLimit() || stack.isEmpty()) {
                return false;
            }
            ItemStack existing = stackInSlot(slot);
            return existing.isEmpty() || ItemHandlerHelper.canItemStacksStack(existing, stack);
        }
    }
}
