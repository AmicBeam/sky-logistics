package com.skylogistics.network;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.block.entity.SimplePipeBlockEntity;
import com.skylogistics.block.entity.NetworkEndpointBlockEntity;
import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.compat.arsnouveau.ArsNouveauCompat;
import com.skylogistics.compat.arsnouveau.SourceHandlerBridge;
import com.skylogistics.compat.botania.BotaniaCompat;
import com.skylogistics.compat.botania.ManaHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.storage.ItemStackKey;
import com.skylogistics.util.BudgetedScanCursors;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import com.skylogistics.util.SimplePipeType;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SkyNetworkRegistry {
    private static final int REJECTED_ITEM_CACHE_SIZE = 4;
    private static final int EMPTY_ITEM_SLOT_CACHE_SIZE = 32;
    private static final int PREFERRED_ITEM_SLOT_MISS_LIMIT = 3;
    private static final int ITEM_SLOT_DISCOVERY_PREFERRED_INTERVAL = 4;
    private static final int FLUID_TANK_DISCOVERY_PREFERRED_INTERVAL = 4;
    private static final int CHEMICAL_TANK_DISCOVERY_PREFERRED_INTERVAL = 4;
    private static final int EMPTY_ITEM_SLOT_RETRY_TICKS = 20;
    private static final int PREFERRED_FLUID_TANK_CACHE_SIZE = 4;
    private static final int EMPTY_FLUID_TANK_CACHE_SIZE = 16;
    private static final int PREFERRED_FLUID_TANK_MISS_LIMIT = 3;
    private static final int EMPTY_FLUID_TANK_RETRY_TICKS = 20;
    private static final int PREFERRED_CHEMICAL_TANK_CACHE_SIZE = 4;
    private static final int EMPTY_CHEMICAL_TANK_CACHE_SIZE = 16;
    private static final int PREFERRED_CHEMICAL_TANK_MISS_LIMIT = 3;
    private static final int EMPTY_CHEMICAL_TANK_RETRY_TICKS = 20;
    private static final int MAX_TRANSFER_FAILURES = 8;
    private static final int TARGET_CURSOR_REPROBE_SUCCESSES = 9;
    private static final byte TARGET_CURSOR_NEW = 0;
    private static final byte TARGET_CURSOR_PROBATION = 1;
    private static final byte TARGET_CURSOR_REUSE = 2;
    private static final byte TARGET_CURSOR_SEQUENTIAL = 3;

    private static final Map<ResourceKey<Level>, DimensionIndex> DIMENSIONS = new HashMap<>();
    private static final Set<LineIndex> ACTIVE_LINES = new LinkedHashSet<>();
    private static final List<LineIndex> ACTIVE_LINE_SNAPSHOT = new ArrayList<>();
    private static final TreeMap<Long, Set<LineIndex>> WAKE_BUCKETS = new TreeMap<>();
    private static final Map<LineIndex, Long> SCHEDULED_WAKE = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_ITEM_OUTPUTS = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_FLUID_OUTPUTS = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_CHEMICAL_OUTPUTS = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_ENERGY_OUTPUTS = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_MANA_OUTPUTS = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_SOURCE_OUTPUTS = new HashMap<>();
    // Keep a resource index warm once requested; upgrade/config churn must not recreate it.
    private static final Set<UUID> GLOBAL_ITEM_OUTPUT_LINES = new HashSet<>();
    private static final Set<UUID> GLOBAL_FLUID_OUTPUT_LINES = new HashSet<>();
    private static final Set<UUID> GLOBAL_CHEMICAL_OUTPUT_LINES = new HashSet<>();
    private static final Set<UUID> GLOBAL_ENERGY_OUTPUT_LINES = new HashSet<>();
    private static final Set<UUID> GLOBAL_MANA_OUTPUT_LINES = new HashSet<>();
    private static final Set<UUID> GLOBAL_SOURCE_OUTPUT_LINES = new HashSet<>();
    private static boolean runtimeCachesDirty = true;
    private static boolean globalOutputsDirty = true;
    private static boolean activeLineSnapshotDirty = true;
    private static int activeLineCursor;

    private SkyNetworkRegistry() {
    }

    public static synchronized void register(ServerLevel level, BlockPos pos) {
        DimensionIndex index = DIMENSIONS.computeIfAbsent(level.dimension(), ignored -> new DimensionIndex());
        index.nodes.add(pos.immutable());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof NetworkEndpointBlockEntity endpoint) {
            index.loadedEndpoints.put(pos.immutable(), endpoint);
        }
        if (blockEntity instanceof SimplePipeBlockEntity) {
            markPipeTopologyDirty(index, pos);
        } else {
            markNodeTopologyDirty(index, pos);
        }
    }

    public static synchronized void unregister(ServerLevel level, BlockPos pos) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        if (index != null) {
            boolean pipe = index.pipeLineByPos.containsKey(pos)
                    || level.getBlockEntity(pos) instanceof SimplePipeBlockEntity;
            if (pipe) {
                markPipeTopologyDirty(index, pos);
            } else {
                markNodeTopologyDirty(index, pos);
            }
            index.nodes.remove(pos);
            index.loadedEndpoints.remove(pos);
            if (index.nodes.isEmpty()) {
                DIMENSIONS.remove(level.dimension());
                runtimeCachesDirty = true;
                globalOutputsDirty = true;
            }
        }
    }

    public static synchronized void markDirty(ServerLevel level) {
        markTopologyDirty(level);
    }

    public static synchronized void markTopologyDirty(ServerLevel level) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        if (index != null) {
            markTopologyDirty(index);
        }
    }

    public static synchronized void markTopologyDirty(ServerLevel level, BlockPos pos) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        if (index != null) {
            markNodeTopologyDirty(index, pos);
        }
    }

    public static synchronized void markPipeTopologyDirty(ServerLevel level, BlockPos pos) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        if (index != null) {
            markPipeTopologyDirty(index, pos);
        }
    }

    public static synchronized PipeLineInfo simplePipeLineInfo(
            ServerLevel level, BlockPos pos, SimplePipeType pipeType) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        if (index == null || index.dirty) return null;
        SimplePipeBlockEntity pipe = simplePipeAt(index.loadedEndpoints, pos);
        if (pipe == null || pipe.pipeType() != pipeType) return null;
        UUID lineId = index.pipeLineByPos.get(pos);
        Set<BlockPos> members = lineId == null ? null : index.pipeMembers.get(lineId);
        return members == null ? null : new PipeLineInfo(lineId, members.size());
    }

    public static synchronized void markRuntimeDirty(ServerLevel level, BlockPos pos) {
        LineIndex line = findLine(level, pos);
        if (line != null) {
            wakeLine(line);
        }
    }

    public static synchronized void markPriorityDirty(ServerLevel level, BlockPos pos) {
        LineIndex line = findLine(level, pos);
        if (line != null) {
            line.refreshPriorityOutputs(pos);
            refreshGlobalLineIds(Set.of(line.lineId()));
        }
    }

    private static void resetTargetScans(UUID lineId) {
        for (DimensionIndex index : DIMENSIONS.values()) {
            LineIndex line = index.lines.get(lineId);
            if (line == null) continue;
            for (CachedEndpoint endpoint : line.inputs()) endpoint.clearTargetScans();
            wakeLine(line);
        }
    }

    public static synchronized ReadyLines readyLines(MinecraftServer server, long gameTime) {
        rebuildDirty(server);
        if (runtimeCachesDirty) {
            rebuildRuntimeCaches(server);
            runtimeCachesDirty = false;
            globalOutputsDirty = true;
        }
        if (globalOutputsDirty) {
            rebuildGlobalOutputs();
            globalOutputsDirty = false;
        }
        promoteDueWakes(gameTime);
        return activeLinesView();
    }

    public static synchronized List<CachedEndpoint> globalItemOutputs(UUID lineId) {
        return activateGlobalOutputs(GLOBAL_ITEM_OUTPUT_LINES, GLOBAL_ITEM_OUTPUTS, lineId,
                LineIndex::priorityItemOutputs);
    }

    public static synchronized List<CachedEndpoint> globalItemInputs(MinecraftServer server, UUID lineId) {
        rebuildDirty(server);
        List<CachedEndpoint> result = new ArrayList<>();
        for (DimensionIndex index : DIMENSIONS.values()) {
            LineIndex line = index.lines.get(lineId);
            if (line != null) result.addAll(line.itemInputsView());
        }
        return result;
    }

    public static synchronized List<CachedEndpoint> globalFluidOutputs(UUID lineId) {
        return activateGlobalOutputs(GLOBAL_FLUID_OUTPUT_LINES, GLOBAL_FLUID_OUTPUTS, lineId,
                LineIndex::priorityFluidOutputs);
    }

    public static synchronized List<CachedEndpoint> globalChemicalOutputs(UUID lineId) {
        return activateGlobalOutputs(GLOBAL_CHEMICAL_OUTPUT_LINES, GLOBAL_CHEMICAL_OUTPUTS, lineId,
                LineIndex::priorityChemicalOutputs);
    }

    public static synchronized List<CachedEndpoint> globalEnergyOutputs(UUID lineId) {
        return activateGlobalOutputs(GLOBAL_ENERGY_OUTPUT_LINES, GLOBAL_ENERGY_OUTPUTS, lineId,
                LineIndex::priorityEnergyOutputs);
    }

    public static synchronized List<CachedEndpoint> globalManaOutputs(UUID lineId) {
        return activateGlobalOutputs(GLOBAL_MANA_OUTPUT_LINES, GLOBAL_MANA_OUTPUTS, lineId,
                LineIndex::priorityManaOutputs);
    }

    public static synchronized List<CachedEndpoint> globalSourceOutputs(UUID lineId) {
        return activateGlobalOutputs(GLOBAL_SOURCE_OUTPUT_LINES, GLOBAL_SOURCE_OUTPUTS, lineId,
                LineIndex::prioritySourceOutputs);
    }

    public static synchronized LineStats lineStats(MinecraftServer server, UUID lineId) {
        rebuildDirty(server);
        int nodes = 0;
        int inputs = 0;
        int outputs = 0;
        for (Map.Entry<ResourceKey<Level>, DimensionIndex> entry : DIMENSIONS.entrySet()) {
            if (server.getLevel(entry.getKey()) == null) {
                continue;
            }
            LineIndex line = entry.getValue().lines.get(lineId);
            if (line != null) {
                nodes += line.nodeCount();
                inputs += line.inputCount();
                outputs += line.outputCount();
            }
        }
        inputs += SkyNecklaceTicker.activeExtractorCount(lineId);
        outputs += SkyNecklaceTicker.activeInserterCount(lineId);
        return new LineStats(nodes, inputs, outputs);
    }

    public static synchronized boolean lineHasExternalConnections(MinecraftServer server, UUID lineId,
            ResourceKey<Level> ignoredDimension, BlockPos ignoredNodePos) {
        if (server == null || lineId == null) {
            return false;
        }
        rebuildDirty(server);
        for (Map.Entry<ResourceKey<Level>, DimensionIndex> entry : DIMENSIONS.entrySet()) {
            if (server.getLevel(entry.getKey()) == null) {
                continue;
            }
            LineIndex line = entry.getValue().lines.get(lineId);
            if (line == null) {
                continue;
            }
            boolean ignoredDimensionMatches = entry.getKey().equals(ignoredDimension);
            if (hasEndpointOutsideNode(line.inputs(), ignoredDimensionMatches, ignoredNodePos)
                    || hasEndpointOutsideNode(line.outputs(), ignoredDimensionMatches, ignoredNodePos)) {
                return true;
            }
        }
        return SkyNecklaceTicker.activeExtractorCount(lineId) > 0
                || SkyNecklaceTicker.activeInserterCount(lineId) > 0;
    }

    public static synchronized void renameLine(MinecraftServer server, UUID lineId, String lineName) {
        renameLine(server, lineId, lineName, lineName);
    }

    public static synchronized void renameLine(MinecraftServer server, UUID lineId, String lineName,
            String assignedFallback) {
        if (server == null || lineId == null) {
            return;
        }
        SkyLineNames.Entry line = SkyLineNames.rename(server, lineId, lineName, assignedFallback);
        for (Map.Entry<ResourceKey<Level>, DimensionIndex> entry : DIMENSIONS.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            for (BlockPos pos : entry.getValue().nodes) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof SkyNodeBlockEntity node) {
                    node.lineNameChanged(lineId);
                }
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetworking.sendToPlayer(player, new LineNamePacket(lineId, line.assignedName(), line.displayName()));
        }
    }

    public static void syncLineName(ServerPlayer player, UUID lineId, String assignedFallback) {
        syncLineName(player, lineId, assignedFallback, assignedFallback);
    }

    public static void syncLineName(ServerPlayer player, UUID lineId, String assignedFallback, String displayFallback) {
        if (player == null || lineId == null || player.server == null) {
            return;
        }
        SkyLineNames.Entry line = SkyLineNames.ensure(player.server, lineId, assignedFallback, displayFallback);
        ModNetworking.sendToPlayer(player, new LineNamePacket(lineId, line.assignedName(), line.displayName()));
    }

    public static synchronized List<CachedEndpoint> lineItemOutputs(MinecraftServer server,
            ResourceKey<Level> dimension, UUID lineId) {
        rebuildDirty(server);
        if (server.getLevel(dimension) == null) {
            return List.of();
        }
        DimensionIndex index = DIMENSIONS.get(dimension);
        if (index == null) {
            return List.of();
        }
        LineIndex line = index.lines.get(lineId);
        return line == null ? List.of() : line.priorityItemOutputsView();
    }

    public static synchronized List<CachedEndpoint> lineItemInputs(MinecraftServer server,
            ResourceKey<Level> dimension, UUID lineId) {
        rebuildDirty(server);
        if (server.getLevel(dimension) == null) {
            return List.of();
        }
        DimensionIndex index = DIMENSIONS.get(dimension);
        if (index == null) {
            return List.of();
        }
        LineIndex line = index.lines.get(lineId);
        return line == null ? List.of() : line.itemInputsView();
    }

    public static synchronized List<LineFaceDetail> lineDetails(MinecraftServer server, UUID lineId, int limit) {
        rebuildDirty(server);
        List<LineFaceDetail> details = new ArrayList<>();
        if (limit <= 0) {
            return details;
        }
        for (Map.Entry<ResourceKey<Level>, DimensionIndex> entry : DIMENSIONS.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            String dimension = entry.getKey().location().toString();
            LineIndex line = entry.getValue().lines.get(lineId);
            if (line == null) {
                continue;
            }
            addLineDetails(level, dimension, line.inputs(), details, limit);
            if (details.size() >= limit) {
                details.sort(LineFaceDetail::compare);
                return details;
            }
            addLineDetails(level, dimension, line.outputs(), details, limit);
            if (details.size() >= limit) {
                details.sort(LineFaceDetail::compare);
                return details;
            }
        }
        details.sort(LineFaceDetail::compare);
        return details;
    }

    private static void addLineDetails(ServerLevel level, String dimension, List<CachedEndpoint> endpoints,
            List<LineFaceDetail> details, int limit) {
        for (CachedEndpoint endpoint : endpoints) {
            if (details.size() >= limit) {
                return;
            }
            NetworkEndpointBlockEntity node = endpoint.node();
            Direction direction = endpoint.direction();
            NodeFaceMode faceMode = node.getFaceMode(direction);
            if (faceMode == NodeFaceMode.NONE || !level.isLoaded(node.getBlockPos())) {
                continue;
            }
            BlockPos targetPos = node.getTargetPos(direction);
            ResourceLocation targetId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(targetPos).getBlock());
            details.add(new LineFaceDetail(dimension, node.getBlockPos().immutable(), direction, targetPos.immutable(),
                    targetId == null ? "unknown" : targetId.toString(), faceMode,
                    node.isItemsEnabled(direction), node.isFluidsEnabled(direction), node.isEnergyEnabled(direction),
                    node.getRedstoneControl(direction), node.getPriority(direction)));
        }
    }

    private static boolean hasEndpointOutsideNode(List<CachedEndpoint> endpoints, boolean ignoredDimensionMatches,
            BlockPos ignoredNodePos) {
        for (CachedEndpoint endpoint : endpoints) {
            if (!ignoredDimensionMatches || ignoredNodePos == null
                    || !endpoint.node().getBlockPos().equals(ignoredNodePos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rebuildDirty(MinecraftServer server) {
        boolean rebuilt = false;
        for (Map.Entry<ResourceKey<Level>, DimensionIndex> entry : DIMENSIONS.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }
            DimensionIndex index = entry.getValue();
            if (index.dirty) {
                rebuild(level, index);
                rebuilt = true;
            }
        }
        return rebuilt;
    }

    public static synchronized void clear() {
        DIMENSIONS.clear();
        ACTIVE_LINES.clear();
        ACTIVE_LINE_SNAPSHOT.clear();
        WAKE_BUCKETS.clear();
        SCHEDULED_WAKE.clear();
        GLOBAL_ITEM_OUTPUTS.clear();
        GLOBAL_FLUID_OUTPUTS.clear();
        GLOBAL_CHEMICAL_OUTPUTS.clear();
        GLOBAL_ENERGY_OUTPUTS.clear();
        GLOBAL_MANA_OUTPUTS.clear();
        GLOBAL_SOURCE_OUTPUTS.clear();
        GLOBAL_ITEM_OUTPUT_LINES.clear();
        GLOBAL_FLUID_OUTPUT_LINES.clear();
        GLOBAL_CHEMICAL_OUTPUT_LINES.clear();
        GLOBAL_ENERGY_OUTPUT_LINES.clear();
        GLOBAL_MANA_OUTPUT_LINES.clear();
        GLOBAL_SOURCE_OUTPUT_LINES.clear();
        runtimeCachesDirty = true;
        globalOutputsDirty = true;
        activeLineSnapshotDirty = true;
        activeLineCursor = 0;
    }

    private static void markTopologyDirty(DimensionIndex index) {
        index.fullRebuild = true;
        index.dirty = true;
        runtimeCachesDirty = true;
        globalOutputsDirty = true;
    }

    private static void markNodeTopologyDirty(DimensionIndex index, BlockPos pos) {
        BlockPos immutablePos = pos.immutable();
        index.dirtyNodePositions.add(immutablePos);
        LineIndex oldLine = index.lineByNode.get(pos);
        NetworkEndpointBlockEntity endpoint = index.loadedEndpoints.get(pos);
        if (oldLine != null && endpoint != null && !oldLine.lineId().equals(endpoint.getLineId())) {
            index.dirtyNodeLines.add(oldLine.lineId());
            index.dirtyNodeLines.add(endpoint.getLineId());
        }
        index.dirty = true;
    }

    private static void markPipeTopologyDirty(DimensionIndex index, BlockPos pos) {
        if (index.rebuildingPipes) return;
        index.dirtyPipePositions.add(pos.immutable());
        UUID currentLine = index.pipeLineByPos.get(pos);
        if (currentLine != null) {
            index.dirtyPipeLines.add(currentLine);
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            index.dirtyPipePositions.add(neighbor.immutable());
            UUID neighborLine = index.pipeLineByPos.get(neighbor);
            if (neighborLine != null) {
                index.dirtyPipeLines.add(neighborLine);
            }
        }
        index.dirty = true;
    }

    private static void rebuild(ServerLevel level, DimensionIndex index) {
        if (!index.fullRebuild) {
            if (!index.dirtyPipePositions.isEmpty() || !index.dirtyPipeLines.isEmpty()) {
                rebuildPipeTopology(level, index);
            }
            if (!index.dirtyNodePositions.isEmpty() || !index.dirtyNodeLines.isEmpty()) {
                rebuildNodeTopology(index);
            }
            index.dirty = false;
            return;
        }
        Map<UUID, Long> retryAfterByLine = new HashMap<>();
        Map<EndpointKey, CachedEndpoint> reusableEndpoints = new HashMap<>();
        for (LineIndex line : index.lines.values()) {
            retryAfterByLine.put(line.lineId(), line.retryAfter);
            for (CachedEndpoint endpoint : line.inputs()) {
                reusableEndpoints.put(new EndpointKey(endpoint.node().getBlockPos(), endpoint.direction()), endpoint);
            }
            for (CachedEndpoint endpoint : line.outputs()) {
                reusableEndpoints.put(new EndpointKey(endpoint.node().getBlockPos(), endpoint.direction()), endpoint);
            }
        }
        index.lines.clear();
        index.lineByNode.clear();
        index.lineMembers.clear();
        Map<BlockPos, NetworkEndpointBlockEntity> loadedNodes = index.loadedEndpoints;
        Iterator<BlockPos> iterator = index.nodes.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof NetworkEndpointBlockEntity node)) {
                iterator.remove();
                loadedNodes.remove(pos);
                continue;
            }
            loadedNodes.put(pos, node);
        }
        index.rebuildingPipes = true;
        try {
            assignSimplePipeLineIds(level, index, loadedNodes);
        } finally {
            index.rebuildingPipes = false;
        }
        for (Map.Entry<BlockPos, NetworkEndpointBlockEntity> entry : loadedNodes.entrySet()) {
            BlockPos pos = entry.getKey();
            NetworkEndpointBlockEntity node = entry.getValue();
            LineIndex line = index.lines.computeIfAbsent(node.getLineId(), lineId -> {
                LineIndex created = new LineIndex(lineId);
                created.retryAfter = retryAfterByLine.getOrDefault(lineId, 0L);
                return created;
            });
            line.nodeCount++;
            index.lineByNode.put(pos, line);
            index.lineMembers.computeIfAbsent(node.getLineId(), ignored -> new HashSet<>()).add(pos);
            for (Direction direction : Direction.values()) {
                NodeFaceMode faceMode = node.getFaceMode(direction);
                if (faceMode == NodeFaceMode.INPUT) {
                    CachedEndpoint endpoint = reusableEndpoint(reusableEndpoints, node, direction);
                    line.addInput(endpoint);
                } else if (faceMode == NodeFaceMode.OUTPUT) {
                    CachedEndpoint endpoint = reusableEndpoint(reusableEndpoints, node, direction);
                    line.addOutput(endpoint);
                }
            }
        }
        for (LineIndex line : index.lines.values()) {
            line.rebuildPriorityOutputs();
        }
        index.dirtyNodePositions.clear();
        index.dirtyNodeLines.clear();
        index.fullRebuild = false;
        index.dirty = false;
    }

    private static CachedEndpoint reusableEndpoint(Map<EndpointKey, CachedEndpoint> reusableEndpoints,
            NetworkEndpointBlockEntity node, Direction direction) {
        CachedEndpoint endpoint = reusableEndpoints.remove(new EndpointKey(node.getBlockPos(), direction));
        if (endpoint != null && endpoint.node() == node) {
            return endpoint;
        }
        return new CachedEndpoint(node, direction);
    }

    private static void rebuildNodeTopology(DimensionIndex index) {
        if (index.dirtyNodeLines.isEmpty()) {
            rebuildDirtyNodePositions(index);
            return;
        }
        Set<UUID> oldLineIds = new HashSet<>(index.dirtyNodeLines);
        for (BlockPos pos : index.dirtyNodePositions) {
            LineIndex oldLine = index.lineByNode.get(pos);
            if (oldLine != null) {
                oldLineIds.add(oldLine.lineId());
            }
            NetworkEndpointBlockEntity endpoint = index.loadedEndpoints.get(pos);
            if (endpoint != null) {
                oldLineIds.add(endpoint.getLineId());
            }
        }

        Set<BlockPos> affectedPositions = new HashSet<>(index.dirtyNodePositions);
        Map<UUID, Long> retryAfterByLine = new HashMap<>();
        Map<EndpointKey, CachedEndpoint> reusableEndpoints = new HashMap<>();
        for (UUID lineId : oldLineIds) {
            Set<BlockPos> members = index.lineMembers.remove(lineId);
            if (members != null) {
                affectedPositions.addAll(members);
            }
            LineIndex oldLine = index.lines.remove(lineId);
            if (oldLine == null) continue;
            retryAfterByLine.put(lineId, oldLine.retryAfter);
            detachRuntimeLine(oldLine);
            for (CachedEndpoint endpoint : oldLine.inputs()) {
                if (!index.dirtyNodePositions.contains(endpoint.node().getBlockPos())) {
                    reusableEndpoints.put(new EndpointKey(endpoint.node().getBlockPos(), endpoint.direction()), endpoint);
                }
            }
            for (CachedEndpoint endpoint : oldLine.outputs()) {
                if (!index.dirtyNodePositions.contains(endpoint.node().getBlockPos())) {
                    reusableEndpoints.put(new EndpointKey(endpoint.node().getBlockPos(), endpoint.direction()), endpoint);
                }
            }
        }
        for (BlockPos pos : affectedPositions) {
            index.lineByNode.remove(pos);
        }

        Map<UUID, Set<BlockPos>> rebuiltMembers = new HashMap<>();
        for (BlockPos pos : affectedPositions) {
            NetworkEndpointBlockEntity node = index.loadedEndpoints.get(pos);
            if (node == null || node instanceof SimplePipeBlockEntity) continue;
            rebuiltMembers.computeIfAbsent(node.getLineId(), ignored -> new HashSet<>()).add(pos);
        }

        Set<UUID> changedLineIds = new HashSet<>(oldLineIds);
        changedLineIds.addAll(rebuiltMembers.keySet());
        for (Map.Entry<UUID, Set<BlockPos>> entry : rebuiltMembers.entrySet()) {
            UUID lineId = entry.getKey();
            LineIndex line = new LineIndex(lineId);
            line.retryAfter = retryAfterByLine.getOrDefault(lineId, 0L);
            for (BlockPos pos : entry.getValue()) {
                NetworkEndpointBlockEntity node = index.loadedEndpoints.get(pos);
                if (node == null) continue;
                line.nodeCount++;
                index.lineByNode.put(pos, line);
                for (Direction direction : Direction.values()) {
                    NodeFaceMode faceMode = node.getFaceMode(direction);
                    if (faceMode == NodeFaceMode.INPUT) {
                        line.addInput(reusableEndpoint(reusableEndpoints, node, direction));
                    } else if (faceMode == NodeFaceMode.OUTPUT) {
                        line.addOutput(reusableEndpoint(reusableEndpoints, node, direction));
                    }
                }
            }
            line.rebuildPriorityOutputs();
            index.lineMembers.put(lineId, entry.getValue());
            index.lines.put(lineId, line);
            attachRuntimeLine(line);
        }
        index.dirtyNodePositions.clear();
        index.dirtyNodeLines.clear();
        refreshGlobalLineIds(changedLineIds);
    }

    private static void rebuildDirtyNodePositions(DimensionIndex index) {
        Set<LineIndex> changedLines = new HashSet<>();
        Set<UUID> changedLineIds = new HashSet<>();
        for (BlockPos pos : index.dirtyNodePositions) {
            LineIndex oldLine = index.lineByNode.remove(pos);
            if (oldLine != null) {
                if (changedLines.add(oldLine)) detachRuntimeLine(oldLine);
                changedLineIds.add(oldLine.lineId());
                Set<BlockPos> members = index.lineMembers.get(oldLine.lineId());
                if (members != null && members.remove(pos)) oldLine.nodeCount--;
                oldLine.removeNodeEndpoints(pos);
            }

            NetworkEndpointBlockEntity node = index.loadedEndpoints.get(pos);
            if (node == null || node instanceof SimplePipeBlockEntity) continue;
            UUID lineId = node.getLineId();
            LineIndex line = index.lines.computeIfAbsent(lineId, LineIndex::new);
            if (changedLines.add(line)) detachRuntimeLine(line);
            changedLineIds.add(lineId);
            Set<BlockPos> members = index.lineMembers.computeIfAbsent(lineId, ignored -> new HashSet<>());
            if (members.add(pos)) line.nodeCount++;
            index.lineByNode.put(pos, line);
            for (Direction direction : Direction.values()) {
                NodeFaceMode faceMode = node.getFaceMode(direction);
                if (faceMode == NodeFaceMode.INPUT) {
                    line.addInput(new CachedEndpoint(node, direction));
                } else if (faceMode == NodeFaceMode.OUTPUT) {
                    CachedEndpoint endpoint = new CachedEndpoint(node, direction);
                    line.addOutput(endpoint);
                    line.addPriorityOutput(endpoint);
                }
            }
        }
        for (LineIndex line : changedLines) {
            Set<BlockPos> members = index.lineMembers.get(line.lineId());
            if (members == null || members.isEmpty()) {
                index.lineMembers.remove(line.lineId());
                index.lines.remove(line.lineId(), line);
                continue;
            }
            line.refreshResourceMasks();
            attachRuntimeLine(line);
        }
        index.dirtyNodePositions.clear();
        refreshGlobalLineIds(changedLineIds);
    }

    private static void rebuildPipeTopology(ServerLevel level, DimensionIndex index) {
        int configuredMax = SkyLogisticsConfig.simplePipeMaxConnectedBlocks();
        if (index.pipeMaxConnected != configuredMax) {
            index.dirtyPipeLines.addAll(index.pipeMembers.keySet());
            for (Set<BlockPos> members : index.pipeMembers.values()) {
                index.dirtyPipePositions.addAll(members);
            }
        }
        Set<UUID> oldLineIds = new HashSet<>(index.dirtyPipeLines);
        for (BlockPos pos : index.dirtyPipePositions) {
            UUID lineId = index.pipeLineByPos.get(pos);
            if (lineId != null) oldLineIds.add(lineId);
        }
        Set<BlockPos> affectedPositions = new HashSet<>(index.dirtyPipePositions);
        Map<UUID, Long> retryAfterByLine = new HashMap<>();
        Map<EndpointKey, CachedEndpoint> reusableEndpoints = new HashMap<>();
        for (UUID lineId : oldLineIds) {
            index.lineMembers.remove(lineId);
            Set<BlockPos> members = index.pipeMembers.get(lineId);
            if (members != null) affectedPositions.addAll(members);
            LineIndex oldLine = index.lines.remove(lineId);
            if (oldLine == null) continue;
            retryAfterByLine.put(lineId, oldLine.retryAfter);
            detachRuntimeLine(oldLine);
            for (CachedEndpoint endpoint : oldLine.inputs()) {
                if (!index.dirtyPipePositions.contains(endpoint.node().getBlockPos())) {
                    reusableEndpoints.put(new EndpointKey(endpoint.node().getBlockPos(), endpoint.direction()), endpoint);
                }
            }
            for (CachedEndpoint endpoint : oldLine.outputs()) {
                if (!index.dirtyPipePositions.contains(endpoint.node().getBlockPos())) {
                    reusableEndpoints.put(new EndpointKey(endpoint.node().getBlockPos(), endpoint.direction()), endpoint);
                }
            }
        }
        for (BlockPos pos : affectedPositions) index.lineByNode.remove(pos);
        index.rebuildingPipes = true;
        try {
            assignSimplePipeLineIds(level, index, index.loadedEndpoints);
        } finally {
            index.rebuildingPipes = false;
        }
        Set<UUID> changedLineIds = new HashSet<>(oldLineIds);
        for (BlockPos pos : affectedPositions) {
            UUID lineId = index.pipeLineByPos.get(pos);
            if (lineId != null) changedLineIds.add(lineId);
        }
        for (UUID lineId : changedLineIds) {
            Set<BlockPos> members = index.pipeMembers.get(lineId);
            if (members == null) continue;
            LineIndex line = new LineIndex(lineId);
            line.retryAfter = retryAfterByLine.getOrDefault(lineId, 0L);
            for (BlockPos pos : members) {
                NetworkEndpointBlockEntity node = index.loadedEndpoints.get(pos);
                if (node == null) continue;
                line.nodeCount++;
                index.lineByNode.put(pos, line);
                for (Direction direction : Direction.values()) {
                    NodeFaceMode faceMode = node.getFaceMode(direction);
                    if (faceMode == NodeFaceMode.INPUT) {
                        line.addInput(reusableEndpoint(reusableEndpoints, node, direction));
                    } else if (faceMode == NodeFaceMode.OUTPUT) {
                        line.addOutput(reusableEndpoint(reusableEndpoints, node, direction));
                    }
                }
            }
            line.rebuildPriorityOutputs();
            index.lineMembers.put(lineId, new HashSet<>(members));
            index.lines.put(lineId, line);
            attachRuntimeLine(line);
        }
        refreshGlobalLineIds(changedLineIds);
    }

    private static void detachRuntimeLine(LineIndex line) {
        if (ACTIVE_LINES.remove(line)) activeLineSnapshotDirty = true;
        removeScheduledWake(line);
    }

    private static void attachRuntimeLine(LineIndex line) {
        if (!line.hasProcessableInputs()) return;
        if (line.retryAfter <= 0L) {
            if (ACTIVE_LINES.add(line)) activeLineSnapshotDirty = true;
        } else {
            scheduleWake(line, line.retryAfter);
        }
    }

    private static void refreshGlobalLineIds(Set<UUID> lineIds) {
        for (UUID lineId : lineIds) {
            resetTargetScans(lineId);
            refreshGlobalOutputIfActive(GLOBAL_ITEM_OUTPUT_LINES, GLOBAL_ITEM_OUTPUTS, lineId,
                    LineIndex::priorityItemOutputs);
            refreshGlobalOutputIfActive(GLOBAL_FLUID_OUTPUT_LINES, GLOBAL_FLUID_OUTPUTS, lineId,
                    LineIndex::priorityFluidOutputs);
            refreshGlobalOutputIfActive(GLOBAL_CHEMICAL_OUTPUT_LINES, GLOBAL_CHEMICAL_OUTPUTS, lineId,
                    LineIndex::priorityChemicalOutputs);
            refreshGlobalOutputIfActive(GLOBAL_ENERGY_OUTPUT_LINES, GLOBAL_ENERGY_OUTPUTS, lineId,
                    LineIndex::priorityEnergyOutputs);
            refreshGlobalOutputIfActive(GLOBAL_MANA_OUTPUT_LINES, GLOBAL_MANA_OUTPUTS, lineId,
                    LineIndex::priorityManaOutputs);
            refreshGlobalOutputIfActive(GLOBAL_SOURCE_OUTPUT_LINES, GLOBAL_SOURCE_OUTPUTS, lineId,
                    LineIndex::prioritySourceOutputs);
        }
    }

    private static void assignSimplePipeLineIds(ServerLevel level, DimensionIndex index,
            Map<BlockPos, NetworkEndpointBlockEntity> loadedNodes) {
        int maxConnected = SkyLogisticsConfig.simplePipeMaxConnectedBlocks();
        if (index.pipeMaxConnected != maxConnected) {
            index.dirtyPipeLines.addAll(index.pipeMembers.keySet());
            for (Set<BlockPos> members : index.pipeMembers.values()) {
                index.dirtyPipePositions.addAll(members);
            }
            index.pipeMaxConnected = maxConnected;
        }
        TreeSet<BlockPos> candidates = new TreeSet<>(SkyNetworkRegistry::comparePositions);
        candidates.addAll(index.dirtyPipePositions);
        Set<UUID> linesToRemove = new HashSet<>(index.dirtyPipeLines);
        for (BlockPos pos : index.dirtyPipePositions) {
            UUID lineId = index.pipeLineByPos.get(pos);
            if (lineId != null) {
                linesToRemove.add(lineId);
            }
        }
        for (UUID lineId : linesToRemove) {
            Set<BlockPos> members = index.pipeMembers.remove(lineId);
            if (members != null) {
                candidates.addAll(members);
                for (BlockPos member : members) {
                    index.pipeLineByPos.remove(member);
                }
            }
        }
        index.dirtyPipeLines.clear();
        index.dirtyPipePositions.clear();

        Set<BlockPos> rebuilt = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        while (!candidates.isEmpty()) {
            BlockPos start = candidates.pollFirst();
            SimplePipeBlockEntity first = simplePipeAt(loadedNodes, start);
            if (first == null || rebuilt.contains(start) || index.pipeLineByPos.containsKey(start)) {
                continue;
            }
            pending.clear();
            List<BlockPos> component = new ArrayList<>(Math.min(maxConnected, 64));
            pending.add(start);
            BlockPos root = start;
            while (!pending.isEmpty() && component.size() < maxConnected) {
                BlockPos currentPos = pending.removeFirst();
                if (!rebuilt.add(currentPos)) {
                    continue;
                }
                SimplePipeBlockEntity current = simplePipeAt(loadedNodes, currentPos);
                if (current == null || current.pipeType() != first.pipeType()) {
                    continue;
                }
                component.add(currentPos);
                if (comparePositions(currentPos, root) < 0) {
                    root = currentPos;
                }
                BlockState state = current.getBlockState();
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = currentPos.relative(direction);
                    if (state.getValue(SimplePipeBlock.connectionProperty(direction))
                            && simplePipeAt(loadedNodes, neighbor) != null) {
                        if (rebuilt.contains(neighbor)) {
                            continue;
                        }
                        UUID existingLine = index.pipeLineByPos.get(neighbor);
                        if (existingLine != null) {
                            Set<BlockPos> absorbed = index.pipeMembers.remove(existingLine);
                            if (absorbed != null) {
                                for (BlockPos member : absorbed) {
                                    index.pipeLineByPos.remove(member);
                                    candidates.add(member);
                                }
                            }
                        }
                        pending.addLast(neighbor);
                    }
                }
            }
            candidates.addAll(pending);
            disconnectOverflowPipeEdges(level, loadedNodes, component);
            String seed = "skylogistics:simple_pipe:" + level.dimension().location() + ":"
                    + first.pipeType().name() + ":" + root.getX() + ":" + root.getY() + ":" + root.getZ();
            UUID lineId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
            Set<BlockPos> members = new HashSet<>(component);
            index.pipeMembers.put(lineId, members);
            for (BlockPos member : members) {
                index.pipeLineByPos.put(member, lineId);
            }
        }
        for (Set<BlockPos> members : index.pipeMembers.values()) {
            normalizePipeOwner(loadedNodes, members);
        }
        for (BlockPos pos : rebuilt) {
            SimplePipeBlockEntity pipe = simplePipeAt(loadedNodes, pos);
            UUID lineId = index.pipeLineByPos.get(pos);
            if (pipe != null && lineId != null) {
                pipe.assignNetworkLineId(lineId);
            }
        }
    }

    private static SimplePipeBlockEntity simplePipeAt(
            Map<BlockPos, NetworkEndpointBlockEntity> loadedNodes, BlockPos pos) {
        NetworkEndpointBlockEntity endpoint = loadedNodes.get(pos);
        return endpoint instanceof SimplePipeBlockEntity pipe ? pipe : null;
    }

    private static void normalizePipeOwner(Map<BlockPos, NetworkEndpointBlockEntity> loadedNodes,
            Set<BlockPos> members) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (BlockPos member : members) {
            SimplePipeBlockEntity pipe = simplePipeAt(loadedNodes, member);
            if (pipe != null && pipe.ownerId() != null) counts.merge(pipe.ownerId(), 1, Integer::sum);
        }
        UUID ownerId = counts.entrySet().stream()
                .max(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(entry -> entry.getKey().toString(), Comparator.reverseOrder()))
                .map(Map.Entry::getKey).orElse(null);
        if (ownerId == null) return;
        for (BlockPos member : members) {
            SimplePipeBlockEntity pipe = simplePipeAt(loadedNodes, member);
            if (pipe != null) pipe.assignOwnerId(ownerId);
        }
    }

    private static void disconnectOverflowPipeEdges(ServerLevel level,
            Map<BlockPos, NetworkEndpointBlockEntity> loadedNodes, List<BlockPos> component) {
        Set<BlockPos> members = new HashSet<>(component);
        for (BlockPos member : component) {
            SimplePipeBlockEntity pipe = simplePipeAt(loadedNodes, member);
            if (pipe == null) {
                continue;
            }
            BlockState state = level.getBlockState(member);
            for (Direction direction : Direction.values()) {
                if (!state.getValue(SimplePipeBlock.connectionProperty(direction))) {
                    continue;
                }
                BlockPos neighborPos = member.relative(direction);
                SimplePipeBlockEntity neighbor = simplePipeAt(loadedNodes, neighborPos);
                if (neighbor == null || neighbor.pipeType() != pipe.pipeType() || members.contains(neighborPos)) {
                    continue;
                }
                pipe.setSideDisconnected(direction, true,
                        com.skylogistics.util.SimplePipeConnection.PIPE);
                neighbor.setSideDisconnected(direction.getOpposite(), true,
                        com.skylogistics.util.SimplePipeConnection.PIPE);
                state = state.setValue(SimplePipeBlock.connectionProperty(direction), false);
                BlockState neighborState = level.getBlockState(neighborPos).setValue(
                        SimplePipeBlock.connectionProperty(direction.getOpposite()), false);
                level.setBlock(neighborPos, neighborState, Block.UPDATE_ALL);
            }
            if (state != level.getBlockState(member)) {
                level.setBlock(member, state, Block.UPDATE_ALL);
            }
        }
    }

    private static int comparePositions(BlockPos left, BlockPos right) {
        int x = Integer.compare(left.getX(), right.getX());
        if (x != 0) {
            return x;
        }
        int y = Integer.compare(left.getY(), right.getY());
        return y != 0 ? y : Integer.compare(left.getZ(), right.getZ());
    }

    private static void rebuildRuntimeCaches(MinecraftServer server) {
        ACTIVE_LINES.clear();
        WAKE_BUCKETS.clear();
        SCHEDULED_WAKE.clear();
        activeLineSnapshotDirty = true;
        activeLineCursor = 0;
        for (Map.Entry<ResourceKey<Level>, DimensionIndex> entry : DIMENSIONS.entrySet()) {
            if (server.getLevel(entry.getKey()) == null) {
                continue;
            }
            DimensionIndex index = entry.getValue();
            for (LineIndex line : index.lines.values()) {
                if (line.hasProcessableInputs()) {
                    if (line.retryAfter <= 0L) {
                        ACTIVE_LINES.add(line);
                    } else {
                        scheduleWake(line, line.retryAfter);
                    }
                }
            }
        }
    }

    private static void rebuildGlobalOutputs() {
        refreshGlobalOutputs(GLOBAL_ITEM_OUTPUT_LINES, GLOBAL_ITEM_OUTPUTS, LineIndex::priorityItemOutputs);
        refreshGlobalOutputs(GLOBAL_FLUID_OUTPUT_LINES, GLOBAL_FLUID_OUTPUTS, LineIndex::priorityFluidOutputs);
        refreshGlobalOutputs(GLOBAL_CHEMICAL_OUTPUT_LINES, GLOBAL_CHEMICAL_OUTPUTS,
                LineIndex::priorityChemicalOutputs);
        refreshGlobalOutputs(GLOBAL_ENERGY_OUTPUT_LINES, GLOBAL_ENERGY_OUTPUTS, LineIndex::priorityEnergyOutputs);
        refreshGlobalOutputs(GLOBAL_MANA_OUTPUT_LINES, GLOBAL_MANA_OUTPUTS, LineIndex::priorityManaOutputs);
        refreshGlobalOutputs(GLOBAL_SOURCE_OUTPUT_LINES, GLOBAL_SOURCE_OUTPUTS, LineIndex::prioritySourceOutputs);
    }

    private static List<CachedEndpoint> activateGlobalOutputs(Set<UUID> activeLines,
            Map<UUID, List<CachedEndpoint>> globalOutputs, UUID lineId,
            Function<LineIndex, List<CachedEndpoint>> outputSelector) {
        if (activeLines.add(lineId)) {
            refreshGlobalOutput(globalOutputs, lineId, outputSelector);
        }
        return globalOutputs.get(lineId);
    }

    private static void refreshGlobalOutputs(Set<UUID> activeLines,
            Map<UUID, List<CachedEndpoint>> globalOutputs,
            Function<LineIndex, List<CachedEndpoint>> outputSelector) {
        for (UUID lineId : activeLines) {
            refreshGlobalOutput(globalOutputs, lineId, outputSelector);
        }
    }

    private static void refreshGlobalOutputIfActive(Set<UUID> activeLines,
            Map<UUID, List<CachedEndpoint>> globalOutputs, UUID lineId,
            Function<LineIndex, List<CachedEndpoint>> outputSelector) {
        if (activeLines.contains(lineId)) {
            refreshGlobalOutput(globalOutputs, lineId, outputSelector);
        }
    }

    private static void refreshGlobalOutput(Map<UUID, List<CachedEndpoint>> globalOutputs, UUID lineId,
            Function<LineIndex, List<CachedEndpoint>> outputSelector) {
        List<CachedEndpoint> outputs = globalOutputs.computeIfAbsent(lineId, ignored -> new ArrayList<>());
        outputs.clear();
        for (DimensionIndex dimension : DIMENSIONS.values()) {
            LineIndex line = dimension.lines.get(lineId);
            if (line != null) {
                outputs.addAll(outputSelector.apply(line));
            }
        }
        sortByPriority(outputs);
    }

    private static void sortByPriority(List<CachedEndpoint> endpoints) {
        endpoints.sort(Comparator.comparingInt(
                (CachedEndpoint endpoint) -> endpoint.node().getPriority(endpoint.direction())).reversed());
    }

    private static ReadyLines activeLinesView() {
        if (ACTIVE_LINES.isEmpty()) {
            ACTIVE_LINE_SNAPSHOT.clear();
            activeLineSnapshotDirty = false;
            return ReadyLines.EMPTY;
        }
        if (activeLineSnapshotDirty) {
            ACTIVE_LINE_SNAPSHOT.clear();
            ACTIVE_LINE_SNAPSHOT.addAll(ACTIVE_LINES);
            activeLineSnapshotDirty = false;
            activeLineCursor = Math.floorMod(activeLineCursor, ACTIVE_LINE_SNAPSHOT.size());
        }
        int start = Math.floorMod(activeLineCursor, ACTIVE_LINE_SNAPSHOT.size());
        activeLineCursor = (start + 1) % ACTIVE_LINE_SNAPSHOT.size();
        return new ReadyLines(ACTIVE_LINE_SNAPSHOT, start);
    }

    private static LineIndex findLine(ServerLevel level, BlockPos pos) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        if (index == null || index.dirty) {
            return null;
        }
        return index.lineByNode.get(pos);
    }

    private static void promoteDueWakes(long gameTime) {
        while (!WAKE_BUCKETS.isEmpty()) {
            Map.Entry<Long, Set<LineIndex>> entry = WAKE_BUCKETS.firstEntry();
            if (entry.getKey() > gameTime) {
                break;
            }
            for (LineIndex line : entry.getValue()) {
                SCHEDULED_WAKE.remove(line);
                if (line.hasProcessableInputs() && ACTIVE_LINES.add(line)) {
                    activeLineSnapshotDirty = true;
                }
            }
            WAKE_BUCKETS.pollFirstEntry();
        }
    }

    private static synchronized void wakeLine(LineIndex line) {
        line.retryAfter = 0L;
        removeScheduledWake(line);
        if (line.hasProcessableInputs() && ACTIVE_LINES.add(line)) {
            activeLineSnapshotDirty = true;
        }
    }

    private static synchronized void sleepLine(LineIndex line, long gameTime) {
        line.retryAfter = Math.max(0L, gameTime);
        if (ACTIVE_LINES.remove(line)) {
            activeLineSnapshotDirty = true;
        }
        removeScheduledWake(line);
        if (line.hasProcessableInputs()) {
            scheduleWake(line, line.retryAfter);
        }
    }

    private static void scheduleWake(LineIndex line, long gameTime) {
        long wake = Math.max(0L, gameTime);
        SCHEDULED_WAKE.put(line, wake);
        WAKE_BUCKETS.computeIfAbsent(wake, ignored -> new HashSet<>()).add(line);
    }

    private static void removeScheduledWake(LineIndex line) {
        Long wake = SCHEDULED_WAKE.remove(line);
        if (wake == null) {
            return;
        }
        Set<LineIndex> bucket = WAKE_BUCKETS.get(wake);
        if (bucket == null) {
            return;
        }
        bucket.remove(line);
        if (bucket.isEmpty()) {
            WAKE_BUCKETS.remove(wake);
        }
    }

    private static final class DimensionIndex {
        private final Set<BlockPos> nodes = new HashSet<>();
        private final Map<BlockPos, NetworkEndpointBlockEntity> loadedEndpoints = new HashMap<>();
        private final Map<UUID, LineIndex> lines = new HashMap<>();
        private final Map<BlockPos, LineIndex> lineByNode = new HashMap<>();
        private final Map<UUID, Set<BlockPos>> lineMembers = new HashMap<>();
        private final Map<BlockPos, UUID> pipeLineByPos = new HashMap<>();
        private final Map<UUID, Set<BlockPos>> pipeMembers = new HashMap<>();
        private final Set<BlockPos> dirtyPipePositions = new HashSet<>();
        private final Set<UUID> dirtyPipeLines = new HashSet<>();
        private final Set<BlockPos> dirtyNodePositions = new HashSet<>();
        private final Set<UUID> dirtyNodeLines = new HashSet<>();
        private int pipeMaxConnected = -1;
        private boolean fullRebuild = true;
        private boolean rebuildingPipes;
        private boolean dirty = true;
    }

    private record EndpointKey(BlockPos pos, Direction direction) {
    }

    public record LineStats(int nodes, int inputs, int outputs) {
    }

    public record PipeLineInfo(UUID lineId, int size) {
    }

    public record LineFaceDetail(String dimension, BlockPos nodePos, Direction face, BlockPos targetPos,
                                 String targetBlockId, NodeFaceMode mode, boolean itemsEnabled,
                                 boolean fluidsEnabled, boolean energyEnabled, RedstoneControl redstoneControl,
                                 int priority) {
        private static int compare(LineFaceDetail left, LineFaceDetail right) {
            int result = left.dimension.compareTo(right.dimension);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(left.nodePos.getX(), right.nodePos.getX());
            if (result != 0) {
                return result;
            }
            result = Integer.compare(left.nodePos.getY(), right.nodePos.getY());
            if (result != 0) {
                return result;
            }
            result = Integer.compare(left.nodePos.getZ(), right.nodePos.getZ());
            if (result != 0) {
                return result;
            }
            return Integer.compare(left.face.ordinal(), right.face.ordinal());
        }
    }

    public static final class ReadyLines {
        private static final ReadyLines EMPTY = new ReadyLines(List.of(), 0);

        private final List<LineIndex> lines;
        private final int start;

        private ReadyLines(List<LineIndex> lines, int start) {
            this.lines = lines;
            this.start = start;
        }

        public int size() {
            return lines.size();
        }

        public LineIndex get(int offset) {
            return lines.get((start + offset) % lines.size());
        }
    }

    public static final class LineIndex {
        private static final int RESOURCE_ITEMS = 1;
        private static final int RESOURCE_FLUIDS = 1 << 1;
        private static final int RESOURCE_CHEMICALS = 1 << 2;
        private static final int RESOURCE_ENERGY = 1 << 3;
        private static final int RESOURCE_MANA = 1 << 4;
        private static final int RESOURCE_SOURCE = 1 << 5;
        private final UUID lineId;
        private final List<CachedEndpoint> inputs = new ArrayList<>();
        private final List<CachedEndpoint> outputs = new ArrayList<>();
        private final List<CachedEndpoint> itemInputs = new ArrayList<>();
        private final List<CachedEndpoint> itemInputsView = Collections.unmodifiableList(itemInputs);
        private final List<CachedEndpoint> fluidInputs = new ArrayList<>();
        private final List<CachedEndpoint> chemicalInputs = new ArrayList<>();
        private final List<CachedEndpoint> energyInputs = new ArrayList<>();
        private final List<CachedEndpoint> manaInputs = new ArrayList<>();
        private final List<CachedEndpoint> sourceInputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityItemOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityItemOutputsView = Collections.unmodifiableList(priorityItemOutputs);
        private final List<CachedEndpoint> priorityFluidOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityChemicalOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityEnergyOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityManaOutputs = new ArrayList<>();
        private final List<CachedEndpoint> prioritySourceOutputs = new ArrayList<>();
        private long retryAfter;
        private int nodeCount;
        private int inputCursor;
        private int inputResourceMask;
        private int outputResourceMask;

        private LineIndex(UUID lineId) {
            this.lineId = lineId;
        }

        public UUID lineId() {
            return lineId;
        }

        public List<CachedEndpoint> inputs() {
            return inputs;
        }

        public int inputCount() {
            return inputs.size();
        }

        public int nodeCount() {
            return nodeCount;
        }

        public CachedEndpoint inputAt(int offset) {
            return inputs.get(Math.floorMod(inputCursor + offset, inputs.size()));
        }

        public void advanceInputCursor() {
            advanceInputCursor(1);
        }

        public void advanceInputCursor(int amount) {
            if (!inputs.isEmpty()) {
                inputCursor = Math.floorMod(inputCursor + Math.max(0, amount), inputs.size());
            }
        }

        public List<CachedEndpoint> outputs() {
            return outputs;
        }

        public int outputCount() {
            return outputs.size();
        }

        public List<CachedEndpoint> priorityItemOutputs() {
            return priorityItemOutputs;
        }

        private List<CachedEndpoint> itemInputsView() {
            return itemInputsView;
        }

        private List<CachedEndpoint> priorityItemOutputsView() {
            return priorityItemOutputsView;
        }

        public List<CachedEndpoint> priorityFluidOutputs() {
            return priorityFluidOutputs;
        }

        public List<CachedEndpoint> priorityChemicalOutputs() {
            return priorityChemicalOutputs;
        }

        public List<CachedEndpoint> priorityEnergyOutputs() {
            return priorityEnergyOutputs;
        }

        public List<CachedEndpoint> priorityManaOutputs() {
            return priorityManaOutputs;
        }

        public List<CachedEndpoint> prioritySourceOutputs() {
            return prioritySourceOutputs;
        }

        public boolean hasLocalItemRoute() {
            return hasLocalRoute(RESOURCE_ITEMS);
        }

        public boolean hasLocalFluidRoute() {
            return hasLocalRoute(RESOURCE_FLUIDS);
        }

        public boolean hasLocalChemicalRoute() {
            return hasLocalRoute(RESOURCE_CHEMICALS);
        }

        public boolean hasLocalEnergyRoute() {
            return hasLocalRoute(RESOURCE_ENERGY);
        }

        public boolean hasLocalManaRoute() {
            return hasLocalRoute(RESOURCE_MANA);
        }

        public boolean hasLocalSourceRoute() {
            return hasLocalRoute(RESOURCE_SOURCE);
        }

        private boolean hasLocalRoute(int resource) {
            return (inputResourceMask & outputResourceMask & resource) != 0;
        }

        public boolean hasProcessableInputs() {
            return !itemInputs.isEmpty() || !fluidInputs.isEmpty() || !chemicalInputs.isEmpty()
                    || !energyInputs.isEmpty() || !manaInputs.isEmpty() || !sourceInputs.isEmpty();
        }

        public boolean canProcess(long gameTime) {
            return gameTime >= retryAfter;
        }

        public void wakeNow() {
            SkyNetworkRegistry.wakeLine(this);
        }

        public void sleepUntil(long gameTime) {
            SkyNetworkRegistry.sleepLine(this, gameTime);
        }

        private void addInput(CachedEndpoint endpoint) {
            endpoint.clearTargetScans();
            inputs.add(endpoint);
            inputResourceMask |= addResourceEndpoint(endpoint, itemInputs, fluidInputs, chemicalInputs,
                    energyInputs, manaInputs, sourceInputs);
        }

        private void addOutput(CachedEndpoint endpoint) {
            outputs.add(endpoint);
            outputResourceMask |= addResourceEndpoint(endpoint, priorityItemOutputs, priorityFluidOutputs,
                    priorityChemicalOutputs, priorityEnergyOutputs, priorityManaOutputs, prioritySourceOutputs);
        }

        private void rebuildPriorityOutputs() {
            sortByPriority(priorityItemOutputs);
            sortByPriority(priorityFluidOutputs);
            sortByPriority(priorityChemicalOutputs);
            sortByPriority(priorityEnergyOutputs);
            sortByPriority(priorityManaOutputs);
            sortByPriority(prioritySourceOutputs);
        }

        private void removeNodeEndpoints(BlockPos pos) {
            inputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            outputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            itemInputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            fluidInputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            chemicalInputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            energyInputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            manaInputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            sourceInputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityItemOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityFluidOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityChemicalOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityEnergyOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityManaOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            prioritySourceOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
        }

        private void refreshResourceMasks() {
            inputResourceMask = resourceMask(itemInputs, fluidInputs, chemicalInputs, energyInputs, manaInputs,
                    sourceInputs);
            outputResourceMask = resourceMask(priorityItemOutputs, priorityFluidOutputs, priorityChemicalOutputs,
                    priorityEnergyOutputs, priorityManaOutputs, prioritySourceOutputs);
        }

        private void addPriorityOutput(CachedEndpoint endpoint) {
            priorityItemOutputs.remove(endpoint);
            priorityFluidOutputs.remove(endpoint);
            priorityChemicalOutputs.remove(endpoint);
            priorityEnergyOutputs.remove(endpoint);
            priorityManaOutputs.remove(endpoint);
            prioritySourceOutputs.remove(endpoint);
            NetworkEndpointBlockEntity node = endpoint.node();
            Direction direction = endpoint.direction();
            if (node.isItemsEnabled(direction)) insertByPriority(priorityItemOutputs, endpoint);
            if (node.isFluidsEnabled(direction)) {
                insertByPriority(priorityFluidOutputs, endpoint);
                if (SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()) {
                    insertByPriority(priorityChemicalOutputs, endpoint);
                }
            }
            if (node.isEnergyEnabled(direction)) {
                insertByPriority(priorityEnergyOutputs, endpoint);
                if (SkyLogisticsConfig.allowEnergyManaTransfer() && BotaniaCompat.isLoaded()) {
                    insertByPriority(priorityManaOutputs, endpoint);
                }
                if (SkyLogisticsConfig.allowEnergySourceTransfer() && ArsNouveauCompat.isLoaded()) {
                    insertByPriority(prioritySourceOutputs, endpoint);
                }
            }
        }

        private void refreshPriorityOutputs(BlockPos pos) {
            priorityItemOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityFluidOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityChemicalOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityEnergyOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            priorityManaOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            prioritySourceOutputs.removeIf(endpoint -> endpoint.node().getBlockPos().equals(pos));
            for (CachedEndpoint endpoint : outputs) {
                if (endpoint.node().getBlockPos().equals(pos)) addPriorityOutput(endpoint);
            }
        }

        private static void insertByPriority(List<CachedEndpoint> endpoints, CachedEndpoint endpoint) {
            int priority = endpoint.node().getPriority(endpoint.direction());
            int low = 0;
            int high = endpoints.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                CachedEndpoint candidate = endpoints.get(middle);
                int candidatePriority = candidate.node().getPriority(candidate.direction());
                if (candidatePriority >= priority) low = middle + 1;
                else high = middle;
            }
            endpoints.add(low, endpoint);
        }

        private static int resourceMask(List<CachedEndpoint> items, List<CachedEndpoint> fluids,
                List<CachedEndpoint> chemicals, List<CachedEndpoint> energy, List<CachedEndpoint> mana,
                List<CachedEndpoint> source) {
            int mask = 0;
            if (!items.isEmpty()) mask |= RESOURCE_ITEMS;
            if (!fluids.isEmpty()) mask |= RESOURCE_FLUIDS;
            if (!chemicals.isEmpty()) mask |= RESOURCE_CHEMICALS;
            if (!energy.isEmpty()) mask |= RESOURCE_ENERGY;
            if (!mana.isEmpty()) mask |= RESOURCE_MANA;
            if (!source.isEmpty()) mask |= RESOURCE_SOURCE;
            return mask;
        }

        private static int addResourceEndpoint(CachedEndpoint endpoint, List<CachedEndpoint> itemEndpoints,
                List<CachedEndpoint> fluidEndpoints, List<CachedEndpoint> chemicalEndpoints,
                List<CachedEndpoint> energyEndpoints, List<CachedEndpoint> manaEndpoints,
                List<CachedEndpoint> sourceEndpoints) {
            NetworkEndpointBlockEntity node = endpoint.node();
            Direction direction = endpoint.direction();
            int resourceMask = 0;
            if (node.isItemsEnabled(direction)) {
                itemEndpoints.add(endpoint);
                resourceMask |= RESOURCE_ITEMS;
            }
            if (node.isFluidsEnabled(direction)) {
                fluidEndpoints.add(endpoint);
                resourceMask |= RESOURCE_FLUIDS;
                if (SkyLogisticsConfig.allowFluidChemicalTransfer() && MekanismCompat.isLoaded()) {
                    chemicalEndpoints.add(endpoint);
                    resourceMask |= RESOURCE_CHEMICALS;
                }
            }
            if (node.isEnergyEnabled(direction)) {
                energyEndpoints.add(endpoint);
                resourceMask |= RESOURCE_ENERGY;
                if (SkyLogisticsConfig.allowEnergyManaTransfer() && BotaniaCompat.isLoaded()) {
                    manaEndpoints.add(endpoint);
                    resourceMask |= RESOURCE_MANA;
                }
                if (SkyLogisticsConfig.allowEnergySourceTransfer() && ArsNouveauCompat.isLoaded()) {
                    sourceEndpoints.add(endpoint);
                    resourceMask |= RESOURCE_SOURCE;
                }
            }
            return resourceMask;
        }

    }

    public static final class CachedEndpoint {
        private static final int CAPABILITY_ITEMS = 1;
        private static final int CAPABILITY_FLUIDS = 1 << 1;
        private static final int CAPABILITY_CHEMICALS = 1 << 2;
        private static final int CAPABILITY_ENERGY = 1 << 3;
        private static final int CAPABILITY_MANA = 1 << 4;
        private static final int CAPABILITY_SOURCE = 1 << 5;
        private static final long CAPABILITY_LIFECYCLE_CHECK_INTERVAL = 20L;
        private final NetworkEndpointBlockEntity node;
        private final Direction direction;
        private final BlockPos targetPos;
        private final Direction accessSide;
        private BlockCapabilityCache<IItemHandler, Direction> itemCache;
        private BlockCapabilityCache<IFluidHandler, Direction> fluidCache;
        private BlockCapabilityCache<IEnergyStorage, Direction> energyCache;
        private IItemHandler itemHandler;
        private IFluidHandler fluidHandler;
        private ChemicalHandlerBridge chemicalHandler;
        private BlockEntity chemicalTarget;
        private IEnergyStorage energyHandler;
        private ManaHandlerBridge manaHandler;
        private SourceHandlerBridge sourceHandler;
        private long itemRetryAfter;
        private long fluidRetryAfter;
        private long chemicalRetryAfter;
        private long energyRetryAfter;
        private long manaRetryAfter;
        private long sourceRetryAfter;
        private int itemFailures;
        private int fluidFailures;
        private int chemicalFailures;
        private int energyFailures;
        private int manaFailures;
        private int sourceFailures;
        private int externalItemCandidateCursor;
        private int externalFluidCandidateCursor;
        private int itemSourceMisses;
        private int fluidSourceMisses;
        private int chemicalSourceMisses;
        private int[] preferredItemSlots;
        private int[] preferredItemSlotMisses;
        private long[] preferredItemSlotTriedAt;
        private int preferredItemSlotCursor;
        private int preferredItemSlotWriteCursor;
        private int itemSlotDiscoveryRemaining;
        private int itemSlotDiscoveryDeferrals;
        private int[] emptyItemSlots;
        private long[] emptyItemSlotUntil;
        private int emptyItemSlotCursor;
        private int[] preferredFluidTanks;
        private int[] preferredFluidTankMisses;
        private int preferredFluidTankCursor;
        private int preferredFluidTankWriteCursor;
        private int fluidTankDiscoveryRemaining;
        private int fluidTankDiscoveryDeferrals;
        private int[] emptyFluidTanks;
        private long[] emptyFluidTankUntil;
        private int emptyFluidTankCursor;
        private int[] preferredChemicalTanks;
        private int[] preferredChemicalTankMisses;
        private int preferredChemicalTankCursor;
        private int preferredChemicalTankWriteCursor;
        private int chemicalTankDiscoveryRemaining;
        private int chemicalTankDiscoveryDeferrals;
        private int[] emptyChemicalTanks;
        private long[] emptyChemicalTankUntil;
        private int emptyChemicalTankCursor;
        private ItemStack[] rejectedItems;
        private long[] rejectedItemUntil;
        private int rejectedItemCursor;
        private ItemStackKey[] rejectedItemAccepts;
        private long[] rejectedItemAcceptUntil;
        private int[] rejectedItemAcceptFailures;
        private int rejectedItemAcceptCursor;
        private Item[] targetItemCursorOwners;
        private int[] targetItemHotSlots;
        private int[] targetItemScanCursors;
        private byte[] targetItemCursorModes;
        private int[] targetItemSequentialSuccesses;
        private int targetItemCursorSlotCount = -1;
        private FluidStackKey[] rejectedFluidAccepts;
        private long[] rejectedFluidAcceptUntil;
        private int[] rejectedFluidAcceptFailures;
        private int rejectedFluidAcceptCursor;
        private ChemicalStackView[] rejectedChemicalAccepts;
        private long[] rejectedChemicalAcceptUntil;
        private int[] rejectedChemicalAcceptFailures;
        private int rejectedChemicalAcceptCursor;
        private BudgetedScanCursors<ItemStackKey> itemTargetScanCursors;
        private BudgetedScanCursors<FluidStackKey> fluidTargetScanCursors;
        private BudgetedScanCursors<String> chemicalTargetScanCursors;
        private final int[] resourceTargetScanCursors =
                new int[NetworkEndpointBlockEntity.TargetResource.values().length];
        private int absentCapabilityMask;
        private BlockEntity capabilityTarget;
        private net.minecraft.world.level.block.state.BlockState capabilityTargetState;
        private final long[] capabilityLifecycleCheckAt = new long[6];
        private long chemicalHandlerValidateAt;
        private long manaHandlerValidateAt;
        private long sourceHandlerValidateAt;

        private CachedEndpoint(NetworkEndpointBlockEntity node, Direction direction) {
            this.node = node;
            this.direction = direction;
            this.targetPos = node.getTargetPos(direction);
            this.accessSide = node.getAccessSide(direction);
        }

        private void enableItemSourceCaching() {
            int configuredSize = SkyLogisticsConfig.preferredItemSlotCacheSize();
            if (preferredItemSlots != null) {
                if (preferredItemSlots.length != configuredSize) resizePreferredItemSlots(configuredSize);
                return;
            }
            preferredItemSlots = new int[configuredSize];
            preferredItemSlotMisses = new int[preferredItemSlots.length];
            preferredItemSlotTriedAt = new long[preferredItemSlots.length];
            emptyItemSlots = new int[EMPTY_ITEM_SLOT_CACHE_SIZE];
            emptyItemSlotUntil = new long[EMPTY_ITEM_SLOT_CACHE_SIZE];
            clearItemSlotCaches();
        }

        private void resizePreferredItemSlots(int configuredSize) {
            int[] previousSlots = preferredItemSlots;
            int[] previousMisses = preferredItemSlotMisses;
            long[] previousTriedAt = preferredItemSlotTriedAt;
            preferredItemSlots = new int[configuredSize];
            preferredItemSlotMisses = new int[configuredSize];
            preferredItemSlotTriedAt = new long[configuredSize];
            for (int i = 0; i < configuredSize; i++) {
                preferredItemSlots[i] = -1;
                preferredItemSlotTriedAt[i] = Long.MIN_VALUE;
            }
            int copied = 0;
            for (int i = 0; i < previousSlots.length && copied < configuredSize; i++) {
                int index = Math.floorMod(preferredItemSlotCursor + i, previousSlots.length);
                if (previousSlots[index] < 0) continue;
                preferredItemSlots[copied] = previousSlots[index];
                preferredItemSlotMisses[copied] = previousMisses[index];
                preferredItemSlotTriedAt[copied] = previousTriedAt[index];
                copied++;
            }
            preferredItemSlotCursor = 0;
            preferredItemSlotWriteCursor = copied < configuredSize ? copied : 0;
        }

        private void enableItemTargetCaching() {
            if (rejectedItemAccepts != null) return;
            int rejectedAcceptCacheSize = SkyLogisticsConfig.rejectedAcceptCacheSize();
            rejectedItemAccepts = new ItemStackKey[rejectedAcceptCacheSize];
            rejectedItemAcceptUntil = new long[rejectedAcceptCacheSize];
            rejectedItemAcceptFailures = new int[rejectedAcceptCacheSize];
            rejectedItems = new ItemStack[REJECTED_ITEM_CACHE_SIZE];
            rejectedItemUntil = new long[REJECTED_ITEM_CACHE_SIZE];
            for (int i = 0; i < rejectedItems.length; i++) rejectedItems[i] = ItemStack.EMPTY;
        }

        private void enableFluidSourceCaching() {
            if (preferredFluidTanks != null) {
                return;
            }
            preferredFluidTanks = new int[PREFERRED_FLUID_TANK_CACHE_SIZE];
            preferredFluidTankMisses = new int[PREFERRED_FLUID_TANK_CACHE_SIZE];
            emptyFluidTanks = new int[EMPTY_FLUID_TANK_CACHE_SIZE];
            emptyFluidTankUntil = new long[EMPTY_FLUID_TANK_CACHE_SIZE];
            clearFluidTankCaches();
        }

        private void enableFluidTargetCaching() {
            if (rejectedFluidAccepts != null) return;
            int rejectedAcceptCacheSize = SkyLogisticsConfig.rejectedAcceptCacheSize();
            rejectedFluidAccepts = new FluidStackKey[rejectedAcceptCacheSize];
            rejectedFluidAcceptUntil = new long[rejectedAcceptCacheSize];
            rejectedFluidAcceptFailures = new int[rejectedAcceptCacheSize];
        }

        private void enableChemicalSourceCaching() {
            if (preferredChemicalTanks != null) {
                return;
            }
            preferredChemicalTanks = new int[PREFERRED_CHEMICAL_TANK_CACHE_SIZE];
            preferredChemicalTankMisses = new int[PREFERRED_CHEMICAL_TANK_CACHE_SIZE];
            emptyChemicalTanks = new int[EMPTY_CHEMICAL_TANK_CACHE_SIZE];
            emptyChemicalTankUntil = new long[EMPTY_CHEMICAL_TANK_CACHE_SIZE];
            clearChemicalTankCaches();
        }

        private void enableChemicalTargetCaching() {
            if (rejectedChemicalAccepts != null) return;
            int rejectedAcceptCacheSize = SkyLogisticsConfig.rejectedAcceptCacheSize();
            rejectedChemicalAccepts = new ChemicalStackView[rejectedAcceptCacheSize];
            rejectedChemicalAcceptUntil = new long[rejectedAcceptCacheSize];
            rejectedChemicalAcceptFailures = new int[rejectedAcceptCacheSize];
        }

        public NetworkEndpointBlockEntity node() {
            return node;
        }

        public Direction direction() {
            return direction;
        }

        public int nextExternalItemCandidate(int candidateCount) {
            if (candidateCount <= 0) return 0;
            int candidate = Math.floorMod(externalItemCandidateCursor, candidateCount);
            externalItemCandidateCursor = (candidate + 1) % candidateCount;
            return candidate;
        }

        public int nextExternalFluidCandidate(int candidateCount) {
            if (candidateCount <= 0) return 0;
            int candidate = Math.floorMod(externalFluidCandidateCursor, candidateCount);
            externalFluidCandidateCursor = (candidate + 1) % candidateCount;
            return candidate;
        }

        public BlockEntity targetBlockEntity() {
            Level level = node.getLevel();
            if (level == null || !level.isLoaded(targetPos)) {
                return null;
            }
            return level.getBlockEntity(targetPos);
        }

        public boolean canTryItems(long gameTime) {
            return gameTime >= itemRetryAfter && capabilityMayExist(CAPABILITY_ITEMS, gameTime);
        }

        public boolean canTryFluids(long gameTime) {
            return gameTime >= fluidRetryAfter && capabilityMayExist(CAPABILITY_FLUIDS, gameTime);
        }

        public boolean canTryChemicals(long gameTime) {
            return gameTime >= chemicalRetryAfter && capabilityMayExist(CAPABILITY_CHEMICALS, gameTime);
        }

        public boolean canTryEnergy(long gameTime) {
            return gameTime >= energyRetryAfter && capabilityMayExist(CAPABILITY_ENERGY, gameTime);
        }

        public boolean canTryMana(long gameTime) {
            return gameTime >= manaRetryAfter && capabilityMayExist(CAPABILITY_MANA, gameTime);
        }

        public boolean canTrySource(long gameTime) {
            return gameTime >= sourceRetryAfter && capabilityMayExist(CAPABILITY_SOURCE, gameTime);
        }

        public long nextItemWake(long gameTime) {
            return nextCapabilityWake(CAPABILITY_ITEMS, itemRetryAfter, gameTime);
        }

        public long nextFluidWake(long gameTime) {
            return nextCapabilityWake(CAPABILITY_FLUIDS, fluidRetryAfter, gameTime);
        }

        public long nextChemicalWake(long gameTime) {
            return nextCapabilityWake(CAPABILITY_CHEMICALS, chemicalRetryAfter, gameTime);
        }

        public long nextEnergyWake(long gameTime) {
            return nextCapabilityWake(CAPABILITY_ENERGY, energyRetryAfter, gameTime);
        }

        public long nextManaWake(long gameTime) {
            return nextCapabilityWake(CAPABILITY_MANA, manaRetryAfter, gameTime);
        }

        public long nextSourceWake(long gameTime) {
            return nextCapabilityWake(CAPABILITY_SOURCE, sourceRetryAfter, gameTime);
        }

        private boolean capabilityMayExist(int capability, long gameTime) {
            if (capability == CAPABILITY_ITEMS || capability == CAPABILITY_FLUIDS
                    || capability == CAPABILITY_ENERGY) return true;
            if ((absentCapabilityMask & capability) == 0) return true;
            int capabilityIndex = Integer.numberOfTrailingZeros(capability);
            if (gameTime < capabilityLifecycleCheckAt[capabilityIndex]) return false;
            Level level = node.getLevel();
            capabilityLifecycleCheckAt[capabilityIndex] = gameTime + CAPABILITY_LIFECYCLE_CHECK_INTERVAL;
            if (level == null || !level.isLoaded(targetPos)) return false;
            BlockEntity currentTarget = level.getBlockEntity(targetPos);
            net.minecraft.world.level.block.state.BlockState currentState = level.getBlockState(targetPos);
            if (currentTarget == capabilityTarget && currentState == capabilityTargetState) {
                absentCapabilityMask &= ~capability;
                capabilityLifecycleCheckAt[capabilityIndex] = 0L;
                return true;
            }
            invalidateCapabilityKnowledge();
            return true;
        }

        private long nextCapabilityWake(int capability, long retryAfter, long gameTime) {
            long wake = retryAfter > gameTime ? retryAfter : gameTime;
            if (capability == CAPABILITY_ITEMS || capability == CAPABILITY_FLUIDS
                    || capability == CAPABILITY_ENERGY) return wake;
            long lifecycleCheckAt = capabilityLifecycleCheckAt[Integer.numberOfTrailingZeros(capability)];
            return (absentCapabilityMask & capability) != 0 && lifecycleCheckAt > wake
                    ? lifecycleCheckAt : wake;
        }

        private void recordCapabilityPresent(int capability) {
            absentCapabilityMask &= ~capability;
            capabilityLifecycleCheckAt[Integer.numberOfTrailingZeros(capability)] = 0L;
        }

        private void recordCapabilityAbsent(int capability, long gameTime) {
            if (capability == CAPABILITY_ITEMS || capability == CAPABILITY_FLUIDS
                    || capability == CAPABILITY_ENERGY) return;
            absentCapabilityMask |= capability;
            Level level = node.getLevel();
            if (level != null && level.isLoaded(targetPos)) {
                capabilityTarget = level.getBlockEntity(targetPos);
                capabilityTargetState = level.getBlockState(targetPos);
            }
            capabilityLifecycleCheckAt[Integer.numberOfTrailingZeros(capability)] =
                    gameTime + CAPABILITY_LIFECYCLE_CHECK_INTERVAL;
        }

        private void invalidateCapabilityKnowledge() {
            absentCapabilityMask = 0;
            capabilityTarget = null;
            capabilityTargetState = null;
            java.util.Arrays.fill(capabilityLifecycleCheckAt, 0L);
            clearItemCache();
            clearFluidCache();
            clearChemicalCache();
            clearEnergyCache();
            clearManaCache();
            clearSourceCache();
        }

        public IItemHandler itemHandler(long gameTime) {
            if (!canTryItems(gameTime)) {
                return null;
            }
            IItemHandler direct = node.getEndpointItemHandler(direction, gameTime);
            if (direct != null) {
                recordCapabilityPresent(CAPABILITY_ITEMS);
                return direct;
            }
            if (itemHandler != null) {
                return itemHandler;
            }
            Level level = node.getLevel();
            BlockCapabilityCache<IItemHandler, Direction> cache = itemCapabilityCache(level);
            if (cache == null) {
                recordItemFailure(gameTime);
                return null;
            }
            itemHandler = cache.getCapability();
            if (itemHandler == null) {
                recordCapabilityAbsent(CAPABILITY_ITEMS, gameTime);
                recordItemFailure(gameTime);
            } else {
                recordCapabilityPresent(CAPABILITY_ITEMS);
            }
            return itemHandler;
        }

        public IFluidHandler fluidHandler(long gameTime) {
            if (!canTryFluids(gameTime)) {
                return null;
            }
            IFluidHandler direct = node.getEndpointFluidHandler(direction, gameTime);
            if (direct != null) {
                recordCapabilityPresent(CAPABILITY_FLUIDS);
                return direct;
            }
            if (fluidHandler != null) {
                return fluidHandler;
            }
            Level level = node.getLevel();
            BlockCapabilityCache<IFluidHandler, Direction> cache = fluidCapabilityCache(level);
            if (cache == null) {
                recordFluidFailure(gameTime);
                return null;
            }
            fluidHandler = cache.getCapability();
            if (fluidHandler == null) {
                recordCapabilityAbsent(CAPABILITY_FLUIDS, gameTime);
                recordFluidFailure(gameTime);
            } else {
                recordCapabilityPresent(CAPABILITY_FLUIDS);
            }
            return fluidHandler;
        }

        public ChemicalHandlerBridge chemicalHandler(long gameTime) {
            if (!canTryChemicals(gameTime)
                    || !SkyLogisticsConfig.allowFluidChemicalTransfer()) {
                return null;
            }
            ChemicalHandlerBridge direct = node.getEndpointChemicalHandler(direction, gameTime);
            if (direct != null) {
                recordCapabilityPresent(CAPABILITY_CHEMICALS);
                return direct;
            }
            if (chemicalHandler != null && gameTime < chemicalHandlerValidateAt) {
                return chemicalHandler;
            }
            Level level = node.getLevel();
            if (level == null || !level.isLoaded(targetPos)) {
                recordChemicalFailure(gameTime);
                return null;
            }
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null) {
                recordChemicalFailure(gameTime);
                return null;
            }
            boolean preserveTankKnowledge = chemicalHandler != null && chemicalTarget == target;
            chemicalHandler = null;
            chemicalHandlerValidateAt = 0L;
            if (!preserveTankKnowledge) {
                clearChemicalTankCaches();
                clearRejectedChemicalAccepts();
            }
            chemicalTarget = target;
            chemicalHandler = MekanismCompat.chemicalHandler(level, targetPos, accessSide);
            chemicalHandlerValidateAt = gameTime + CAPABILITY_LIFECYCLE_CHECK_INTERVAL;
            if (chemicalHandler == null) {
                clearChemicalTankCaches();
                clearRejectedChemicalAccepts();
                recordCapabilityAbsent(CAPABILITY_CHEMICALS, gameTime);
                recordChemicalFailure(gameTime);
            } else {
                recordCapabilityPresent(CAPABILITY_CHEMICALS);
            }
            return chemicalHandler;
        }

        public IEnergyStorage energyHandler(long gameTime) {
            if (!canTryEnergy(gameTime)) {
                return null;
            }
            IEnergyStorage direct = node.getEndpointEnergyHandler(direction, gameTime);
            if (direct != null) {
                recordCapabilityPresent(CAPABILITY_ENERGY);
                return direct;
            }
            if (energyHandler != null) {
                return energyHandler;
            }
            Level level = node.getLevel();
            BlockCapabilityCache<IEnergyStorage, Direction> cache = energyCapabilityCache(level);
            if (cache == null) {
                recordEnergyFailure(gameTime);
                return null;
            }
            energyHandler = cache.getCapability();
            if (energyHandler == null) {
                recordCapabilityAbsent(CAPABILITY_ENERGY, gameTime);
                recordEnergyFailure(gameTime);
            } else {
                recordCapabilityPresent(CAPABILITY_ENERGY);
            }
            return energyHandler;
        }

        public ManaHandlerBridge manaHandler(long gameTime) {
            if (!canTryMana(gameTime)
                    || !SkyLogisticsConfig.allowEnergyManaTransfer()
                    || !BotaniaCompat.isLoaded()) {
                return null;
            }
            ManaHandlerBridge direct = node.getEndpointManaHandler(direction, gameTime);
            if (direct != null) {
                recordCapabilityPresent(CAPABILITY_MANA);
                return direct;
            }
            if (manaHandler != null && gameTime < manaHandlerValidateAt) {
                return manaHandler;
            }
            Level level = node.getLevel();
            if (level == null || !level.isLoaded(targetPos)) {
                recordManaFailure(gameTime);
                return null;
            }
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null) {
                recordManaFailure(gameTime);
                return null;
            }
            clearManaCache();
            manaHandler = BotaniaCompat.manaHandler(level, targetPos, accessSide);
            manaHandlerValidateAt = gameTime + CAPABILITY_LIFECYCLE_CHECK_INTERVAL;
            if (manaHandler == null) {
                recordCapabilityAbsent(CAPABILITY_MANA, gameTime);
                recordManaFailure(gameTime);
            } else {
                recordCapabilityPresent(CAPABILITY_MANA);
            }
            return manaHandler;
        }

        public SourceHandlerBridge sourceHandler(long gameTime) {
            if (!canTrySource(gameTime)
                    || !SkyLogisticsConfig.allowEnergySourceTransfer()
                    || !ArsNouveauCompat.isLoaded()) {
                return null;
            }
            SourceHandlerBridge direct = node.getEndpointSourceHandler(direction, gameTime);
            if (direct != null) {
                recordCapabilityPresent(CAPABILITY_SOURCE);
                return direct;
            }
            if (sourceHandler != null && gameTime < sourceHandlerValidateAt) {
                return sourceHandler;
            }
            Level level = node.getLevel();
            if (level == null || !level.isLoaded(targetPos)) {
                recordSourceFailure(gameTime);
                return null;
            }
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null) {
                recordSourceFailure(gameTime);
                return null;
            }
            clearSourceCache();
            sourceHandler = ArsNouveauCompat.sourceHandler(level, targetPos, accessSide);
            sourceHandlerValidateAt = gameTime + CAPABILITY_LIFECYCLE_CHECK_INTERVAL;
            if (sourceHandler == null) {
                recordCapabilityAbsent(CAPABILITY_SOURCE, gameTime);
                recordSourceFailure(gameTime);
            } else {
                recordCapabilityPresent(CAPABILITY_SOURCE);
            }
            return sourceHandler;
        }

        public void recordItemSuccess() {
            node.recordRecentTransfer(direction);
            itemFailures = 0;
            itemRetryAfter = 0L;
            itemSourceMisses = 0;
        }

        public void recordItemCandidateFound() {
            itemSourceMisses = 0;
        }

        public void recordItemSourceMiss(int checkedSlots, int totalSlots, long gameTime) {
            if (totalSlots <= 0) {
                recordItemFailure(gameTime);
                return;
            }
            itemSourceMisses += Math.max(0, checkedSlots);
            if (itemSourceMisses >= totalSlots) {
                itemSourceMisses = 0;
                recordItemFailure(gameTime);
            }
        }

        public void recordItemFailure(long gameTime) {
            itemFailures = Math.min(itemFailures + 1, MAX_TRANSFER_FAILURES);
            itemRetryAfter = gameTime + delay(itemFailures);
        }

        public void deferItemsUntil(long gameTime) {
            itemRetryAfter = Math.max(itemRetryAfter, gameTime);
        }

        public boolean isItemFilterRejected(ItemStack stack, long gameTime) {
            if (rejectedItems == null) return false;
            for (int i = 0; i < rejectedItems.length; i++) {
                if (gameTime < rejectedItemUntil[i] && !rejectedItems[i].isEmpty()
                        && StackData.sameItemAndComponents(rejectedItems[i], stack)) {
                    return true;
                }
            }
            return false;
        }

        public void recordItemFilterReject(ItemStack stack, long gameTime) {
            if (stack.isEmpty()) {
                return;
            }
            enableItemTargetCaching();
            ItemStack rejected = stack.copy();
            rejected.setCount(1);
            rejectedItems[rejectedItemCursor] = rejected;
            rejectedItemUntil[rejectedItemCursor] = gameTime + 20L;
            rejectedItemCursor = (rejectedItemCursor + 1) % rejectedItems.length;
        }

        public boolean isItemAcceptRejected(ItemStackKey key, long gameTime) {
            if (rejectedItemAccepts == null) return false;
            for (int i = 0; i < rejectedItemAccepts.length; i++) {
                if (gameTime < rejectedItemAcceptUntil[i] && key.equals(rejectedItemAccepts[i])) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasActiveItemAcceptRejects(long gameTime) {
            if (rejectedItemAcceptUntil == null) return false;
            for (long retryUntil : rejectedItemAcceptUntil) {
                if (gameTime < retryUntil) return true;
            }
            return false;
        }

        public int targetItemCursorLane(ItemStack stack, int totalSlots) {
            int configured = SkyLogisticsConfig.targetItemInsertionCursorCount();
            if (configured <= 0 || totalSlots <= 1) {
                clearTargetItemCursor();
                return -1;
            }
            int cursorCount = Math.min(configured, totalSlots);
            if (stack.isEmpty()) return -1;
            if (targetItemCursorOwners == null || targetItemCursorOwners.length != cursorCount
                    || targetItemCursorSlotCount != totalSlots) {
                targetItemCursorOwners = new Item[cursorCount];
                targetItemHotSlots = new int[cursorCount];
                targetItemScanCursors = new int[cursorCount];
                targetItemCursorModes = new byte[cursorCount];
                targetItemSequentialSuccesses = new int[cursorCount];
                for (int i = 0; i < cursorCount; i++) targetItemHotSlots[i] = -1;
                targetItemCursorSlotCount = totalSlots;
            }
            int lane = Math.floorMod(System.identityHashCode(stack.getItem()), cursorCount);
            if (targetItemCursorOwners[lane] != stack.getItem()) {
                targetItemCursorOwners[lane] = stack.getItem();
                targetItemHotSlots[lane] = -1;
                targetItemScanCursors[lane] = 0;
                targetItemCursorModes[lane] = TARGET_CURSOR_NEW;
                targetItemSequentialSuccesses[lane] = 0;
            }
            return lane;
        }

        public int targetItemHotSlot(int lane, int totalSlots) {
            if (!validTargetItemCursorLane(lane) || totalSlots <= 0) return -1;
            byte mode = targetItemCursorModes[lane];
            if (mode != TARGET_CURSOR_PROBATION && mode != TARGET_CURSOR_REUSE) return -1;
            int slot = targetItemHotSlots[lane];
            if (slot < 0 || slot >= totalSlots) {
                targetItemHotSlots[lane] = -1;
                targetItemCursorModes[lane] = TARGET_CURSOR_NEW;
                return -1;
            }
            return slot;
        }

        public int targetItemScanStart(int lane, int totalSlots) {
            if (!validTargetItemCursorLane(lane) || totalSlots <= 0) return 0;
            return Math.floorMod(targetItemScanCursors[lane], totalSlots);
        }

        public void recordTargetItemHotMiss(int lane, int slot, int totalSlots) {
            if (!validTargetItemCursorLane(lane) || totalSlots <= 0) return;
            targetItemCursorModes[lane] = TARGET_CURSOR_SEQUENTIAL;
            targetItemSequentialSuccesses[lane] = 0;
            targetItemScanCursors[lane] = Math.floorMod(slot + 1, totalSlots);
        }

        public void recordTargetItemSlotSuccess(int lane, int slot, boolean filled, boolean usedHot,
                int totalSlots) {
            if (!validTargetItemCursorLane(lane) || slot < 0 || totalSlots <= 0) return;
            targetItemHotSlots[lane] = slot;
            if (!filled || usedHot) {
                targetItemCursorModes[lane] = TARGET_CURSOR_REUSE;
                targetItemSequentialSuccesses[lane] = 0;
                targetItemScanCursors[lane] = slot;
                return;
            }
            targetItemScanCursors[lane] = Math.floorMod(slot + 1, totalSlots);
            if (targetItemCursorModes[lane] == TARGET_CURSOR_SEQUENTIAL) {
                int successes = targetItemSequentialSuccesses[lane] + 1;
                if (successes >= TARGET_CURSOR_REPROBE_SUCCESSES) {
                    targetItemCursorModes[lane] = TARGET_CURSOR_PROBATION;
                    targetItemSequentialSuccesses[lane] = 0;
                } else {
                    targetItemSequentialSuccesses[lane] = successes;
                }
            } else {
                targetItemCursorModes[lane] = TARGET_CURSOR_PROBATION;
                targetItemSequentialSuccesses[lane] = 0;
            }
        }

        public void recordTargetItemSlotMiss(int lane, int slot, int totalSlots) {
            if (!validTargetItemCursorLane(lane) || totalSlots <= 0) return;
            if (targetItemHotSlots[lane] == slot) recordTargetItemHotMiss(lane, slot, totalSlots);
            else targetItemScanCursors[lane] = Math.floorMod(slot + 1, totalSlots);
        }

        private boolean validTargetItemCursorLane(int lane) {
            return targetItemCursorOwners != null && lane >= 0 && lane < targetItemCursorOwners.length;
        }

        public void clearTargetItemCursor() {
            if (targetItemCursorOwners == null) return;
            targetItemCursorOwners = null;
            targetItemHotSlots = null;
            targetItemScanCursors = null;
            targetItemCursorModes = null;
            targetItemSequentialSuccesses = null;
            targetItemCursorSlotCount = -1;
        }

        public void recordItemAcceptReject(ItemStackKey key, long gameTime) {
            enableItemTargetCaching();
            int index = findRejectedItemAccept(key);
            if (index < 0) {
                index = rejectedItemAcceptCursor;
                rejectedItemAcceptCursor = (rejectedItemAcceptCursor + 1) % rejectedItemAccepts.length;
                rejectedItemAccepts[index] = key;
                rejectedItemAcceptFailures[index] = 0;
            }
            int failures = Math.min(rejectedItemAcceptFailures[index] + 1, MAX_TRANSFER_FAILURES);
            rejectedItemAcceptFailures[index] = failures;
            rejectedItemAcceptUntil[index] = gameTime + delay(failures);
        }

        public int itemTargetScanStart(ItemStackKey key, int targetCount) {
            if (itemTargetScanCursors == null) {
                itemTargetScanCursors = new BudgetedScanCursors<>(SkyLogisticsConfig.rejectedAcceptCacheSize());
            }
            return itemTargetScanCursors.start(key, targetCount);
        }

        public void resumeItemTargetScan(ItemStackKey key, int nextIndex, int targetCount) {
            if (itemTargetScanCursors == null) {
                itemTargetScanCursors = new BudgetedScanCursors<>(SkyLogisticsConfig.rejectedAcceptCacheSize());
            }
            itemTargetScanCursors.resumeAt(key, nextIndex, targetCount);
        }

        public void resetItemTargetScan(ItemStackKey key) {
            if (itemTargetScanCursors != null) itemTargetScanCursors.reset(key);
        }

        public int nextPreferredItemSlot(int slots, long gameTime, int firstTriedSlot, int secondTriedSlot) {
            if (preferredItemSlots == null) return -1;
            enableItemSourceCaching();
            for (int i = 0; i < preferredItemSlots.length; i++) {
                int index = Math.floorMod(preferredItemSlotCursor + i, preferredItemSlots.length);
                int slot = preferredItemSlots[index];
                if (slot < 0) {
                    continue;
                }
                if (slot >= slots) {
                    preferredItemSlots[index] = -1;
                    preferredItemSlotMisses[index] = 0;
                    continue;
                }
                if (preferredItemSlotTriedAt[index] == gameTime
                        || wasSlotTried(firstTriedSlot, secondTriedSlot, slot)
                        || !canTryItemSlot(slot, gameTime)) {
                    continue;
                }
                preferredItemSlotCursor = (index + 1) % preferredItemSlots.length;
                preferredItemSlotTriedAt[index] = gameTime;
                return slot;
            }
            return -1;
        }

        public boolean canTryItemSlot(int slot, long gameTime) {
            int preferredIndex = preferredItemSlots == null ? -1 : findPreferredItemSlot(slot);
            if (preferredIndex >= 0 && preferredItemSlotTriedAt[preferredIndex] == gameTime) return false;
            if (emptyItemSlots == null) return true;
            int index = findEmptyItemSlot(slot);
            return index < 0 || gameTime >= emptyItemSlotUntil[index];
        }

        public void recordItemSlotSuccess(int slot, int totalSlots, long gameTime) {
            enableItemSourceCaching();
            int preferredCount = preferredItemSlotCount();
            int preferredIndex = findPreferredItemSlot(slot);
            if (preferredIndex >= 0) {
                preferredItemSlotMisses[preferredIndex] = 0;
                preferredItemSlotTriedAt[preferredIndex] = gameTime;
            } else {
                int insertIndex = firstFreePreferredItemSlot();
                if (insertIndex < 0) {
                    insertIndex = preferredItemSlotWriteCursor;
                    preferredItemSlotWriteCursor = (preferredItemSlotWriteCursor + 1) % preferredItemSlots.length;
                }
                preferredItemSlots[insertIndex] = slot;
                preferredItemSlotMisses[insertIndex] = 0;
                preferredItemSlotTriedAt[insertIndex] = gameTime;
                if (preferredCount == 0 && totalSlots > 1) {
                    itemSlotDiscoveryRemaining = Math.max(itemSlotDiscoveryRemaining, totalSlots - 1);
                    itemSlotDiscoveryDeferrals = 0;
                }
            }
            clearEmptyItemSlot(slot);
        }

        public boolean isItemSlotDiscoveryActive() {
            return itemSlotDiscoveryRemaining > 0;
        }

        public boolean shouldTryItemSlotDiscoveryAfterPreferred() {
            if (itemSlotDiscoveryRemaining <= 0) {
                return false;
            }
            if (preferredItemSlotCount() <= 0) {
                return true;
            }
            itemSlotDiscoveryDeferrals++;
            if (itemSlotDiscoveryDeferrals >= ITEM_SLOT_DISCOVERY_PREFERRED_INTERVAL) {
                itemSlotDiscoveryDeferrals = 0;
                return true;
            }
            return false;
        }

        public void resetItemSlotDiscoveryDeferral() {
            itemSlotDiscoveryDeferrals = 0;
        }

        public void recordItemSlotDiscoveryCheck() {
            if (itemSlotDiscoveryRemaining > 0) {
                itemSlotDiscoveryRemaining--;
            }
        }

        public void clearItemSlotDiscovery() {
            itemSlotDiscoveryRemaining = 0;
            itemSlotDiscoveryDeferrals = 0;
        }

        public void recordItemSlotMiss(int slot, long gameTime) {
            enableItemSourceCaching();
            int preferredIndex = findPreferredItemSlot(slot);
            if (preferredIndex >= 0) {
                int misses = preferredItemSlotMisses[preferredIndex] + 1;
                if (misses >= PREFERRED_ITEM_SLOT_MISS_LIMIT) {
                    preferredItemSlots[preferredIndex] = -1;
                    preferredItemSlotMisses[preferredIndex] = 0;
                    recordEmptyItemSlot(slot, gameTime, gameTime + EMPTY_ITEM_SLOT_RETRY_TICKS);
                } else {
                    preferredItemSlotMisses[preferredIndex] = misses;
                }
                return;
            }
            recordEmptyItemSlot(slot, gameTime, gameTime + EMPTY_ITEM_SLOT_RETRY_TICKS);
        }

        public void recordItemSlotRejected(int slot, long gameTime) {
            enableItemSourceCaching();
            int preferredIndex = findPreferredItemSlot(slot);
            if (preferredIndex >= 0) {
                preferredItemSlots[preferredIndex] = -1;
                preferredItemSlotMisses[preferredIndex] = 0;
            }
            recordEmptyItemSlot(slot, gameTime, gameTime + EMPTY_ITEM_SLOT_RETRY_TICKS);
        }

        public void recordFluidSuccess() {
            node.recordRecentTransfer(direction);
            fluidFailures = 0;
            fluidRetryAfter = 0L;
            fluidSourceMisses = 0;
        }

        public void recordFluidCandidateFound() {
            fluidSourceMisses = 0;
        }

        public void recordFluidSourceMiss(int checkedTanks, int totalTanks, long gameTime) {
            if (totalTanks <= 0) {
                recordFluidFailure(gameTime);
                return;
            }
            fluidSourceMisses += Math.max(0, checkedTanks);
            if (fluidSourceMisses >= totalTanks) {
                fluidSourceMisses = 0;
                recordFluidFailure(gameTime);
            }
        }

        public void recordFluidFailure(long gameTime) {
            fluidFailures = Math.min(fluidFailures + 1, MAX_TRANSFER_FAILURES);
            fluidRetryAfter = gameTime + delay(fluidFailures);
        }

        public boolean isFluidAcceptRejected(FluidStackKey key, long gameTime) {
            if (rejectedFluidAccepts == null) return false;
            for (int i = 0; i < rejectedFluidAccepts.length; i++) {
                if (gameTime < rejectedFluidAcceptUntil[i] && key.equals(rejectedFluidAccepts[i])) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasActiveFluidAcceptRejects(long gameTime) {
            if (rejectedFluidAcceptUntil == null) return false;
            for (long retryUntil : rejectedFluidAcceptUntil) {
                if (gameTime < retryUntil) return true;
            }
            return false;
        }

        public void recordFluidAcceptReject(FluidStackKey key, long gameTime) {
            enableFluidTargetCaching();
            int index = findRejectedFluidAccept(key);
            if (index < 0) {
                index = rejectedFluidAcceptCursor;
                rejectedFluidAcceptCursor = (rejectedFluidAcceptCursor + 1) % rejectedFluidAccepts.length;
                rejectedFluidAccepts[index] = key;
                rejectedFluidAcceptFailures[index] = 0;
            }
            int failures = Math.min(rejectedFluidAcceptFailures[index] + 1, MAX_TRANSFER_FAILURES);
            rejectedFluidAcceptFailures[index] = failures;
            rejectedFluidAcceptUntil[index] = gameTime + delay(failures);
        }

        public int fluidTargetScanStart(FluidStackKey key, int targetCount) {
            if (fluidTargetScanCursors == null) {
                fluidTargetScanCursors = new BudgetedScanCursors<>(SkyLogisticsConfig.rejectedAcceptCacheSize());
            }
            return fluidTargetScanCursors.start(key, targetCount);
        }

        public void resumeFluidTargetScan(FluidStackKey key, int nextIndex, int targetCount) {
            if (fluidTargetScanCursors == null) {
                fluidTargetScanCursors = new BudgetedScanCursors<>(SkyLogisticsConfig.rejectedAcceptCacheSize());
            }
            fluidTargetScanCursors.resumeAt(key, nextIndex, targetCount);
        }

        public void resetFluidTargetScan(FluidStackKey key) {
            if (fluidTargetScanCursors != null) fluidTargetScanCursors.reset(key);
        }

        public int nextPreferredFluidTank(int tanks, long gameTime, int firstTriedTank, int secondTriedTank) {
            if (preferredFluidTanks == null) return -1;
            for (int i = 0; i < preferredFluidTanks.length; i++) {
                int index = Math.floorMod(preferredFluidTankCursor + i, preferredFluidTanks.length);
                int tank = preferredFluidTanks[index];
                if (tank < 0) {
                    continue;
                }
                if (tank >= tanks) {
                    preferredFluidTanks[index] = -1;
                    preferredFluidTankMisses[index] = 0;
                    continue;
                }
                if (wasSlotTried(firstTriedTank, secondTriedTank, tank) || !canTryFluidTank(tank, gameTime)) {
                    continue;
                }
                preferredFluidTankCursor = (index + 1) % preferredFluidTanks.length;
                return tank;
            }
            return -1;
        }

        public boolean canTryFluidTank(int tank, long gameTime) {
            if (emptyFluidTanks == null) return true;
            int index = findEmptyFluidTank(tank);
            return index < 0 || gameTime >= emptyFluidTankUntil[index];
        }

        public void recordFluidTankSuccess(int tank, int totalTanks) {
            enableFluidSourceCaching();
            int preferredCount = preferredFluidTankCount();
            int preferredIndex = findPreferredFluidTank(tank);
            if (preferredIndex >= 0) {
                preferredFluidTankMisses[preferredIndex] = 0;
            } else {
                int insertIndex = firstFreePreferredFluidTank();
                if (insertIndex < 0) {
                    insertIndex = preferredFluidTankWriteCursor;
                    preferredFluidTankWriteCursor = (preferredFluidTankWriteCursor + 1) % preferredFluidTanks.length;
                }
                preferredFluidTanks[insertIndex] = tank;
                preferredFluidTankMisses[insertIndex] = 0;
                if (preferredCount == 0 && totalTanks > 1) {
                    fluidTankDiscoveryRemaining = Math.max(fluidTankDiscoveryRemaining, totalTanks - 1);
                    fluidTankDiscoveryDeferrals = 0;
                }
            }
            clearEmptyFluidTank(tank);
        }

        public boolean isFluidTankDiscoveryActive() {
            return fluidTankDiscoveryRemaining > 0;
        }

        public boolean shouldTryFluidTankDiscoveryBeforePreferred() {
            if (fluidTankDiscoveryRemaining <= 0) return false;
            if (preferredFluidTankCount() <= 0) return true;
            fluidTankDiscoveryDeferrals++;
            if (fluidTankDiscoveryDeferrals >= FLUID_TANK_DISCOVERY_PREFERRED_INTERVAL) {
                fluidTankDiscoveryDeferrals = 0;
                return true;
            }
            return false;
        }

        public void recordFluidTankDiscoveryCheck() {
            if (fluidTankDiscoveryRemaining > 0) {
                fluidTankDiscoveryRemaining--;
            }
        }

        public void clearFluidTankDiscovery() {
            fluidTankDiscoveryRemaining = 0;
            fluidTankDiscoveryDeferrals = 0;
        }

        public void recordFluidTankMiss(int tank, long gameTime) {
            enableFluidSourceCaching();
            int preferredIndex = findPreferredFluidTank(tank);
            if (preferredIndex >= 0) {
                int misses = preferredFluidTankMisses[preferredIndex] + 1;
                if (misses >= PREFERRED_FLUID_TANK_MISS_LIMIT) {
                    preferredFluidTanks[preferredIndex] = -1;
                    preferredFluidTankMisses[preferredIndex] = 0;
                    recordEmptyFluidTank(tank, gameTime, gameTime + EMPTY_FLUID_TANK_RETRY_TICKS);
                } else {
                    preferredFluidTankMisses[preferredIndex] = misses;
                }
                return;
            }
            recordEmptyFluidTank(tank, gameTime, gameTime + EMPTY_FLUID_TANK_RETRY_TICKS);
        }

        public void recordFluidTankRejected(int tank, long gameTime) {
            enableFluidSourceCaching();
            int preferredIndex = findPreferredFluidTank(tank);
            if (preferredIndex >= 0) {
                preferredFluidTanks[preferredIndex] = -1;
                preferredFluidTankMisses[preferredIndex] = 0;
            }
            recordEmptyFluidTank(tank, gameTime, gameTime + EMPTY_FLUID_TANK_RETRY_TICKS);
        }

        public void recordChemicalSuccess() {
            node.recordRecentTransfer(direction);
            chemicalFailures = 0;
            chemicalRetryAfter = 0L;
            chemicalSourceMisses = 0;
        }

        public void recordChemicalCandidateFound() {
            chemicalSourceMisses = 0;
        }

        public void recordChemicalSourceMiss(int checkedTanks, int totalTanks, long gameTime) {
            if (totalTanks <= 0) {
                recordChemicalFailure(gameTime);
                return;
            }
            chemicalSourceMisses += Math.max(0, checkedTanks);
            if (chemicalSourceMisses >= totalTanks) {
                chemicalSourceMisses = 0;
                recordChemicalFailure(gameTime);
            }
        }

        public void recordChemicalFailure(long gameTime) {
            chemicalFailures = Math.min(chemicalFailures + 1, MAX_TRANSFER_FAILURES);
            chemicalRetryAfter = gameTime + delay(chemicalFailures);
        }

        public boolean isChemicalAcceptRejected(ChemicalStackView key, long gameTime) {
            if (rejectedChemicalAccepts == null) return false;
            for (int i = 0; i < rejectedChemicalAccepts.length; i++) {
                if (gameTime < rejectedChemicalAcceptUntil[i]
                        && rejectedChemicalAccepts[i] != null
                        && rejectedChemicalAccepts[i].isSameChemical(key)) {
                    return true;
                }
            }
            return false;
        }

        public void recordChemicalAcceptReject(ChemicalStackView key, long gameTime) {
            enableChemicalTargetCaching();
            int index = findRejectedChemicalAccept(key);
            if (index < 0) {
                index = rejectedChemicalAcceptCursor;
                rejectedChemicalAcceptCursor = (rejectedChemicalAcceptCursor + 1) % rejectedChemicalAccepts.length;
                rejectedChemicalAccepts[index] = key.copyWithAmount(1L);
                rejectedChemicalAcceptFailures[index] = 0;
            }
            int failures = Math.min(rejectedChemicalAcceptFailures[index] + 1, MAX_TRANSFER_FAILURES);
            rejectedChemicalAcceptFailures[index] = failures;
            rejectedChemicalAcceptUntil[index] = gameTime + delay(failures);
        }

        public int chemicalTargetScanStart(ChemicalStackView key, int targetCount) {
            if (chemicalTargetScanCursors == null) {
                chemicalTargetScanCursors = new BudgetedScanCursors<>(SkyLogisticsConfig.rejectedAcceptCacheSize());
            }
            return chemicalTargetScanCursors.start(key.chemicalKey(), targetCount);
        }

        public void resumeChemicalTargetScan(ChemicalStackView key, int nextIndex, int targetCount) {
            if (chemicalTargetScanCursors == null) {
                chemicalTargetScanCursors = new BudgetedScanCursors<>(SkyLogisticsConfig.rejectedAcceptCacheSize());
            }
            chemicalTargetScanCursors.resumeAt(key.chemicalKey(), nextIndex, targetCount);
        }

        public void resetChemicalTargetScan(ChemicalStackView key) {
            if (chemicalTargetScanCursors != null) chemicalTargetScanCursors.reset(key.chemicalKey());
        }

        public int resourceTargetScanStart(NetworkEndpointBlockEntity.TargetResource resource, int targetCount) {
            return targetCount <= 0 ? 0
                    : Math.floorMod(resourceTargetScanCursors[resource.ordinal()], targetCount);
        }

        public void resumeResourceTargetScan(NetworkEndpointBlockEntity.TargetResource resource, int nextIndex,
                int targetCount) {
            resourceTargetScanCursors[resource.ordinal()] =
                    targetCount <= 0 ? 0 : Math.floorMod(nextIndex, targetCount);
        }

        public void resetResourceTargetScan(NetworkEndpointBlockEntity.TargetResource resource) {
            resourceTargetScanCursors[resource.ordinal()] = 0;
        }

        public void clearTargetScans() {
            if (itemTargetScanCursors != null) itemTargetScanCursors.clear();
            if (fluidTargetScanCursors != null) fluidTargetScanCursors.clear();
            if (chemicalTargetScanCursors != null) chemicalTargetScanCursors.clear();
            for (int i = 0; i < resourceTargetScanCursors.length; i++) resourceTargetScanCursors[i] = 0;
        }

        public int nextPreferredChemicalTank(int tanks, long gameTime, int firstTriedTank, int secondTriedTank) {
            if (preferredChemicalTanks == null) return -1;
            for (int i = 0; i < preferredChemicalTanks.length; i++) {
                int index = Math.floorMod(preferredChemicalTankCursor + i, preferredChemicalTanks.length);
                int tank = preferredChemicalTanks[index];
                if (tank < 0) {
                    continue;
                }
                if (tank >= tanks) {
                    preferredChemicalTanks[index] = -1;
                    preferredChemicalTankMisses[index] = 0;
                    continue;
                }
                if (wasSlotTried(firstTriedTank, secondTriedTank, tank) || !canTryChemicalTank(tank, gameTime)) {
                    continue;
                }
                preferredChemicalTankCursor = (index + 1) % preferredChemicalTanks.length;
                return tank;
            }
            return -1;
        }

        public boolean canTryChemicalTank(int tank, long gameTime) {
            if (emptyChemicalTanks == null) return true;
            int index = findEmptyChemicalTank(tank);
            return index < 0 || gameTime >= emptyChemicalTankUntil[index];
        }

        public void recordChemicalTankSuccess(int tank, int totalTanks) {
            enableChemicalSourceCaching();
            int preferredCount = preferredChemicalTankCount();
            int preferredIndex = findPreferredChemicalTank(tank);
            if (preferredIndex >= 0) {
                preferredChemicalTankMisses[preferredIndex] = 0;
            } else {
                int insertIndex = firstFreePreferredChemicalTank();
                if (insertIndex < 0) {
                    insertIndex = preferredChemicalTankWriteCursor;
                    preferredChemicalTankWriteCursor = (preferredChemicalTankWriteCursor + 1) % preferredChemicalTanks.length;
                }
                preferredChemicalTanks[insertIndex] = tank;
                preferredChemicalTankMisses[insertIndex] = 0;
                if (preferredCount == 0 && totalTanks > 1) {
                    chemicalTankDiscoveryRemaining = Math.max(chemicalTankDiscoveryRemaining, totalTanks - 1);
                    chemicalTankDiscoveryDeferrals = 0;
                }
            }
            clearEmptyChemicalTank(tank);
        }

        public boolean isChemicalTankDiscoveryActive() {
            return chemicalTankDiscoveryRemaining > 0;
        }

        public boolean shouldTryChemicalTankDiscoveryBeforePreferred() {
            if (chemicalTankDiscoveryRemaining <= 0) return false;
            if (preferredChemicalTankCount() <= 0) return true;
            chemicalTankDiscoveryDeferrals++;
            if (chemicalTankDiscoveryDeferrals >= CHEMICAL_TANK_DISCOVERY_PREFERRED_INTERVAL) {
                chemicalTankDiscoveryDeferrals = 0;
                return true;
            }
            return false;
        }

        public void recordChemicalTankDiscoveryCheck() {
            if (chemicalTankDiscoveryRemaining > 0) {
                chemicalTankDiscoveryRemaining--;
            }
        }

        public void clearChemicalTankDiscovery() {
            chemicalTankDiscoveryRemaining = 0;
            chemicalTankDiscoveryDeferrals = 0;
        }

        public void recordChemicalTankMiss(int tank, long gameTime) {
            enableChemicalSourceCaching();
            int preferredIndex = findPreferredChemicalTank(tank);
            if (preferredIndex >= 0) {
                int misses = preferredChemicalTankMisses[preferredIndex] + 1;
                if (misses >= PREFERRED_CHEMICAL_TANK_MISS_LIMIT) {
                    preferredChemicalTanks[preferredIndex] = -1;
                    preferredChemicalTankMisses[preferredIndex] = 0;
                    recordEmptyChemicalTank(tank, gameTime, gameTime + EMPTY_CHEMICAL_TANK_RETRY_TICKS);
                } else {
                    preferredChemicalTankMisses[preferredIndex] = misses;
                }
                return;
            }
            recordEmptyChemicalTank(tank, gameTime, gameTime + EMPTY_CHEMICAL_TANK_RETRY_TICKS);
        }

        public void recordEnergySuccess() {
            node.recordRecentTransfer(direction);
            energyFailures = 0;
            energyRetryAfter = 0L;
        }

        public void recordEnergyFailure(long gameTime) {
            energyFailures = Math.min(energyFailures + 1, MAX_TRANSFER_FAILURES);
            energyRetryAfter = gameTime + delay(energyFailures);
        }

        public void recordManaSuccess() {
            node.recordRecentTransfer(direction);
            manaFailures = 0;
            manaRetryAfter = 0L;
        }

        public void recordManaFailure(long gameTime) {
            manaFailures = Math.min(manaFailures + 1, MAX_TRANSFER_FAILURES);
            manaRetryAfter = gameTime + delay(manaFailures);
        }

        public void recordSourceSuccess() {
            node.recordRecentTransfer(direction);
            sourceFailures = 0;
            sourceRetryAfter = 0L;
        }

        public void recordSourceFailure(long gameTime) {
            sourceFailures = Math.min(sourceFailures + 1, MAX_TRANSFER_FAILURES);
            sourceRetryAfter = gameTime + delay(sourceFailures);
        }

        private BlockCapabilityCache<IItemHandler, Direction> itemCapabilityCache(Level level) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return null;
            }
            if (itemCache == null || itemCache.level() != serverLevel) {
                itemCache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, serverLevel, targetPos,
                        accessSide, () -> node.getLevel() == serverLevel && !node.isRemoved(),
                        this::invalidateItemCache);
            }
            return itemCache;
        }

        private BlockCapabilityCache<IFluidHandler, Direction> fluidCapabilityCache(Level level) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return null;
            }
            if (fluidCache == null || fluidCache.level() != serverLevel) {
                fluidCache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, serverLevel, targetPos,
                        accessSide, () -> node.getLevel() == serverLevel && !node.isRemoved(),
                        this::invalidateFluidCache);
            }
            return fluidCache;
        }

        private BlockCapabilityCache<IEnergyStorage, Direction> energyCapabilityCache(Level level) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return null;
            }
            if (energyCache == null || energyCache.level() != serverLevel) {
                energyCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, serverLevel, targetPos,
                        accessSide, () -> node.getLevel() == serverLevel && !node.isRemoved(),
                        this::invalidateEnergyCache);
            }
            return energyCache;
        }

        private void invalidateItemCache() {
            clearItemCache();
            itemFailures = 0;
            itemRetryAfter = 0L;
            itemSourceMisses = 0;
            clearRejectedItems();
        }

        private void invalidateFluidCache() {
            clearFluidCache();
            fluidFailures = 0;
            fluidRetryAfter = 0L;
            fluidSourceMisses = 0;
        }

        private void invalidateEnergyCache() {
            clearEnergyCache();
            energyFailures = 0;
            energyRetryAfter = 0L;
        }

        private void clearItemCache() {
            recordCapabilityPresent(CAPABILITY_ITEMS);
            itemHandler = null;
            clearItemSlotCaches();
            clearRejectedItemAccepts();
            if (itemTargetScanCursors != null) itemTargetScanCursors.clear();
        }

        private void clearFluidCache() {
            recordCapabilityPresent(CAPABILITY_FLUIDS);
            fluidHandler = null;
            clearFluidTankCaches();
            clearRejectedFluidAccepts();
            if (fluidTargetScanCursors != null) fluidTargetScanCursors.clear();
        }

        private void clearChemicalCache() {
            recordCapabilityPresent(CAPABILITY_CHEMICALS);
            chemicalHandler = null;
            chemicalTarget = null;
            chemicalHandlerValidateAt = 0L;
            clearChemicalTankCaches();
            clearRejectedChemicalAccepts();
            if (chemicalTargetScanCursors != null) chemicalTargetScanCursors.clear();
        }

        private void clearEnergyCache() {
            recordCapabilityPresent(CAPABILITY_ENERGY);
            energyHandler = null;
            resetResourceTargetScan(NetworkEndpointBlockEntity.TargetResource.ENERGY);
        }

        private void clearManaCache() {
            recordCapabilityPresent(CAPABILITY_MANA);
            manaHandler = null;
            manaHandlerValidateAt = 0L;
            resetResourceTargetScan(NetworkEndpointBlockEntity.TargetResource.MANA);
        }

        private void clearSourceCache() {
            recordCapabilityPresent(CAPABILITY_SOURCE);
            sourceHandler = null;
            sourceHandlerValidateAt = 0L;
            resetResourceTargetScan(NetworkEndpointBlockEntity.TargetResource.SOURCE);
        }

        private void clearRejectedItems() {
            if (rejectedItems == null) return;
            for (int i = 0; i < rejectedItems.length; i++) {
                rejectedItems[i] = ItemStack.EMPTY;
                rejectedItemUntil[i] = 0L;
            }
            rejectedItemCursor = 0;
        }

        private void clearRejectedItemAccepts() {
            if (rejectedItemAccepts == null) return;
            for (int i = 0; i < rejectedItemAccepts.length; i++) {
                rejectedItemAccepts[i] = null;
                rejectedItemAcceptUntil[i] = 0L;
                rejectedItemAcceptFailures[i] = 0;
            }
            rejectedItemAcceptCursor = 0;
        }

        private void clearRejectedFluidAccepts() {
            if (rejectedFluidAccepts == null) return;
            for (int i = 0; i < rejectedFluidAccepts.length; i++) {
                rejectedFluidAccepts[i] = null;
                rejectedFluidAcceptUntil[i] = 0L;
                rejectedFluidAcceptFailures[i] = 0;
            }
            rejectedFluidAcceptCursor = 0;
        }

        private void clearRejectedChemicalAccepts() {
            if (rejectedChemicalAccepts == null) return;
            for (int i = 0; i < rejectedChemicalAccepts.length; i++) {
                rejectedChemicalAccepts[i] = null;
                rejectedChemicalAcceptUntil[i] = 0L;
                rejectedChemicalAcceptFailures[i] = 0;
            }
            rejectedChemicalAcceptCursor = 0;
        }

        private void clearItemSlotCaches() {
            if (preferredItemSlots != null) {
                for (int i = 0; i < preferredItemSlots.length; i++) {
                    preferredItemSlots[i] = -1;
                    preferredItemSlotMisses[i] = 0;
                    preferredItemSlotTriedAt[i] = Long.MIN_VALUE;
                }
                for (int i = 0; i < emptyItemSlots.length; i++) {
                    emptyItemSlots[i] = -1;
                    emptyItemSlotUntil[i] = 0L;
                }
            }
            preferredItemSlotCursor = 0;
            preferredItemSlotWriteCursor = 0;
            itemSlotDiscoveryRemaining = 0;
            itemSlotDiscoveryDeferrals = 0;
            emptyItemSlotCursor = 0;
            targetItemCursorOwners = null;
            targetItemHotSlots = null;
            targetItemScanCursors = null;
            targetItemCursorModes = null;
            targetItemSequentialSuccesses = null;
            targetItemCursorSlotCount = -1;
        }

        private void clearFluidTankCaches() {
            if (preferredFluidTanks == null) return;
            for (int i = 0; i < preferredFluidTanks.length; i++) {
                preferredFluidTanks[i] = -1;
                preferredFluidTankMisses[i] = 0;
            }
            for (int i = 0; i < emptyFluidTanks.length; i++) {
                emptyFluidTanks[i] = -1;
                emptyFluidTankUntil[i] = 0L;
            }
            preferredFluidTankCursor = 0;
            preferredFluidTankWriteCursor = 0;
            fluidTankDiscoveryRemaining = 0;
            fluidTankDiscoveryDeferrals = 0;
            emptyFluidTankCursor = 0;
        }

        private void clearChemicalTankCaches() {
            if (preferredChemicalTanks == null) return;
            for (int i = 0; i < preferredChemicalTanks.length; i++) {
                preferredChemicalTanks[i] = -1;
                preferredChemicalTankMisses[i] = 0;
            }
            for (int i = 0; i < emptyChemicalTanks.length; i++) {
                emptyChemicalTanks[i] = -1;
                emptyChemicalTankUntil[i] = 0L;
            }
            preferredChemicalTankCursor = 0;
            preferredChemicalTankWriteCursor = 0;
            chemicalTankDiscoveryRemaining = 0;
            chemicalTankDiscoveryDeferrals = 0;
            emptyChemicalTankCursor = 0;
        }

        private int preferredItemSlotCount() {
            int count = 0;
            for (int slot : preferredItemSlots) {
                if (slot >= 0) {
                    count++;
                }
            }
            return count;
        }

        private int findPreferredItemSlot(int slot) {
            for (int i = 0; i < preferredItemSlots.length; i++) {
                if (preferredItemSlots[i] == slot) {
                    return i;
                }
            }
            return -1;
        }

        private int firstFreePreferredItemSlot() {
            for (int i = 0; i < preferredItemSlots.length; i++) {
                if (preferredItemSlots[i] < 0) {
                    return i;
                }
            }
            return -1;
        }

        private int findEmptyItemSlot(int slot) {
            for (int i = 0; i < emptyItemSlots.length; i++) {
                if (emptyItemSlots[i] == slot) {
                    return i;
                }
            }
            return -1;
        }

        private void recordEmptyItemSlot(int slot, long gameTime, long until) {
            int index = findEmptyItemSlot(slot);
            if (index < 0) {
                index = firstFreeOrExpiredEmptyItemSlot(gameTime);
            }
            if (index < 0) {
                index = emptyItemSlotCursor;
                emptyItemSlotCursor = (emptyItemSlotCursor + 1) % emptyItemSlots.length;
            }
            emptyItemSlots[index] = slot;
            emptyItemSlotUntil[index] = until;
        }

        private int firstFreeOrExpiredEmptyItemSlot(long gameTime) {
            for (int i = 0; i < emptyItemSlots.length; i++) {
                if (emptyItemSlots[i] < 0 || gameTime >= emptyItemSlotUntil[i]) {
                    return i;
                }
            }
            return -1;
        }

        private void clearEmptyItemSlot(int slot) {
            int index = findEmptyItemSlot(slot);
            if (index >= 0) {
                emptyItemSlots[index] = -1;
                emptyItemSlotUntil[index] = 0L;
            }
        }

        private static boolean wasSlotTried(int firstTriedSlot, int secondTriedSlot, int slot) {
            return firstTriedSlot == slot || secondTriedSlot == slot;
        }

        private int preferredFluidTankCount() {
            int count = 0;
            for (int tank : preferredFluidTanks) {
                if (tank >= 0) {
                    count++;
                }
            }
            return count;
        }

        private int findPreferredFluidTank(int tank) {
            for (int i = 0; i < preferredFluidTanks.length; i++) {
                if (preferredFluidTanks[i] == tank) {
                    return i;
                }
            }
            return -1;
        }

        private int firstFreePreferredFluidTank() {
            for (int i = 0; i < preferredFluidTanks.length; i++) {
                if (preferredFluidTanks[i] < 0) {
                    return i;
                }
            }
            return -1;
        }

        private int findEmptyFluidTank(int tank) {
            for (int i = 0; i < emptyFluidTanks.length; i++) {
                if (emptyFluidTanks[i] == tank) {
                    return i;
                }
            }
            return -1;
        }

        private void recordEmptyFluidTank(int tank, long gameTime, long until) {
            int index = findEmptyFluidTank(tank);
            if (index < 0) {
                index = firstFreeOrExpiredEmptyFluidTank(gameTime);
            }
            if (index < 0) {
                index = emptyFluidTankCursor;
                emptyFluidTankCursor = (emptyFluidTankCursor + 1) % emptyFluidTanks.length;
            }
            emptyFluidTanks[index] = tank;
            emptyFluidTankUntil[index] = until;
        }

        private int firstFreeOrExpiredEmptyFluidTank(long gameTime) {
            for (int i = 0; i < emptyFluidTanks.length; i++) {
                if (emptyFluidTanks[i] < 0 || gameTime >= emptyFluidTankUntil[i]) {
                    return i;
                }
            }
            return -1;
        }

        private void clearEmptyFluidTank(int tank) {
            int index = findEmptyFluidTank(tank);
            if (index >= 0) {
                emptyFluidTanks[index] = -1;
                emptyFluidTankUntil[index] = 0L;
            }
        }

        private int preferredChemicalTankCount() {
            int count = 0;
            for (int tank : preferredChemicalTanks) {
                if (tank >= 0) {
                    count++;
                }
            }
            return count;
        }

        private int findPreferredChemicalTank(int tank) {
            for (int i = 0; i < preferredChemicalTanks.length; i++) {
                if (preferredChemicalTanks[i] == tank) {
                    return i;
                }
            }
            return -1;
        }

        private int firstFreePreferredChemicalTank() {
            for (int i = 0; i < preferredChemicalTanks.length; i++) {
                if (preferredChemicalTanks[i] < 0) {
                    return i;
                }
            }
            return -1;
        }

        private int findEmptyChemicalTank(int tank) {
            for (int i = 0; i < emptyChemicalTanks.length; i++) {
                if (emptyChemicalTanks[i] == tank) {
                    return i;
                }
            }
            return -1;
        }

        private void recordEmptyChemicalTank(int tank, long gameTime, long until) {
            int index = findEmptyChemicalTank(tank);
            if (index < 0) {
                index = firstFreeOrExpiredEmptyChemicalTank(gameTime);
            }
            if (index < 0) {
                index = emptyChemicalTankCursor;
                emptyChemicalTankCursor = (emptyChemicalTankCursor + 1) % emptyChemicalTanks.length;
            }
            emptyChemicalTanks[index] = tank;
            emptyChemicalTankUntil[index] = until;
        }

        private int firstFreeOrExpiredEmptyChemicalTank(long gameTime) {
            for (int i = 0; i < emptyChemicalTanks.length; i++) {
                if (emptyChemicalTanks[i] < 0 || gameTime >= emptyChemicalTankUntil[i]) {
                    return i;
                }
            }
            return -1;
        }

        private void clearEmptyChemicalTank(int tank) {
            int index = findEmptyChemicalTank(tank);
            if (index >= 0) {
                emptyChemicalTanks[index] = -1;
                emptyChemicalTankUntil[index] = 0L;
            }
        }

        private int findRejectedItemAccept(ItemStackKey key) {
            for (int i = 0; i < rejectedItemAccepts.length; i++) {
                if (key.equals(rejectedItemAccepts[i])) {
                    return i;
                }
            }
            return -1;
        }

        private int findRejectedFluidAccept(FluidStackKey key) {
            for (int i = 0; i < rejectedFluidAccepts.length; i++) {
                if (key.equals(rejectedFluidAccepts[i])) {
                    return i;
                }
            }
            return -1;
        }

        private int findRejectedChemicalAccept(ChemicalStackView key) {
            for (int i = 0; i < rejectedChemicalAccepts.length; i++) {
                if (rejectedChemicalAccepts[i] != null && rejectedChemicalAccepts[i].isSameChemical(key)) {
                    return i;
                }
            }
            return -1;
        }

        private static int delay(int failures) {
            return SkyLogisticsConfig.transferRetryDelayTicks(failures);
        }
    }
}
