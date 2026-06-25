package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.menu.ItemVaultMenu;
import com.skylogistics.network.ItemVaultSnapshotPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.storage.ItemStackKey;
import com.skylogistics.storage.VaultStorage;
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

public class ItemVaultBlockEntity extends BlockEntity {
    private static final int SNAPSHOT_ENTRY_LIMIT = 256;
    private static final int VIEWER_SYNC_INTERVAL_TICKS = 10;

    private final LinkedHashMap<ItemStackKey, Long> stored = new LinkedHashMap<>();
    private final List<ItemStackKey> contentIndex = new ArrayList<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final IItemHandler itemHandler = new VaultItemHandler();
    private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> itemHandler);
    private LinkedHashMap<ItemStackKey, Long> indexedContents;
    private int indexedContentSize = -1;
    private UUID vaultId = UUID.randomUUID();
    private int typeLimit = 1;
    private int clientUsedTypes = -1;
    private long clientTotalAmount = -1L;
    private long cachedServerTotalAmount = -1L;
    private long clientSnapshotVersion;
    private long lastViewerSyncTime = Long.MIN_VALUE;

    public ItemVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_VAULT.get(), pos, state);
    }

    public int getTypeLimit() {
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
        markMetadataChanged();
        return true;
    }

    public int getUsedTypes() {
        if (level != null && level.isClientSide && clientUsedTypes >= 0) {
            return clientUsedTypes;
        }
        return contents().size();
    }

    public long getTotalAmount() {
        if (level != null && level.isClientSide && clientTotalAmount >= 0) {
            return clientTotalAmount;
        }
        return cachedTotalAmount();
    }

    public long getClientSnapshotVersion() {
        return clientSnapshotVersion;
    }

    public List<StoredItem> getStoredItems(int limit) {
        List<StoredItem> result = new ArrayList<>();
        int added = 0;
        for (Map.Entry<ItemStackKey, Long> entry : contents().entrySet()) {
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
        ItemStack remainder = itemHandler.insertItem(0, stack, false);
        int inserted = stack.getCount() - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }
        stack.setCount(remainder.getCount());
        return true;
    }

    public ItemStack extractFirstForPlayer(int amount) {
        return itemHandler.extractItem(0, amount, false);
    }

    public long transferTo(ItemVaultBlockEntity target, int sourceSlot, long maxAmount) {
        if (target == this || vaultId.equals(target.vaultId) || sourceSlot < 0 || sourceSlot >= getTypeLimit()
                || maxAmount <= 0) {
            return 0L;
        }
        Map.Entry<ItemStackKey, Long> entry = entryAt(sourceSlot);
        if (entry == null) {
            return 0L;
        }
        long moved = Math.min(entry.getValue(), maxAmount);
        if (moved <= 0 || !target.canReceiveOrVoid(entry.getKey())) {
            return 0L;
        }
        target.receiveOrVoid(entry.getKey(), moved);
        removeStored(entry.getKey(), moved);
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
        invalidateSummaryCache();
        invalidateContentIndex();
        stored.clear();
        for (ItemVaultSnapshotPacket.Entry entry : entries) {
            ItemStack stack = entry.stack();
            if (entry.amount() <= 0 || stack.isEmpty()) {
                continue;
            }
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            stored.put(ItemStackKey.of(normalized), entry.amount());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveMetadata(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("VaultId")) {
            vaultId = tag.getUUID("VaultId");
        }
        typeLimit = Math.max(1, tag.getInt("TypeLimit"));
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

    private LinkedHashMap<ItemStackKey, Long> contents() {
        if (level instanceof ServerLevel serverLevel) {
            return VaultStorage.get(serverLevel, vaultId).items(vaultId);
        }
        return stored;
    }

    private void saveMetadata(CompoundTag tag) {
        tag.putUUID("VaultId", vaultId);
        tag.putInt("TypeLimit", typeLimit);
        tag.putInt("UsedTypes", contents().size());
        tag.putLong("TotalAmount", cachedTotalAmount());
    }

    private void markContentsChanged() {
        invalidateSummaryCache();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            VaultStorage.get(serverLevel, vaultId).setDirty();
            syncToViewingPlayers(false);
        }
    }

    private void markMetadataChanged() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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

    private Map.Entry<ItemStackKey, Long> entryAt(int slot) {
        if (slot < 0) {
            return null;
        }
        LinkedHashMap<ItemStackKey, Long> contents = contents();
        if (slot >= contents.size()) {
            return null;
        }
        List<ItemStackKey> index = contentIndex(contents);
        ItemStackKey key = index.get(slot);
        Long amount = contents.get(key);
        if (amount == null || amount <= 0) {
            invalidateContentIndex();
            index = contentIndex(contents);
            if (slot >= index.size()) {
                return null;
            }
            key = index.get(slot);
            amount = contents.get(key);
        }
        return amount == null || amount <= 0 ? null : Map.entry(key, amount);
    }

    private List<ItemStackKey> contentIndex(LinkedHashMap<ItemStackKey, Long> contents) {
        if (indexedContents != contents || indexedContentSize != contents.size()) {
            contentIndex.clear();
            contentIndex.addAll(contents.keySet());
            indexedContents = contents;
            indexedContentSize = contents.size();
        }
        return contentIndex;
    }

    private void invalidateContentIndex() {
        indexedContents = null;
        indexedContentSize = -1;
        contentIndex.clear();
    }

    private boolean canReceiveOrVoid(ItemStackKey key) {
        LinkedHashMap<ItemStackKey, Long> contents = contents();
        return contents.containsKey(key) || contents.size() < getTypeLimit();
    }

    private void receiveOrVoid(ItemStackKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        LinkedHashMap<ItemStackKey, Long> contents = contents();
        boolean knownType = contents.containsKey(key);
        if (!knownType && contents.size() >= getTypeLimit()) {
            return;
        }
        long current = contents.getOrDefault(key, 0L);
        long storedAmount = Math.min(amount, Long.MAX_VALUE - current);
        if (storedAmount <= 0) {
            return;
        }
        contents.put(key, current + storedAmount);
        markContentsChanged();
    }

    private void removeStored(ItemStackKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        LinkedHashMap<ItemStackKey, Long> contents = contents();
        long current = contents.getOrDefault(key, 0L);
        long remaining = current - Math.min(current, amount);
        if (remaining <= 0) {
            contents.remove(key);
        } else {
            contents.put(key, remaining);
        }
        markContentsChanged();
    }

    private static long totalAmount(Map<?, Long> contents) {
        long total = 0L;
        for (long amount : contents.values()) {
            total = saturatingAdd(total, amount);
        }
        return total;
    }

    private long cachedTotalAmount() {
        if (cachedServerTotalAmount < 0L) {
            cachedServerTotalAmount = totalAmount(contents());
        }
        return cachedServerTotalAmount;
    }

    private void invalidateSummaryCache() {
        cachedServerTotalAmount = -1L;
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
            Map.Entry<ItemStackKey, Long> entry = entryAt(slot);
            if (entry == null) {
                return ItemStack.EMPTY;
            }
            return entry.getKey().toStack((int) Math.min(Integer.MAX_VALUE, entry.getValue()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= getTypeLimit() || stack.isEmpty()) {
                return stack;
            }
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            ItemStackKey key = ItemStackKey.of(normalized);
            LinkedHashMap<ItemStackKey, Long> contents = contents();
            boolean knownType = contents.containsKey(key);
            if (!knownType && contents.size() >= getTypeLimit()) {
                return stack;
            }
            if (!simulate) {
                receiveOrVoid(key, stack.getCount());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= getTypeLimit() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            Map.Entry<ItemStackKey, Long> entry = entryAt(slot);
            if (entry == null) {
                return ItemStack.EMPTY;
            }
            int extractedAmount = (int) Math.min(Math.min(Integer.MAX_VALUE, amount), entry.getValue());
            if (extractedAmount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = entry.getKey().toStack(extractedAmount);
            if (!simulate) {
                long remaining = entry.getValue() - extractedAmount;
                if (remaining <= 0) {
                    contents().remove(entry.getKey());
                } else {
                    contents().put(entry.getKey(), remaining);
                }
                markContentsChanged();
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 0 || slot >= getTypeLimit() || stack.isEmpty()) {
                return false;
            }
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            ItemStackKey key = ItemStackKey.of(normalized);
            LinkedHashMap<ItemStackKey, Long> contents = contents();
            return contents.containsKey(key) || contents.size() < getTypeLimit();
        }
    }
}
