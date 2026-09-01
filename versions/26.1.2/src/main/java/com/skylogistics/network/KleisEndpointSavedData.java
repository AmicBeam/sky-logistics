package com.skylogistics.network;

import com.skylogistics.block.entity.KleisVirtualNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.util.NodeMode;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class KleisEndpointSavedData extends SavedData {
    private static final String DATA_NAME = "skylogistics_kleis_dominion_wand_endpoints";
    private static final SavedDataType<KleisEndpointSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("skylogistics", DATA_NAME),
            KleisEndpointSavedData::new,
            CompoundTag.CODEC.xmap(
                    tag -> load(tag, StackData.builtinRegistries()),
                    data -> data.save(new CompoundTag(), StackData.builtinRegistries())),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private static final int SCHEMA_VERSION = 1;
    private static final String ENTRIES = "Entries";

    private final Map<Key, Entry> entries = new HashMap<>();
    private final Map<Key, KleisVirtualNodeBlockEntity> runtime = new HashMap<>();
    private long lastRefreshTick = Long.MIN_VALUE;

    public static KleisEndpointSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static KleisEndpointSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        KleisEndpointSavedData data = new KleisEndpointSavedData();
        if (tag.getIntOr("SchemaVersion", 0) > SCHEMA_VERSION) {
            return data;
        }
        ListTag list = tag.getListOrEmpty(ENTRIES);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompoundOrEmpty(i);
            Identifier dimensionId = Identifier.tryParse(saved.getStringOr("Dimension", ""));
            Direction face = Direction.byName(saved.getStringOr("Face", ""));
            if (dimensionId == null || face == null || !com.skylogistics.util.NbtCompat.hasUuid(saved, "Owner")) continue;
            Key key = new Key(ResourceKey.create(Registries.DIMENSION, dimensionId),
                    BlockPos.of(saved.getLongOr("Pos", 0L)), face);
            data.entries.put(key, new Entry(com.skylogistics.util.NbtCompat.getUuid(saved, "Owner"),
                    saved.getIntOr("Revision", 0), saved.getCompoundOrEmpty("Node").copy()));
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag list = new ListTag();
        for (Map.Entry<Key, Entry> mapEntry : entries.entrySet()) {
            CompoundTag saved = new CompoundTag();
            saved.putString("Dimension", mapEntry.getKey().dimension().identifier().toString());
            saved.putLong("Pos", mapEntry.getKey().pos().asLong());
            saved.putString("Face", mapEntry.getKey().face().getSerializedName());
            com.skylogistics.util.NbtCompat.putUuid(saved, "Owner", mapEntry.getValue().owner());
            saved.putInt("Revision", mapEntry.getValue().revision());
            saved.put("Node", mapEntry.getValue().nodeData().copy());
            list.add(saved);
        }
        tag.put(ENTRIES, list);
        return tag;
    }

    public ToggleResult toggle(ServerPlayer player, BlockPos pos, Direction face, boolean extracting,
            ItemStack configurator) {
        Key key = new Key(player.level().dimension(), pos.immutable(), face);
        if (entries.containsKey(key)) {
            removeRuntime(key);
            entries.remove(key);
            setDirty();
            return ToggleResult.REMOVED;
        }
        if (!(player.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)
                || level.getBlockState(pos).isAir()) {
            return ToggleResult.INVALID_TARGET;
        }
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.readOrCreate(configurator, player);
        KleisVirtualNodeBlockEntity node = new KleisVirtualNodeBlockEntity(pos, face);
        node.setLevel(level);
        node.setSuppressChanges(true);
        node.applyPlacementToolConfig(config, false);
        node.setMode(extracting ? NodeMode.INPUT : NodeMode.OUTPUT);
        node.setSuppressChanges(false);
        Entry entry = new Entry(player.getUUID(), 1, node.saveWithoutMetadata(node.getLevel().registryAccess()));
        entries.put(key, entry);
        attachRuntime(key, entry, node);
        SkyPlayerLines.claimOwner(player.level().getServer(), node.getLineId(), player);
        setDirty();
        return extracting ? ToggleResult.CREATED_EXTRACT : ToggleResult.CREATED_INSERT;
    }

    public Entry entry(Key key) {
        return entries.get(key);
    }

    public KleisVirtualNodeBlockEntity runtimeNode(Key key) {
        return runtime.get(key);
    }

    public List<Snapshot> snapshots(ResourceKey<Level> dimension, UUID lineId, BlockPos center, int range) {
        long distance = (long) range * range;
        List<Snapshot> result = new ArrayList<>();
        for (Map.Entry<Key, KleisVirtualNodeBlockEntity> mapEntry : runtime.entrySet()) {
            Key key = mapEntry.getKey();
            if (!key.dimension().equals(dimension) || !mapEntry.getValue().getLineId().equals(lineId)
                    || key.pos().distSqr(center) > distance) continue;
            result.add(new Snapshot(key.pos(), key.face(),
                    mapEntry.getValue().getFaceMode(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION),
                    mapEntry.getValue().getLineId()));
        }
        return List.copyOf(result);
    }

    public void refresh(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (lastRefreshTick != Long.MIN_VALUE && now - lastRefreshTick < 20L) return;
        lastRefreshTick = now;
        for (Map.Entry<Key, Entry> mapEntry : entries.entrySet()) {
            Key key = mapEntry.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !level.hasChunkAt(key.pos())) {
                removeRuntime(key);
                continue;
            }
            if (!runtime.containsKey(key)) {
                KleisVirtualNodeBlockEntity node = new KleisVirtualNodeBlockEntity(key.pos(), key.face());
                node.setLevel(level);
                node.setSuppressChanges(true);
                node.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                        net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(),
                        mapEntry.getValue().nodeData().copy()));
                node.setSuppressChanges(false);
                attachRuntime(key, mapEntry.getValue(), node);
            }
        }
    }

    public void clearRuntime() {
        for (Map.Entry<Key, KleisVirtualNodeBlockEntity> entry : List.copyOf(runtime.entrySet())) {
            removeRuntime(entry.getKey());
        }
        lastRefreshTick = Long.MIN_VALUE;
    }

    private void attachRuntime(Key key, Entry entry, KleisVirtualNodeBlockEntity node) {
        runtime.put(key, node);
        node.setChangeListener(() -> updateFromRuntime(key, node));
        SkyNetworkRegistry.registerVirtual((ServerLevel) node.getLevel(), node);
    }

    private void updateFromRuntime(Key key, KleisVirtualNodeBlockEntity node) {
        Entry previous = entries.get(key);
        if (previous == null) return;
        entries.put(key, new Entry(previous.owner(), previous.revision() + 1, node.saveWithoutMetadata(node.getLevel().registryAccess())));
        setDirty();
    }

    private void removeRuntime(Key key) {
        KleisVirtualNodeBlockEntity node = runtime.remove(key);
        if (node != null && node.getLevel() instanceof ServerLevel level) {
            SkyNetworkRegistry.unregisterVirtual(level, node);
        }
    }

    public record Key(ResourceKey<Level> dimension, BlockPos pos, Direction face) {}
    public record Entry(UUID owner, int revision, CompoundTag nodeData) {}
    public record Snapshot(BlockPos pos, Direction face, com.skylogistics.util.NodeFaceMode mode, UUID lineId) {}

    public enum ToggleResult {
        CREATED_INSERT,
        CREATED_EXTRACT,
        REMOVED,
        INVALID_TARGET
    }
}
