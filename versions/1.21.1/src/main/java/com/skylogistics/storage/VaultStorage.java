package com.skylogistics.storage;

import com.skylogistics.SkyLogistics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.fluids.FluidStack;

public class VaultStorage extends SavedData {
    private static final String SHARDED_DATA_NAME = SkyLogistics.MOD_ID + "_vault_storage_";
    private static final int SHARD_COUNT = 16;

    private final Map<UUID, LinkedHashMap<FluidStackKey, Long>> fluidVaults = new HashMap<>();

    public static VaultStorage get(ServerLevel level, UUID vaultId) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        ServerLevel storageLevel = overworld == null ? level : overworld;
        DimensionDataStorage storage = storageLevel.getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(VaultStorage::new, VaultStorage::load),
                SHARDED_DATA_NAME + shard(vaultId));
    }

    public static VaultStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        VaultStorage storage = new VaultStorage();
        readFluidVaults(tag, storage);
        return storage;
    }

    public LinkedHashMap<FluidStackKey, Long> fluids(UUID id) {
        return fluidVaults.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("FluidVaults", writeFluidVaults());
        return tag;
    }

    private static void readFluidVaults(CompoundTag tag, VaultStorage storage) {
        ListTag vaults = tag.getList("FluidVaults", Tag.TAG_COMPOUND);
        for (int i = 0; i < vaults.size(); i++) {
            CompoundTag vaultTag = vaults.getCompound(i);
            if (!vaultTag.hasUUID("Id")) {
                continue;
            }
            LinkedHashMap<FluidStackKey, Long> contents = new LinkedHashMap<>();
            ListTag entries = vaultTag.getList("Fluids", Tag.TAG_COMPOUND);
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                CompoundTag entry = entries.getCompound(entryIndex);
                long amount = entry.getLong("Amount");
                FluidStackKey key = FluidStackKey.load(entry.getCompound("Key"));
                FluidStack stack = key.toStack(1);
                if (amount > 0 && !stack.isEmpty()) {
                    contents.put(key, amount);
                }
            }
            storage.fluidVaults.put(vaultTag.getUUID("Id"), contents);
        }
    }

    private ListTag writeFluidVaults() {
        ListTag vaults = new ListTag();
        for (Map.Entry<UUID, LinkedHashMap<FluidStackKey, Long>> vault : fluidVaults.entrySet()) {
            if (vault.getValue().isEmpty()) {
                continue;
            }
            CompoundTag vaultTag = new CompoundTag();
            vaultTag.putUUID("Id", vault.getKey());
            ListTag entries = new ListTag();
            vault.getValue().forEach((key, amount) -> {
                if (amount > 0) {
                    CompoundTag entry = new CompoundTag();
                    entry.put("Key", key.save());
                    entry.putLong("Amount", amount);
                    entries.add(entry);
                }
            });
            vaultTag.put("Fluids", entries);
            vaults.add(vaultTag);
        }
        return vaults;
    }

    private static int shard(UUID id) {
        return Math.floorMod(id.hashCode(), SHARD_COUNT);
    }

}
