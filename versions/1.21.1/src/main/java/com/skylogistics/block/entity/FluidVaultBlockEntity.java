package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.menu.FluidVaultMenu;
import com.skylogistics.network.FluidVaultSnapshotPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.util.NbtSize;
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
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class FluidVaultBlockEntity extends BlockEntity {
    private static final int SNAPSHOT_ENTRY_LIMIT = 256;

    private final LinkedHashMap<FluidStackKey, Long> stored = new LinkedHashMap<>();
    private final List<FluidStackKey> contentIndex = new ArrayList<>();
    private final Set<UUID> viewers = new HashSet<>();
    private final IFluidHandler fluidHandler = new VaultFluidHandler();
    private LinkedHashMap<FluidStackKey, Long> indexedContents;
    private int indexedContentSize = -1;
    private int typeLimit = 1;
    private long capacityPerType = Long.MAX_VALUE;
    private int clientUsedTypes = -1;
    private long clientTotalAmount = -1L;
    private long cachedServerTotalAmount = -1L;
    private long syncVersion;
    private long clientSnapshotVersion;

    public FluidVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_VAULT.get(), pos, state);
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

    public long getSyncVersion() {
        return syncVersion;
    }

    public IFluidHandler fluidHandler() {
        return fluidHandler;
    }

    public List<StoredFluid> getStoredFluids(int limit) {
        List<StoredFluid> result = new ArrayList<>();
        int added = 0;
        for (Map.Entry<FluidStackKey, Long> entry : contents().entrySet()) {
            if (added++ >= limit) {
                break;
            }
            result.add(new StoredFluid(entry.getKey().toStack(1), entry.getValue()));
        }
        return result;
    }

    public long transferTo(FluidVaultBlockEntity target, int sourceTank, long maxAmount) {
        if (target == this || sourceTank < 0 || sourceTank >= getTypeLimit() || maxAmount <= 0) {
            return 0L;
        }
        Map.Entry<FluidStackKey, Long> entry = entryAt(sourceTank);
        if (entry == null) {
            return 0L;
        }
        long requested = Math.min(entry.getValue(), maxAmount);
        long moved = target.insertDirect(entry.getKey(), requested, false);
        if (moved <= 0) {
            return 0L;
        }
        removeStored(entry.getKey(), moved);
        return moved;
    }

    public StoredFluid storedFluidInTank(int tank) {
        Map.Entry<FluidStackKey, Long> entry = entryAt(tank);
        if (entry == null) {
            return new StoredFluid(FluidStack.EMPTY, 0L);
        }
        long amount = entry.getValue();
        if (amount <= 0L) {
            return new StoredFluid(FluidStack.EMPTY, 0L);
        }
        return new StoredFluid(entry.getKey().toStack((int) Math.min(Integer.MAX_VALUE, amount)), amount);
    }

    public long insertStoredFluid(FluidStack template, long amount, boolean simulate) {
        if (template.isEmpty() || amount <= 0L) {
            return 0L;
        }
        FluidStack normalized = template.copy();
        normalized.setAmount(1);
        return insertDirect(FluidStackKey.of(normalized), amount, simulate);
    }

    public long extractStoredFluid(int tank, long amount, boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        Map.Entry<FluidStackKey, Long> entry = entryAt(tank);
        if (entry == null) {
            return 0L;
        }
        long extracted = Math.min(amount, entry.getValue());
        if (extracted > 0L && !simulate) {
            removeStored(entry.getKey(), extracted);
        }
        return extracted;
    }

    public FluidStack drainForPlayer(FluidStack template, int amount, IFluidHandler.FluidAction action) {
        if (template.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack normalized = template.copy();
        normalized.setAmount(1);
        FluidStackKey key = FluidStackKey.of(normalized);
        LinkedHashMap<FluidStackKey, Long> contents = contents();
        long current = contents.getOrDefault(key, 0L);
        int drained = (int) Math.min(Math.min(Integer.MAX_VALUE, amount), current);
        if (drained <= 0) {
            return FluidStack.EMPTY;
        }
        if (action.execute()) {
            removeStored(key, drained);
        }
        return key.toStack(drained);
    }

    public void syncTo(ServerPlayer player) {
        List<FluidVaultSnapshotPacket.Entry> entries = new ArrayList<>();
        for (StoredFluid fluid : getStoredFluids(SNAPSHOT_ENTRY_LIMIT)) {
            entries.add(new FluidVaultSnapshotPacket.Entry(fluid.stack(), fluid.amount()));
        }
        ModNetworking.sendToPlayer(player, new FluidVaultSnapshotPacket(worldPosition, getTypeLimit(), getUsedTypes(),
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
            List<FluidVaultSnapshotPacket.Entry> entries) {
        this.typeLimit = Math.max(1, typeLimit);
        this.clientUsedTypes = Math.max(0, usedTypes);
        this.clientTotalAmount = Math.max(0L, totalAmount);
        clientSnapshotVersion++;
        invalidateSummaryCache();
        invalidateContentIndex();
        stored.clear();
        for (FluidVaultSnapshotPacket.Entry entry : entries) {
            FluidStack stack = entry.stack();
            if (entry.amount() <= 0 || stack.isEmpty()) {
                continue;
            }
            FluidStack normalized = stack.copy();
            normalized.setAmount(1);
            stored.put(FluidStackKey.of(normalized), entry.amount());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveMetadata(tag);
        ListTag fluidTags = new ListTag();
        for (Map.Entry<FluidStackKey, Long> fluid : stored.entrySet()) {
            long amount = fluid.getValue();
            if (amount <= 0) {
                continue;
            }
            CompoundTag keyTag = fluid.getKey().save();
            if (!isSavedFluidWithinNbtLimit(keyTag)) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put("Key", keyTag);
            entry.putLong("Amount", amount);
            fluidTags.add(entry);
        }
        if (!fluidTags.isEmpty()) {
            tag.put("Fluids", fluidTags);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        typeLimit = Math.max(1, tag.getInt("TypeLimit"));
        capacityPerType = Long.MAX_VALUE;
        stored.clear();
        invalidateContentIndex();
        invalidateSummaryCache();
        if (tag.contains("Fluids", Tag.TAG_LIST)) {
            ListTag fluidTags = tag.getList("Fluids", Tag.TAG_COMPOUND);
            int maxEntries = Math.min(fluidTags.size(), getTypeLimit());
            for (int i = 0; i < maxEntries; i++) {
                CompoundTag entry = fluidTags.getCompound(i);
                long amount = entry.getLong("Amount");
                FluidStackKey key = FluidStackKey.load(entry.getCompound("Key"));
                FluidStack stack = key.toStack(1);
                if (amount > 0 && !stack.isEmpty() && isFluidKeyWithinNbtLimit(key)) {
                    stored.put(key, amount);
                }
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveMetadata(tag);
        return tag;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        CompoundTag tag = new CompoundTag();
        tag.putInt("TypeLimit", typeLimit);
        tag.putLong("CapacityPerType", capacityPerType);
        BlockEntity.addEntityType(tag, getType());
        components.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private LinkedHashMap<FluidStackKey, Long> contents() {
        return stored;
    }

    private void saveMetadata(CompoundTag tag) {
        tag.putInt("TypeLimit", typeLimit);
        tag.putLong("CapacityPerType", capacityPerType);
        tag.putInt("UsedTypes", contents().size());
        tag.putLong("TotalAmount", cachedTotalAmount());
    }

    private void markContentsChanged() {
        syncVersion++;
        invalidateSummaryCache();
        setChanged();
        if (level instanceof ServerLevel) {
            syncToViewingPlayers();
        }
    }

    private void markMetadataChanged() {
        syncVersion++;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
            if (player == null || !(player.containerMenu instanceof FluidVaultMenu menu)
                    || !menu.getPos().equals(worldPosition)) {
                iterator.remove();
                continue;
            }
            syncTo(player);
            menu.noteVaultSnapshotSynced(syncVersion);
        }
    }

    private Map.Entry<FluidStackKey, Long> entryAt(int tank) {
        if (tank < 0) {
            return null;
        }
        LinkedHashMap<FluidStackKey, Long> contents = contents();
        if (tank >= contents.size()) {
            return null;
        }
        List<FluidStackKey> index = contentIndex(contents);
        FluidStackKey key = index.get(tank);
        Long amount = contents.get(key);
        if (amount == null || amount <= 0) {
            invalidateContentIndex();
            index = contentIndex(contents);
            if (tank >= index.size()) {
                return null;
            }
            key = index.get(tank);
            amount = contents.get(key);
        }
        return amount == null || amount <= 0 ? null : Map.entry(key, amount);
    }

    private List<FluidStackKey> contentIndex(LinkedHashMap<FluidStackKey, Long> contents) {
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

    private long insertDirect(FluidStackKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0L;
        }
        LinkedHashMap<FluidStackKey, Long> contents = contents();
        long current = contents.getOrDefault(key, 0L);
        if (current <= 0 && !isFluidKeyWithinNbtLimit(key)) {
            return 0L;
        }
        if (current <= 0 && contents.size() >= getTypeLimit()) {
            return 0L;
        }
        long space = Math.max(0L, capacityPerType - current);
        long inserted = Math.min(space, amount);
        if (inserted <= 0L) {
            return 0L;
        }
        if (!simulate) {
            long updated = current + inserted;
            contents.put(key, updated);
            markContentsChanged();
        }
        return inserted;
    }

    private void removeStored(FluidStackKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        LinkedHashMap<FluidStackKey, Long> contents = contents();
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

    private static boolean isFluidKeyWithinNbtLimit(FluidStackKey key) {
        return isSavedFluidWithinNbtLimit(key.save());
    }

    private static boolean isSavedFluidWithinNbtLimit(CompoundTag tag) {
        return NbtSize.serializedBytes(tag) <= SkyLogisticsConfig.maxVaultFluidEntryNbtBytes();
    }

    public record StoredFluid(FluidStack stack, long amount) {
    }

    private final class VaultFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return getTypeLimit();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank < 0 || tank >= getTypeLimit()) {
                return FluidStack.EMPTY;
            }
            Map.Entry<FluidStackKey, Long> entry = entryAt(tank);
            if (entry == null) {
                return FluidStack.EMPTY;
            }
            return entry.getKey().toStack((int) Math.min(Integer.MAX_VALUE, entry.getValue()));
        }

        @Override
        public int getTankCapacity(int tank) {
            return (int) Math.min(Integer.MAX_VALUE, capacityPerType);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (tank < 0 || tank >= getTypeLimit() || stack.isEmpty()) {
                return false;
            }
            FluidStack normalized = stack.copy();
            normalized.setAmount(1);
            FluidStackKey key = FluidStackKey.of(normalized);
            LinkedHashMap<FluidStackKey, Long> contents = contents();
            if (contents.containsKey(key)) {
                return true;
            }
            return contents.size() < getTypeLimit() && isFluidKeyWithinNbtLimit(key);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            FluidStack normalized = resource.copy();
            normalized.setAmount(1);
            FluidStackKey key = FluidStackKey.of(normalized);
            LinkedHashMap<FluidStackKey, Long> contents = contents();
            long current = contents.getOrDefault(key, 0L);
            if (current <= 0 && !isFluidKeyWithinNbtLimit(key)) {
                return 0;
            }
            if (current <= 0 && contents.size() >= getTypeLimit()) {
                return 0;
            }
            if (action.execute()) {
                long updated = Math.min(capacityPerType, saturatingAdd(current, resource.getAmount()));
                if (updated != current) {
                    contents.put(key, updated);
                    markContentsChanged();
                }
            }
            return resource.getAmount();
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidStack normalized = resource.copy();
            normalized.setAmount(1);
            FluidStackKey key = FluidStackKey.of(normalized);
            LinkedHashMap<FluidStackKey, Long> contents = contents();
            long current = contents.getOrDefault(key, 0L);
            int drained = (int) Math.min(Math.min(Integer.MAX_VALUE, resource.getAmount()), current);
            if (drained <= 0) {
                return FluidStack.EMPTY;
            }
            if (action.execute()) {
                long remaining = current - drained;
                if (remaining <= 0) {
                    contents.remove(key);
                } else {
                    contents.put(key, remaining);
                }
                markContentsChanged();
            }
            return key.toStack(drained);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            Map.Entry<FluidStackKey, Long> entry = entryAt(0);
            if (entry == null) {
                return FluidStack.EMPTY;
            }
            int drained = (int) Math.min(Math.min(Integer.MAX_VALUE, maxDrain), entry.getValue());
            if (drained <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStackKey key = entry.getKey();
            if (action.execute()) {
                long remaining = entry.getValue() - drained;
                if (remaining <= 0) {
                    contents().remove(key);
                } else {
                    contents().put(key, remaining);
                }
                markContentsChanged();
            }
            return key.toStack(drained);
        }
    }
}
