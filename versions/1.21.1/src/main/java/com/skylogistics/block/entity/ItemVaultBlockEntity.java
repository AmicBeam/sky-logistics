package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.menu.ItemVaultMenu;
import com.skylogistics.network.ItemVaultSnapshotPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.storage.ItemStackKey;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.items.IItemHandler;

public class ItemVaultBlockEntity extends BlockEntity {
    private static final int SNAPSHOT_ENTRY_LIMIT = 256;

    private final List<ItemStack> items = new ArrayList<>();
    private final List<Long> amounts = new ArrayList<>();
    private final LinkedHashMap<ItemStackKey, Long> clientStored = new LinkedHashMap<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final IItemHandler itemHandler = new VaultItemHandler();
    private int typeLimit = 1;
    private int clientUsedTypes = -1;
    private long clientTotalAmount = -1L;
    private long syncVersion;
    private long clientSnapshotVersion;

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

    public long getSyncVersion() {
        return syncVersion;
    }

    public IItemHandler itemHandler() {
        return itemHandler;
    }

    public List<StoredItem> getStoredItems(int limit) {
        List<StoredItem> result = new ArrayList<>();
        int added = 0;
        for (Map.Entry<ItemStackKey, Long> entry : displayContents().entrySet()) {
            if (added++ >= limit) {
                break;
            }
            result.add(new StoredItem(entry.getKey().toStack(1), entry.getValue()));
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
            ItemStack stack = templateInSlot(slot);
            long storedAmount = amountInSlot(slot);
            if (stack.isEmpty() || storedAmount <= 0 || !StackData.sameItemAndComponents(stack, template)) {
                continue;
            }
            int toExtract = (int) Math.min(remaining, storedAmount);
            if (result.isEmpty()) {
                result = stack.copy();
                result.setCount(0);
            }
            result.grow(toExtract);
            decreaseStored(slot, toExtract);
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
        ItemStack source = templateInSlot(sourceSlot);
        long storedAmount = amountInSlot(sourceSlot);
        if (source.isEmpty() || storedAmount <= 0) {
            return 0L;
        }
        long requested = Math.min(maxAmount, storedAmount);
        long moved = target.insertStored(source, requested, false);
        if (moved <= 0) {
            return 0L;
        }
        decreaseStored(sourceSlot, moved);
        markContentsChanged();
        return moved;
    }

    public StoredItem storedItemInSlot(int slot) {
        if (slot < 0 || slot >= getTypeLimit()) {
            return new StoredItem(ItemStack.EMPTY, 0L);
        }
        ItemStack stack = templateInSlot(slot);
        long amount = amountInSlot(slot);
        if (stack.isEmpty() || amount <= 0L) {
            return new StoredItem(ItemStack.EMPTY, 0L);
        }
        ItemStack copy = stack.copy();
        copy.setCount((int) Math.min(Integer.MAX_VALUE, amount));
        return new StoredItem(copy, amount);
    }

    public long insertStoredItem(ItemStack template, long amount, boolean simulate) {
        return insertStored(template, amount, simulate);
    }

    public long extractStoredItem(int slot, long amount, boolean simulate) {
        if (slot < 0 || slot >= getTypeLimit() || amount <= 0L) {
            return 0L;
        }
        ItemStack existing = templateInSlot(slot);
        long storedAmount = amountInSlot(slot);
        if (existing.isEmpty() || storedAmount <= 0L) {
            return 0L;
        }
        long extracted = Math.min(amount, storedAmount);
        if (extracted > 0L && !simulate) {
            decreaseStored(slot, extracted);
            markContentsChanged();
        }
        return extracted;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveMetadata(tag);
        ListTag itemTags = new ListTag();
        int slots = Math.min(items.size(), getConfiguredMaxTypes());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = templateInSlot(slot);
            long amount = amountInSlot(slot);
            if (stack.isEmpty() || amount <= 0) {
                continue;
            }
            ItemStack savedStack = stack.copy();
            savedStack.setCount(1);
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", slot);
            entry.putLong("Amount", amount);
            entry.put("Stack", StackData.saveItem(savedStack, registries));
            itemTags.add(entry);
        }
        if (!itemTags.isEmpty()) {
            tag.put("Items", itemTags);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
                ItemStack stack = StackData.loadItem(entry.getCompound("Stack"), registries);
                long amount = entry.contains("Amount", Tag.TAG_LONG) ? entry.getLong("Amount") : stack.getCount();
                setStored(slot, stack, amount);
            }
            compactDuplicateStoredItems();
        }
        if (tag.contains("UsedTypes")) {
            clientUsedTypes = Math.max(0, tag.getInt("UsedTypes"));
        }
        if (tag.contains("TotalAmount")) {
            clientTotalAmount = Math.max(0L, tag.getLong("TotalAmount"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveMetadata(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void saveMetadata(CompoundTag tag) {
        tag.putInt("TypeLimit", typeLimit);
        tag.putInt("UsedTypes", getUsedTypes());
        tag.putLong("TotalAmount", getTotalAmount());
    }

    private void markContentsChanged() {
        syncVersion++;
        setChanged();
        if (level instanceof ServerLevel) {
            syncToViewingPlayers();
        }
    }

    private void markMetadataChanged() {
        syncVersion++;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            syncToViewingPlayers();
        }
    }

    private void syncToViewingPlayers() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (viewers.isEmpty()) {
            return;
        }
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
            menu.noteVaultSnapshotSynced(syncVersion);
        }
    }

    private ItemStack insertStack(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            ItemStack existing = templateInSlot(slot);
            if (!existing.isEmpty() && StackData.sameItemAndComponents(existing, remainder)) {
                remainder = insertIntoSlot(slot, remainder, simulate);
            }
        }
        for (int slot = 0; slot < slots && !remainder.isEmpty(); slot++) {
            if (templateInSlot(slot).isEmpty()) {
                remainder = insertIntoSlot(slot, remainder, simulate);
            }
        }
        return remainder;
    }

    private long insertStored(ItemStack template, long amount, boolean simulate) {
        if (template.isEmpty() || amount <= 0) {
            return 0L;
        }
        ItemStack normalized = template.copy();
        normalized.setCount(1);
        long remaining = amount;
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack existing = templateInSlot(slot);
            if (!existing.isEmpty() && StackData.sameItemAndComponents(existing, normalized)) {
                remaining -= insertAmountIntoSlot(slot, normalized, remaining, simulate);
            }
        }
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            if (templateInSlot(slot).isEmpty()) {
                remaining -= insertAmountIntoSlot(slot, normalized, remaining, simulate);
            }
        }
        long inserted = amount - remaining;
        if (inserted > 0 && !simulate) {
            markContentsChanged();
        }
        return inserted;
    }

    private ItemStack insertIntoSlot(int slot, ItemStack stack, boolean simulate) {
        if (slot < 0 || slot >= getTypeLimit() || stack.isEmpty()) {
            return stack;
        }
        ItemStack remainder = stack.copy();
        long accepted = insertAmountIntoSlot(slot, stack, stack.getCount(), simulate);
        if (accepted <= 0) {
            return stack;
        }
        if (!simulate) {
            markContentsChanged();
        }
        remainder.shrink((int) accepted);
        return remainder;
    }

    private long insertAmountIntoSlot(int slot, ItemStack template, long amount, boolean simulate) {
        if (slot < 0 || slot >= getTypeLimit() || template.isEmpty() || amount <= 0) {
            return 0L;
        }
        ItemStack existing = templateInSlot(slot);
        long current = amountInSlot(slot);
        if (!existing.isEmpty() && !StackData.sameItemAndComponents(existing, template)) {
            return 0L;
        }
        long inserted = amount;
        if (!existing.isEmpty() && current > 0L && Long.MAX_VALUE - current < amount) {
            inserted = Long.MAX_VALUE - current;
        }
        if (inserted <= 0L) {
            return 0L;
        }
        if (!simulate) {
            if (existing.isEmpty() || current <= 0) {
                ItemStack stored = template.copy();
                stored.setCount(1);
                items.set(slot, stored);
                amounts.set(slot, inserted);
            } else {
                amounts.set(slot, current + inserted);
            }
        }
        return inserted;
    }

    private ItemStack stackInSlot(int slot) {
        ItemStack template = templateInSlot(slot);
        long amount = amountInSlot(slot);
        if (template.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = template.copy();
        stack.setCount((int) Math.min(Integer.MAX_VALUE, amount));
        return stack;
    }

    private ItemStack templateInSlot(int slot) {
        ensureItemCapacity();
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    private long amountInSlot(int slot) {
        ensureItemCapacity();
        return slot >= 0 && slot < amounts.size() ? Math.max(0L, amounts.get(slot)) : 0L;
    }

    private int getSlotLimit(int slot) {
        return slot >= 0 && slot < getTypeLimit() ? Integer.MAX_VALUE : 0;
    }

    private LinkedHashMap<ItemStackKey, Long> displayContents() {
        if (level != null && level.isClientSide) {
            return clientStored;
        }
        LinkedHashMap<ItemStackKey, Long> contents = new LinkedHashMap<>();
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = templateInSlot(slot);
            long amount = amountInSlot(slot);
            if (stack.isEmpty() || amount <= 0) {
                continue;
            }
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            ItemStackKey key = ItemStackKey.of(normalized);
            contents.put(key, saturatingAdd(contents.getOrDefault(key, 0L), amount));
        }
        return contents;
    }

    private int occupiedSlots() {
        int occupied = 0;
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots; slot++) {
            if (!templateInSlot(slot).isEmpty() && amountInSlot(slot) > 0) {
                occupied++;
            }
        }
        return occupied;
    }

    private long totalAmount() {
        long total = 0L;
        int slots = getTypeLimit();
        for (int slot = 0; slot < slots; slot++) {
            total = saturatingAdd(total, amountInSlot(slot));
        }
        return total;
    }

    private void ensureItemCapacity() {
        int max = Math.max(1, SkyLogisticsConfig.maxVaultTypes());
        while (items.size() < max) {
            items.add(ItemStack.EMPTY);
            amounts.add(0L);
        }
        while (items.size() > max) {
            items.remove(items.size() - 1);
            amounts.remove(amounts.size() - 1);
        }
        while (amounts.size() < items.size()) {
            amounts.add(0L);
        }
        while (amounts.size() > items.size()) {
            amounts.remove(amounts.size() - 1);
        }
        typeLimit = Math.max(1, Math.min(typeLimit, max));
    }

    private void clearItems() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
            amounts.set(i, 0L);
        }
    }

    private void compactDuplicateStoredItems() {
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = templateInSlot(slot);
            if (stack.isEmpty() || amountInSlot(slot) <= 0) {
                continue;
            }
            for (int otherSlot = slot + 1; otherSlot < items.size(); otherSlot++) {
                ItemStack other = templateInSlot(otherSlot);
                long otherAmount = amountInSlot(otherSlot);
                if (other.isEmpty() || otherAmount <= 0 || !StackData.sameItemAndComponents(stack, other)) {
                    continue;
                }
                amounts.set(slot, saturatingAdd(amountInSlot(slot), otherAmount));
                items.set(otherSlot, ItemStack.EMPTY);
                amounts.set(otherSlot, 0L);
            }
        }
    }

    private void setStored(int slot, ItemStack stack, long amount) {
        if (slot < 0 || slot >= items.size() || stack.isEmpty() || amount <= 0) {
            if (slot >= 0 && slot < items.size()) {
                items.set(slot, ItemStack.EMPTY);
                amounts.set(slot, 0L);
            }
            return;
        }
        ItemStack stored = stack.copy();
        stored.setCount(1);
        items.set(slot, stored);
        amounts.set(slot, amount);
    }

    private void decreaseStored(int slot, long amount) {
        if (slot < 0 || slot >= items.size() || amount <= 0) {
            return;
        }
        long remaining = amountInSlot(slot) - Math.min(amountInSlot(slot), amount);
        if (remaining <= 0) {
            items.set(slot, ItemStack.EMPTY);
            amounts.set(slot, 0L);
        } else {
            amounts.set(slot, remaining);
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
            ItemStack existing = templateInSlot(slot);
            long storedAmount = amountInSlot(slot);
            if (existing.isEmpty() || storedAmount <= 0) {
                return ItemStack.EMPTY;
            }
            int extractedAmount = (int) Math.min(amount, storedAmount);
            ItemStack extracted = existing.copy();
            extracted.setCount(extractedAmount);
            if (!simulate) {
                decreaseStored(slot, extractedAmount);
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
            ItemStack existing = templateInSlot(slot);
            return existing.isEmpty() || StackData.sameItemAndComponents(existing, stack);
        }
    }
}
