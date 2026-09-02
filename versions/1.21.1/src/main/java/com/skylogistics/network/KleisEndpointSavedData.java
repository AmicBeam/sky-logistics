package com.skylogistics.network;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.menu.KleisDominionWandMenu;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;

public final class KleisEndpointSavedData extends SavedData {
    private static final String DATA_NAME = "skylogistics_kleis_dominion_wand_endpoints";
    private static final int SCHEMA_VERSION = 1;
    private static final String ENTRIES = "Entries";

    private final Map<Key, Entry> entries = new HashMap<>();
    private final Map<Key, KleisRuntimeEndpoint> runtime = new HashMap<>();
    private final KleisChunkIndex<ResourceKey<Level>, Key> entriesByChunk = new KleisChunkIndex<>();
    private boolean runtimeInitialized;

    public static KleisEndpointSavedData get(MinecraftServer server) {
        KleisEndpointSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KleisEndpointSavedData::new, KleisEndpointSavedData::load, null), DATA_NAME);
        data.initializeRuntime(server);
        return data;
    }

    public static KleisEndpointSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        KleisEndpointSavedData data = new KleisEndpointSavedData();
        if (tag.getInt("SchemaVersion") > SCHEMA_VERSION) {
            return data;
        }
        ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag saved = list.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(saved.getString("Dimension"));
            Direction face = Direction.byName(saved.getString("Face"));
            if (dimensionId == null || face == null || !saved.hasUUID("Owner")) continue;
            Key key = new Key(ResourceKey.create(Registries.DIMENSION, dimensionId),
                    BlockPos.of(saved.getLong("Pos")), face);
            data.storeEntry(key, new Entry(saved.getUUID("Owner"), saved.getInt("Revision"),
                    saved.getCompound("Node").copy()));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag list = new ListTag();
        for (Map.Entry<Key, Entry> mapEntry : entries.entrySet()) {
            CompoundTag saved = new CompoundTag();
            saved.putString("Dimension", mapEntry.getKey().dimension().location().toString());
            saved.putLong("Pos", mapEntry.getKey().pos().asLong());
            saved.putString("Face", mapEntry.getKey().face().getSerializedName());
            saved.putUUID("Owner", mapEntry.getValue().owner());
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
            if (!canModify(player, key) || !isReachable(player, key)) return ToggleResult.EDIT_DENIED;
            removeRuntime(key);
            removeEntry(key);
            setDirty();
            closeMenus(player.getServer(), key);
            return ToggleResult.REMOVED;
        }
        if (!(player.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)
                || level.getBlockState(pos).isAir()) {
            return ToggleResult.INVALID_TARGET;
        }
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.readOrCreate(configurator, player);
        UUID lineOwner = SkyPlayerLines.ownerOf(player.getServer(), config.lineId());
        if (lineOwner != null && !lineOwner.equals(player.getUUID())) return ToggleResult.EDIT_DENIED;
        KleisRuntimeEndpoint node = new KleisRuntimeEndpoint(level, pos, face, player.getUUID(), config,
                extracting ? com.skylogistics.util.NodeFaceMode.INPUT : com.skylogistics.util.NodeFaceMode.OUTPUT);
        if (!node.hasEnabledTargetCapability()) return ToggleResult.INVALID_TARGET;
        Entry entry = new Entry(player.getUUID(), 1, node.save());
        storeEntry(key, entry);
        attachRuntime(key, entry, node);
        SkyPlayerLines.claimOwner(player.getServer(), node.getLineId(), player);
        setDirty();
        return extracting ? ToggleResult.CREATED_EXTRACT : ToggleResult.CREATED_INSERT;
    }

    public Entry entry(Key key) {
        return entries.get(key);
    }

    public KleisRuntimeEndpoint runtimeNode(Key key) {
        return runtime.get(key);
    }

    public List<Snapshot> snapshots(ServerPlayer viewer, ResourceKey<Level> dimension, UUID lineId,
            BlockPos center, int range) {
        long distance = (long) range * range;
        List<Snapshot> result = new ArrayList<>();
        for (Key key : nearbyKeys(dimension, center, range)) {
            KleisRuntimeEndpoint node = runtime.get(key);
            if (node == null || !node.getLineId().equals(lineId)
                    || key.pos().distSqr(center) > distance || !canView(viewer, key)) continue;
            Entry saved = entries.get(key);
            if (saved == null) continue;
            result.add(new Snapshot(key.pos(), key.face(),
                    node.getFaceMode(KleisRuntimeEndpoint.ENDPOINT_DIRECTION), node.getLineId(), saved.revision()));
        }
        return List.copyOf(result);
    }

    public List<Snapshot> snapshotsNearby(ServerPlayer viewer, ResourceKey<Level> dimension,
            BlockPos center, int range) {
        long distance = (long) range * range;
        List<Snapshot> result = new ArrayList<>();
        for (Key key : nearbyKeys(dimension, center, range)) {
            KleisRuntimeEndpoint node = runtime.get(key);
            if (node == null || key.pos().distSqr(center) > distance
                    || !canView(viewer, key)) continue;
            Entry saved = entries.get(key);
            if (saved == null) continue;
            result.add(new Snapshot(key.pos(), key.face(),
                    node.getFaceMode(KleisRuntimeEndpoint.ENDPOINT_DIRECTION), node.getLineId(), saved.revision()));
        }
        return List.copyOf(result);
    }

    private List<Key> nearbyKeys(ResourceKey<Level> dimension, BlockPos center, int range) {
        List<Key> result = new ArrayList<>();
        for (int chunkX = (center.getX() - range) >> 4; chunkX <= (center.getX() + range) >> 4; chunkX++) {
            for (int chunkZ = (center.getZ() - range) >> 4; chunkZ <= (center.getZ() + range) >> 4; chunkZ++) {
                result.addAll(entriesByChunk.entries(dimension, chunkX, chunkZ));
            }
        }
        return result;
    }

    public EditResult copyToConfigurator(ServerPlayer player, Key key, int expectedRevision,
            ItemStack configurator) {
        Entry saved = entries.get(key);
        KleisRuntimeEndpoint node = runtime.get(key);
        if (saved == null || node == null || saved.revision() != expectedRevision) return EditResult.STALE;
        if (!canView(player, key) || !isReachable(player, key)) return EditResult.DENIED;
        ConfiguratorItem.writeConfig(configurator, ConfiguratorItem.ToolConfig.fromSingleEndpoint(node),
                node.getAssignedLineName());
        ConfiguratorItem.setPasteMode(configurator, true);
        player.inventoryMenu.broadcastChanges();
        return EditResult.COPIED;
    }

    public EditResult pasteFromConfigurator(ServerPlayer player, Key key, int expectedRevision,
            ItemStack configurator) {
        Entry saved = entries.get(key);
        KleisRuntimeEndpoint node = runtime.get(key);
        if (saved == null || node == null || saved.revision() != expectedRevision) return EditResult.STALE;
        if (!canModify(player, key) || !isReachable(player, key)) return EditResult.DENIED;
        ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(configurator);
        if (!ConfiguratorItem.isPasteMode(configurator) || config == null) return EditResult.NO_CONFIG;
        UUID lineOwner = SkyPlayerLines.ownerOf(player.getServer(), config.lineId());
        if (lineOwner != null && !lineOwner.equals(player.getUUID())) return EditResult.DENIED;
        if (!node.supportsToolConfig(config)) return EditResult.INVALID_TARGET;
        node.applySingleEndpointToolConfig(config, player);
        SkyPlayerLines.claimOwner(player.getServer(), node.getLineId(), player);
        player.inventoryMenu.broadcastChanges();
        return EditResult.PASTED;
    }

    public boolean canView(ServerPlayer player, Key key) {
        Entry saved = entries.get(key);
        return saved != null && saved.owner().equals(player.getUUID());
    }

    public boolean canModify(ServerPlayer player, Key key) {
        return canView(player, key);
    }

    public boolean isReachable(ServerPlayer player, Key key) {
        if (!player.level().dimension().equals(key.dimension())
                || player.distanceToSqr(key.pos().getX() + .5D, key.pos().getY() + .5D,
                        key.pos().getZ() + .5D) > 64.0D
                || !player.level().hasChunkAt(key.pos())) return false;
        if (!(player.pick(8.0D, 1.0F, false) instanceof BlockHitResult hit)) return false;
        return hit.getBlockPos().equals(key.pos()) && hit.getDirection() == key.face();
    }

    public void syncVisibleOverlays(MinecraftServer server, ResourceKey<Level> dimension, BlockPos changedPos) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (!viewer.level().dimension().equals(dimension)
                    || viewer.blockPosition().distSqr(changedPos) > 64L * 64L) continue;
            boolean editNearby = viewer.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                    && viewer.getOffhandItem().is(ModItems.KLEIS_DOMINION_WAND.get());
            boolean currentLine = viewer.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                    && viewer.getOffhandItem().is(ModItems.CONFIGURATOR.get());
            if (editNearby) {
                UUID selectedLine = ConfiguratorItem.readLineId(viewer.getMainHandItem());
                ModNetworking.sendToPlayer(viewer, KleisOverlayPacket.from(true, selectedLine,
                        snapshotsNearby(viewer, dimension, viewer.blockPosition(), 64)));
            } else if (currentLine) {
                UUID selectedLine = ConfiguratorItem.readLineId(viewer.getOffhandItem());
                if (selectedLine != null) ModNetworking.sendToPlayer(viewer, KleisOverlayPacket.from(false,
                        selectedLine, snapshots(viewer, dimension, selectedLine, viewer.blockPosition(), 64)));
            }
        }
    }

    private void initializeRuntime(MinecraftServer server) {
        if (runtimeInitialized) return;
        runtimeInitialized = true;
        for (Map.Entry<Key, Entry> mapEntry : entries.entrySet()) {
            Key key = mapEntry.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null && level.hasChunkAt(key.pos())) attachSavedRuntime(level, key, mapEntry.getValue());
        }
    }

    public void onChunkLoaded(ServerLevel level, ChunkPos chunkPos) {
        for (Key key : entriesByChunk.entries(level.dimension(), chunkPos.x, chunkPos.z)) {
            Entry entry = entries.get(key);
            if (entry != null) attachSavedRuntime(level, key, entry);
        }
    }

    public void onChunkUnloaded(ServerLevel level, ChunkPos chunkPos) {
        for (Key key : entriesByChunk.entries(level.dimension(), chunkPos.x, chunkPos.z)) {
            removeRuntime(key);
        }
    }

    public void clearRuntime() {
        for (Map.Entry<Key, KleisRuntimeEndpoint> entry : List.copyOf(runtime.entrySet())) {
            removeRuntime(entry.getKey());
        }
        runtimeInitialized = false;
    }

    private void attachSavedRuntime(ServerLevel level, Key key, Entry entry) {
        if (runtime.containsKey(key)) return;
        KleisRuntimeEndpoint node = KleisRuntimeEndpoint.fromSavedData(level, key.pos(), key.face(),
                entry.owner(), entry.nodeData().copy());
        attachRuntime(key, entry, node);
    }

    private void storeEntry(Key key, Entry entry) {
        entries.put(key, entry);
        entriesByChunk.add(key.dimension(), key.pos().getX() >> 4, key.pos().getZ() >> 4, key);
    }

    private void removeEntry(Key key) {
        entries.remove(key);
        entriesByChunk.remove(key.dimension(), key.pos().getX() >> 4, key.pos().getZ() >> 4, key);
    }

    private void attachRuntime(Key key, Entry entry, KleisRuntimeEndpoint node) {
        runtime.put(key, node);
        node.setChangeListener(() -> updateFromRuntime(key, node));
        SkyNetworkRegistry.registerVirtual((ServerLevel) node.getLevel(), node);
    }

    private void updateFromRuntime(Key key, KleisRuntimeEndpoint node) {
        Entry previous = entries.get(key);
        if (previous == null) return;
        entries.put(key, new Entry(previous.owner(), previous.revision() + 1, node.save()));
        setDirty();
        syncVisibleOverlays(node.getLevel().getServer(), key.dimension(), key.pos());
    }

    private static void closeMenus(MinecraftServer server, Key key) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.level().dimension().equals(key.dimension())
                    && viewer.containerMenu instanceof KleisDominionWandMenu menu
                    && menu.getPos().equals(key.pos()) && menu.targetFace() == key.face()) viewer.closeContainer();
        }
    }

    private void removeRuntime(Key key) {
        KleisRuntimeEndpoint node = runtime.remove(key);
        if (node != null) SkyNetworkRegistry.unregisterVirtual((ServerLevel) node.getLevel(), node);
    }

    public record Key(ResourceKey<Level> dimension, BlockPos pos, Direction face) {}
    public record Entry(UUID owner, int revision, CompoundTag nodeData) {}
    public record Snapshot(BlockPos pos, Direction face, com.skylogistics.util.NodeFaceMode mode,
                           UUID lineId, int revision) {}

    public enum EditResult {
        COPIED,
        PASTED,
        NO_CONFIG,
        STALE,
        DENIED,
        INVALID_TARGET
    }

    public enum ToggleResult {
        CREATED_INSERT,
        CREATED_EXTRACT,
        REMOVED,
        INVALID_TARGET,
        EDIT_DENIED
    }
}
