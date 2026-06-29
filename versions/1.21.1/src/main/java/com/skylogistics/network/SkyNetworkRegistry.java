package com.skylogistics.network;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.compat.mekanism.ChemicalHandlerBridge;
import com.skylogistics.compat.mekanism.ChemicalStackView;
import com.skylogistics.compat.mekanism.MekanismCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.storage.FluidStackKey;
import com.skylogistics.storage.ItemStackKey;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    private static final int EMPTY_ITEM_SLOT_RETRY_TICKS = 20;
    private static final int PREFERRED_FLUID_TANK_CACHE_SIZE = 4;
    private static final int EMPTY_FLUID_TANK_CACHE_SIZE = 16;
    private static final int PREFERRED_FLUID_TANK_MISS_LIMIT = 3;
    private static final int EMPTY_FLUID_TANK_RETRY_TICKS = 20;
    private static final int PREFERRED_CHEMICAL_TANK_CACHE_SIZE = 4;
    private static final int EMPTY_CHEMICAL_TANK_CACHE_SIZE = 16;
    private static final int PREFERRED_CHEMICAL_TANK_MISS_LIMIT = 3;
    private static final int EMPTY_CHEMICAL_TANK_RETRY_TICKS = 20;
    private static final int REJECTED_ACCEPT_CACHE_SIZE = 8;
    private static final int REJECTED_ACCEPT_RETRY_TICKS = 20;
    private static final int FIRST_FAILURE_RETRY_TICKS = 5;
    private static final int NORMAL_FAILURE_RETRY_TICKS = 20;
    private static final int MAX_FAILURE_RETRY_TICKS = 40;

    private static final Map<ResourceKey<Level>, DimensionIndex> DIMENSIONS = new HashMap<>();
    private static final Set<LineIndex> ACTIVE_LINES = new LinkedHashSet<>();
    private static final List<LineIndex> ACTIVE_LINE_SNAPSHOT = new ArrayList<>();
    private static final TreeMap<Long, Set<LineIndex>> WAKE_BUCKETS = new TreeMap<>();
    private static final Map<LineIndex, Long> SCHEDULED_WAKE = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_ITEM_OUTPUTS = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_FLUID_OUTPUTS = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_CHEMICAL_OUTPUTS = new HashMap<>();
    private static final Map<UUID, List<CachedEndpoint>> GLOBAL_ENERGY_OUTPUTS = new HashMap<>();
    private static boolean runtimeCachesDirty = true;
    private static boolean globalOutputsDirty = true;
    private static boolean activeLineSnapshotDirty = true;
    private static int activeLineCursor;

    private SkyNetworkRegistry() {
    }

    public static synchronized void register(ServerLevel level, BlockPos pos) {
        DimensionIndex index = DIMENSIONS.computeIfAbsent(level.dimension(), ignored -> new DimensionIndex());
        index.nodes.add(pos.immutable());
        markTopologyDirty(index);
    }

    public static synchronized void unregister(ServerLevel level, BlockPos pos) {
        DimensionIndex index = DIMENSIONS.get(level.dimension());
        if (index != null) {
            index.nodes.remove(pos);
            markTopologyDirty(index);
            if (index.nodes.isEmpty()) {
                DIMENSIONS.remove(level.dimension());
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

    public static synchronized void markRuntimeDirty(ServerLevel level, BlockPos pos) {
        LineIndex line = findLine(level, pos);
        if (line != null) {
            wakeLine(line);
        }
    }

    public static synchronized void markPriorityDirty(ServerLevel level, BlockPos pos) {
        LineIndex line = findLine(level, pos);
        if (line != null) {
            line.rebuildPriorityOutputs();
            globalOutputsDirty = true;
            wakeLine(line);
        }
    }

    public static synchronized ReadyLines readyLines(MinecraftServer server, long gameTime) {
        boolean topologyChanged = rebuildDirty(server);
        if (topologyChanged || runtimeCachesDirty) {
            rebuildRuntimeCaches(server);
            runtimeCachesDirty = false;
            globalOutputsDirty = true;
        }
        if (topologyChanged || globalOutputsDirty) {
            rebuildGlobalOutputs(server);
            globalOutputsDirty = false;
        }
        promoteDueWakes(gameTime);
        return activeLinesView();
    }

    public static synchronized List<CachedEndpoint> globalItemOutputs(UUID lineId) {
        List<CachedEndpoint> outputs = GLOBAL_ITEM_OUTPUTS.get(lineId);
        return outputs == null ? List.of() : outputs;
    }

    public static synchronized List<CachedEndpoint> globalFluidOutputs(UUID lineId) {
        List<CachedEndpoint> outputs = GLOBAL_FLUID_OUTPUTS.get(lineId);
        return outputs == null ? List.of() : outputs;
    }

    public static synchronized List<CachedEndpoint> globalChemicalOutputs(UUID lineId) {
        List<CachedEndpoint> outputs = GLOBAL_CHEMICAL_OUTPUTS.get(lineId);
        return outputs == null ? List.of() : outputs;
    }

    public static synchronized List<CachedEndpoint> globalEnergyOutputs(UUID lineId) {
        List<CachedEndpoint> outputs = GLOBAL_ENERGY_OUTPUTS.get(lineId);
        return outputs == null ? List.of() : outputs;
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
        return line == null ? List.of() : List.copyOf(line.priorityItemOutputs());
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
        return line == null ? List.of() : List.copyOf(line.itemInputs);
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
            SkyNodeBlockEntity node = endpoint.node();
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
        runtimeCachesDirty = true;
        globalOutputsDirty = true;
        activeLineSnapshotDirty = true;
        activeLineCursor = 0;
    }

    private static void markTopologyDirty(DimensionIndex index) {
        index.dirty = true;
        runtimeCachesDirty = true;
        globalOutputsDirty = true;
    }

    private static void rebuild(ServerLevel level, DimensionIndex index) {
        Map<UUID, Long> retryAfterByLine = new HashMap<>();
        for (LineIndex line : index.lines.values()) {
            retryAfterByLine.put(line.lineId(), line.retryAfter);
        }
        index.lines.clear();
        index.lineByNode.clear();
        Iterator<BlockPos> iterator = index.nodes.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof SkyNodeBlockEntity node)) {
                iterator.remove();
                continue;
            }
            LineIndex line = index.lines.computeIfAbsent(node.getLineId(), lineId -> {
                LineIndex created = new LineIndex(lineId);
                created.retryAfter = retryAfterByLine.getOrDefault(lineId, 0L);
                return created;
            });
            line.nodeCount++;
            index.lineByNode.put(pos, line);
            for (Direction direction : Direction.values()) {
                NodeFaceMode faceMode = node.getFaceMode(direction);
                CachedEndpoint endpoint = new CachedEndpoint(node, direction);
                if (faceMode == NodeFaceMode.INPUT) {
                    line.addInput(endpoint);
                } else if (faceMode == NodeFaceMode.OUTPUT) {
                    line.addOutput(endpoint);
                }
            }
        }
        for (LineIndex line : index.lines.values()) {
            line.rebuildPriorityOutputs();
        }
        index.dirty = false;
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

    private static void rebuildGlobalOutputs(MinecraftServer server) {
        GLOBAL_ITEM_OUTPUTS.clear();
        GLOBAL_FLUID_OUTPUTS.clear();
        GLOBAL_CHEMICAL_OUTPUTS.clear();
        GLOBAL_ENERGY_OUTPUTS.clear();
        for (Map.Entry<ResourceKey<Level>, DimensionIndex> entry : DIMENSIONS.entrySet()) {
            if (server.getLevel(entry.getKey()) == null) {
                continue;
            }
            DimensionIndex index = entry.getValue();
            for (LineIndex line : index.lines.values()) {
                addGlobalOutputs(GLOBAL_ITEM_OUTPUTS, line.lineId(), line.priorityItemOutputs());
                addGlobalOutputs(GLOBAL_FLUID_OUTPUTS, line.lineId(), line.priorityFluidOutputs());
                addGlobalOutputs(GLOBAL_CHEMICAL_OUTPUTS, line.lineId(), line.priorityChemicalOutputs());
                addGlobalOutputs(GLOBAL_ENERGY_OUTPUTS, line.lineId(), line.priorityEnergyOutputs());
            }
        }
        sortGlobalOutputs(GLOBAL_ITEM_OUTPUTS);
        sortGlobalOutputs(GLOBAL_FLUID_OUTPUTS);
        sortGlobalOutputs(GLOBAL_CHEMICAL_OUTPUTS);
        sortGlobalOutputs(GLOBAL_ENERGY_OUTPUTS);
    }

    private static void addGlobalOutputs(Map<UUID, List<CachedEndpoint>> globalOutputs, UUID lineId,
            List<CachedEndpoint> outputs) {
        if (!outputs.isEmpty()) {
            globalOutputs.computeIfAbsent(lineId, ignored -> new ArrayList<>()).addAll(outputs);
        }
    }

    private static void sortGlobalOutputs(Map<UUID, List<CachedEndpoint>> globalOutputs) {
        for (List<CachedEndpoint> endpoints : globalOutputs.values()) {
            sortByPriority(endpoints);
        }
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
        private final Map<UUID, LineIndex> lines = new HashMap<>();
        private final Map<BlockPos, LineIndex> lineByNode = new HashMap<>();
        private boolean dirty = true;
    }

    public record LineStats(int nodes, int inputs, int outputs) {
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
        private final UUID lineId;
        private final List<CachedEndpoint> inputs = new ArrayList<>();
        private final List<CachedEndpoint> outputs = new ArrayList<>();
        private final List<CachedEndpoint> itemInputs = new ArrayList<>();
        private final List<CachedEndpoint> fluidInputs = new ArrayList<>();
        private final List<CachedEndpoint> chemicalInputs = new ArrayList<>();
        private final List<CachedEndpoint> energyInputs = new ArrayList<>();
        private final List<CachedEndpoint> itemOutputs = new ArrayList<>();
        private final List<CachedEndpoint> fluidOutputs = new ArrayList<>();
        private final List<CachedEndpoint> chemicalOutputs = new ArrayList<>();
        private final List<CachedEndpoint> energyOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityItemOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityFluidOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityChemicalOutputs = new ArrayList<>();
        private final List<CachedEndpoint> priorityEnergyOutputs = new ArrayList<>();
        private long retryAfter;
        private int nodeCount;
        private int inputCursor;

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
            if (!inputs.isEmpty()) {
                inputCursor = (inputCursor + 1) % inputs.size();
            }
        }

        public List<CachedEndpoint> outputs() {
            return outputs;
        }

        public int outputCount() {
            return outputs.size();
        }

        public List<CachedEndpoint> priorityOutputs() {
            return priorityOutputs;
        }

        public List<CachedEndpoint> priorityItemOutputs() {
            return priorityItemOutputs;
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

        public boolean hasProcessableInputs() {
            return !itemInputs.isEmpty() || !fluidInputs.isEmpty() || !chemicalInputs.isEmpty()
                    || !energyInputs.isEmpty();
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
            inputs.add(endpoint);
            addResourceEndpoint(endpoint, itemInputs, fluidInputs, chemicalInputs, energyInputs);
        }

        private void addOutput(CachedEndpoint endpoint) {
            outputs.add(endpoint);
            addResourceEndpoint(endpoint, itemOutputs, fluidOutputs, chemicalOutputs, energyOutputs);
        }

        private void rebuildPriorityOutputs() {
            priorityOutputs.clear();
            priorityOutputs.addAll(outputs);
            sortByPriority(priorityOutputs);

            priorityItemOutputs.clear();
            priorityItemOutputs.addAll(itemOutputs);
            sortByPriority(priorityItemOutputs);

            priorityFluidOutputs.clear();
            priorityFluidOutputs.addAll(fluidOutputs);
            sortByPriority(priorityFluidOutputs);

            priorityChemicalOutputs.clear();
            priorityChemicalOutputs.addAll(chemicalOutputs);
            sortByPriority(priorityChemicalOutputs);

            priorityEnergyOutputs.clear();
            priorityEnergyOutputs.addAll(energyOutputs);
            sortByPriority(priorityEnergyOutputs);
        }

        private static void addResourceEndpoint(CachedEndpoint endpoint, List<CachedEndpoint> itemEndpoints,
                List<CachedEndpoint> fluidEndpoints, List<CachedEndpoint> chemicalEndpoints,
                List<CachedEndpoint> energyEndpoints) {
            SkyNodeBlockEntity node = endpoint.node();
            Direction direction = endpoint.direction();
            if (node.isItemsEnabled(direction)) {
                itemEndpoints.add(endpoint);
            }
            if (node.isFluidsEnabled(direction)) {
                fluidEndpoints.add(endpoint);
                if (MekanismCompat.isLoaded()) {
                    chemicalEndpoints.add(endpoint);
                }
            }
            if (node.isEnergyEnabled(direction)) {
                energyEndpoints.add(endpoint);
            }
        }

    }

    public static final class CachedEndpoint {
        private final SkyNodeBlockEntity node;
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
        private long itemRetryAfter;
        private long fluidRetryAfter;
        private long chemicalRetryAfter;
        private long energyRetryAfter;
        private int itemFailures;
        private int fluidFailures;
        private int chemicalFailures;
        private int energyFailures;
        private int itemSourceMisses;
        private int fluidSourceMisses;
        private int chemicalSourceMisses;
        private final int[] preferredItemSlots = new int[SkyLogisticsConfig.preferredItemSlotCacheSize()];
        private final int[] preferredItemSlotMisses = new int[preferredItemSlots.length];
        private int preferredItemSlotCursor;
        private int preferredItemSlotWriteCursor;
        private int itemSlotDiscoveryRemaining;
        private final int[] emptyItemSlots = new int[EMPTY_ITEM_SLOT_CACHE_SIZE];
        private final long[] emptyItemSlotUntil = new long[EMPTY_ITEM_SLOT_CACHE_SIZE];
        private int emptyItemSlotCursor;
        private final int[] preferredFluidTanks = new int[PREFERRED_FLUID_TANK_CACHE_SIZE];
        private final int[] preferredFluidTankMisses = new int[PREFERRED_FLUID_TANK_CACHE_SIZE];
        private int preferredFluidTankCursor;
        private int preferredFluidTankWriteCursor;
        private int fluidTankDiscoveryRemaining;
        private final int[] emptyFluidTanks = new int[EMPTY_FLUID_TANK_CACHE_SIZE];
        private final long[] emptyFluidTankUntil = new long[EMPTY_FLUID_TANK_CACHE_SIZE];
        private int emptyFluidTankCursor;
        private final int[] preferredChemicalTanks = new int[PREFERRED_CHEMICAL_TANK_CACHE_SIZE];
        private final int[] preferredChemicalTankMisses = new int[PREFERRED_CHEMICAL_TANK_CACHE_SIZE];
        private int preferredChemicalTankCursor;
        private int preferredChemicalTankWriteCursor;
        private int chemicalTankDiscoveryRemaining;
        private final int[] emptyChemicalTanks = new int[EMPTY_CHEMICAL_TANK_CACHE_SIZE];
        private final long[] emptyChemicalTankUntil = new long[EMPTY_CHEMICAL_TANK_CACHE_SIZE];
        private int emptyChemicalTankCursor;
        private final ItemStack[] rejectedItems = new ItemStack[REJECTED_ITEM_CACHE_SIZE];
        private final long[] rejectedItemUntil = new long[REJECTED_ITEM_CACHE_SIZE];
        private int rejectedItemCursor;
        private final ItemStackKey[] rejectedItemAccepts = new ItemStackKey[REJECTED_ACCEPT_CACHE_SIZE];
        private final long[] rejectedItemAcceptUntil = new long[REJECTED_ACCEPT_CACHE_SIZE];
        private int rejectedItemAcceptCursor;
        private final FluidStackKey[] rejectedFluidAccepts = new FluidStackKey[REJECTED_ACCEPT_CACHE_SIZE];
        private final long[] rejectedFluidAcceptUntil = new long[REJECTED_ACCEPT_CACHE_SIZE];
        private int rejectedFluidAcceptCursor;
        private final ChemicalStackView[] rejectedChemicalAccepts = new ChemicalStackView[REJECTED_ACCEPT_CACHE_SIZE];
        private final long[] rejectedChemicalAcceptUntil = new long[REJECTED_ACCEPT_CACHE_SIZE];
        private int rejectedChemicalAcceptCursor;

        private CachedEndpoint(SkyNodeBlockEntity node, Direction direction) {
            this.node = node;
            this.direction = direction;
            this.targetPos = node.getTargetPos(direction);
            this.accessSide = node.getAccessSide(direction);
            clearItemSlotCaches();
            clearFluidTankCaches();
            clearChemicalTankCaches();
            for (int i = 0; i < rejectedItems.length; i++) {
                rejectedItems[i] = ItemStack.EMPTY;
            }
        }

        public SkyNodeBlockEntity node() {
            return node;
        }

        public Direction direction() {
            return direction;
        }

        public BlockEntity targetBlockEntity() {
            Level level = node.getLevel();
            if (level == null || !level.isLoaded(targetPos)) {
                return null;
            }
            return level.getBlockEntity(targetPos);
        }

        public boolean canTryItems(long gameTime) {
            return gameTime >= itemRetryAfter;
        }

        public boolean canTryFluids(long gameTime) {
            return gameTime >= fluidRetryAfter;
        }

        public boolean canTryChemicals(long gameTime) {
            return gameTime >= chemicalRetryAfter;
        }

        public boolean canTryEnergy(long gameTime) {
            return gameTime >= energyRetryAfter;
        }

        public long nextItemWake(long gameTime) {
            return itemRetryAfter > gameTime ? itemRetryAfter : gameTime;
        }

        public long nextFluidWake(long gameTime) {
            return fluidRetryAfter > gameTime ? fluidRetryAfter : gameTime;
        }

        public long nextChemicalWake(long gameTime) {
            return chemicalRetryAfter > gameTime ? chemicalRetryAfter : gameTime;
        }

        public long nextEnergyWake(long gameTime) {
            return energyRetryAfter > gameTime ? energyRetryAfter : gameTime;
        }

        public IItemHandler itemHandler(long gameTime) {
            if (!canTryItems(gameTime)) {
                return null;
            }
            IItemHandler direct = node.getEndpointItemHandler(direction, gameTime);
            if (direct != null) {
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
                recordItemFailure(gameTime);
            }
            return itemHandler;
        }

        public IFluidHandler fluidHandler(long gameTime) {
            if (!canTryFluids(gameTime)) {
                return null;
            }
            IFluidHandler direct = node.getEndpointFluidHandler(direction, gameTime);
            if (direct != null) {
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
                recordFluidFailure(gameTime);
            }
            return fluidHandler;
        }

        public ChemicalHandlerBridge chemicalHandler(long gameTime) {
            if (!canTryChemicals(gameTime)) {
                return null;
            }
            ChemicalHandlerBridge direct = node.getEndpointChemicalHandler(direction, gameTime);
            if (direct != null) {
                return direct;
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
            if (chemicalHandler != null && chemicalTarget == target) {
                return chemicalHandler;
            }
            clearChemicalCache();
            chemicalTarget = target;
            chemicalHandler = MekanismCompat.chemicalHandler(level, targetPos, accessSide);
            if (chemicalHandler == null) {
                recordChemicalFailure(gameTime);
            }
            return chemicalHandler;
        }

        public IEnergyStorage energyHandler(long gameTime) {
            if (!canTryEnergy(gameTime)) {
                return null;
            }
            IEnergyStorage direct = node.getEndpointEnergyHandler(direction, gameTime);
            if (direct != null) {
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
                recordEnergyFailure(gameTime);
            }
            return energyHandler;
        }

        public void recordItemSuccess() {
            itemFailures = 0;
            itemRetryAfter = 0L;
            itemSourceMisses = 0;
            clearRejectedItems();
            clearRejectedItemAccepts();
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
            itemFailures = Math.min(itemFailures + 1, 8);
            itemRetryAfter = gameTime + delay(itemFailures);
        }

        public void deferItemsUntil(long gameTime) {
            itemRetryAfter = Math.max(itemRetryAfter, gameTime);
        }

        public boolean isItemFilterRejected(ItemStack stack, long gameTime) {
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
            ItemStack rejected = stack.copy();
            rejected.setCount(1);
            rejectedItems[rejectedItemCursor] = rejected;
            rejectedItemUntil[rejectedItemCursor] = gameTime + 20L;
            rejectedItemCursor = (rejectedItemCursor + 1) % rejectedItems.length;
        }

        public boolean isItemAcceptRejected(ItemStackKey key, long gameTime) {
            for (int i = 0; i < rejectedItemAccepts.length; i++) {
                if (gameTime < rejectedItemAcceptUntil[i] && key.equals(rejectedItemAccepts[i])) {
                    return true;
                }
            }
            return false;
        }

        public void recordItemAcceptReject(ItemStackKey key, long gameTime) {
            rejectedItemAccepts[rejectedItemAcceptCursor] = key;
            rejectedItemAcceptUntil[rejectedItemAcceptCursor] = gameTime + REJECTED_ACCEPT_RETRY_TICKS;
            rejectedItemAcceptCursor = (rejectedItemAcceptCursor + 1) % rejectedItemAccepts.length;
        }

        public int nextPreferredItemSlot(int slots, long gameTime, int firstTriedSlot, int secondTriedSlot) {
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
                if (wasSlotTried(firstTriedSlot, secondTriedSlot, slot) || !canTryItemSlot(slot, gameTime)) {
                    continue;
                }
                preferredItemSlotCursor = (index + 1) % preferredItemSlots.length;
                return slot;
            }
            return -1;
        }

        public boolean canTryItemSlot(int slot, long gameTime) {
            int index = findEmptyItemSlot(slot);
            return index < 0 || gameTime >= emptyItemSlotUntil[index];
        }

        public void recordItemSlotSuccess(int slot, int totalSlots) {
            int preferredCount = preferredItemSlotCount();
            int preferredIndex = findPreferredItemSlot(slot);
            if (preferredIndex >= 0) {
                preferredItemSlotMisses[preferredIndex] = 0;
            } else {
                int insertIndex = firstFreePreferredItemSlot();
                if (insertIndex < 0) {
                    insertIndex = preferredItemSlotWriteCursor;
                    preferredItemSlotWriteCursor = (preferredItemSlotWriteCursor + 1) % preferredItemSlots.length;
                }
                preferredItemSlots[insertIndex] = slot;
                preferredItemSlotMisses[insertIndex] = 0;
                if (preferredCount == 0 && totalSlots > 1) {
                    itemSlotDiscoveryRemaining = Math.max(itemSlotDiscoveryRemaining, totalSlots - 1);
                }
            }
            clearEmptyItemSlot(slot);
        }

        public boolean isItemSlotDiscoveryActive() {
            return itemSlotDiscoveryRemaining > 0;
        }

        public void recordItemSlotDiscoveryCheck() {
            if (itemSlotDiscoveryRemaining > 0) {
                itemSlotDiscoveryRemaining--;
            }
        }

        public void clearItemSlotDiscovery() {
            itemSlotDiscoveryRemaining = 0;
        }

        public void recordItemSlotMiss(int slot, long gameTime) {
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
            int preferredIndex = findPreferredItemSlot(slot);
            if (preferredIndex >= 0) {
                preferredItemSlots[preferredIndex] = -1;
                preferredItemSlotMisses[preferredIndex] = 0;
            }
            recordEmptyItemSlot(slot, gameTime, gameTime + EMPTY_ITEM_SLOT_RETRY_TICKS);
        }

        public void recordFluidSuccess() {
            fluidFailures = 0;
            fluidRetryAfter = 0L;
            fluidSourceMisses = 0;
            clearRejectedFluidAccepts();
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
            fluidFailures = Math.min(fluidFailures + 1, 8);
            fluidRetryAfter = gameTime + delay(fluidFailures);
        }

        public boolean isFluidAcceptRejected(FluidStackKey key, long gameTime) {
            for (int i = 0; i < rejectedFluidAccepts.length; i++) {
                if (gameTime < rejectedFluidAcceptUntil[i] && key.equals(rejectedFluidAccepts[i])) {
                    return true;
                }
            }
            return false;
        }

        public void recordFluidAcceptReject(FluidStackKey key, long gameTime) {
            rejectedFluidAccepts[rejectedFluidAcceptCursor] = key;
            rejectedFluidAcceptUntil[rejectedFluidAcceptCursor] = gameTime + REJECTED_ACCEPT_RETRY_TICKS;
            rejectedFluidAcceptCursor = (rejectedFluidAcceptCursor + 1) % rejectedFluidAccepts.length;
        }

        public int nextPreferredFluidTank(int tanks, long gameTime, int firstTriedTank, int secondTriedTank) {
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
            int index = findEmptyFluidTank(tank);
            return index < 0 || gameTime >= emptyFluidTankUntil[index];
        }

        public void recordFluidTankSuccess(int tank, int totalTanks) {
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
                }
            }
            clearEmptyFluidTank(tank);
        }

        public boolean isFluidTankDiscoveryActive() {
            return fluidTankDiscoveryRemaining > 0;
        }

        public void recordFluidTankDiscoveryCheck() {
            if (fluidTankDiscoveryRemaining > 0) {
                fluidTankDiscoveryRemaining--;
            }
        }

        public void clearFluidTankDiscovery() {
            fluidTankDiscoveryRemaining = 0;
        }

        public void recordFluidTankMiss(int tank, long gameTime) {
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
            int preferredIndex = findPreferredFluidTank(tank);
            if (preferredIndex >= 0) {
                preferredFluidTanks[preferredIndex] = -1;
                preferredFluidTankMisses[preferredIndex] = 0;
            }
            recordEmptyFluidTank(tank, gameTime, gameTime + EMPTY_FLUID_TANK_RETRY_TICKS);
        }

        public void recordChemicalSuccess() {
            chemicalFailures = 0;
            chemicalRetryAfter = 0L;
            chemicalSourceMisses = 0;
            clearRejectedChemicalAccepts();
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
            chemicalFailures = Math.min(chemicalFailures + 1, 8);
            chemicalRetryAfter = gameTime + delay(chemicalFailures);
        }

        public boolean isChemicalAcceptRejected(ChemicalStackView key, long gameTime) {
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
            rejectedChemicalAccepts[rejectedChemicalAcceptCursor] = key.copyWithAmount(1L);
            rejectedChemicalAcceptUntil[rejectedChemicalAcceptCursor] = gameTime + REJECTED_ACCEPT_RETRY_TICKS;
            rejectedChemicalAcceptCursor = (rejectedChemicalAcceptCursor + 1) % rejectedChemicalAccepts.length;
        }

        public int nextPreferredChemicalTank(int tanks, long gameTime, int firstTriedTank, int secondTriedTank) {
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
            int index = findEmptyChemicalTank(tank);
            return index < 0 || gameTime >= emptyChemicalTankUntil[index];
        }

        public void recordChemicalTankSuccess(int tank, int totalTanks) {
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
                }
            }
            clearEmptyChemicalTank(tank);
        }

        public boolean isChemicalTankDiscoveryActive() {
            return chemicalTankDiscoveryRemaining > 0;
        }

        public void recordChemicalTankDiscoveryCheck() {
            if (chemicalTankDiscoveryRemaining > 0) {
                chemicalTankDiscoveryRemaining--;
            }
        }

        public void clearChemicalTankDiscovery() {
            chemicalTankDiscoveryRemaining = 0;
        }

        public void recordChemicalTankMiss(int tank, long gameTime) {
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
            energyFailures = 0;
            energyRetryAfter = 0L;
        }

        public void recordEnergyFailure(long gameTime) {
            energyFailures = Math.min(energyFailures + 1, 8);
            energyRetryAfter = gameTime + delay(energyFailures);
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
            itemHandler = null;
            clearItemSlotCaches();
            clearRejectedItemAccepts();
        }

        private void clearFluidCache() {
            fluidHandler = null;
            clearFluidTankCaches();
            clearRejectedFluidAccepts();
        }

        private void clearChemicalCache() {
            chemicalHandler = null;
            chemicalTarget = null;
            clearChemicalTankCaches();
            clearRejectedChemicalAccepts();
        }

        private void clearEnergyCache() {
            energyHandler = null;
        }

        private void clearRejectedItems() {
            for (int i = 0; i < rejectedItems.length; i++) {
                rejectedItems[i] = ItemStack.EMPTY;
                rejectedItemUntil[i] = 0L;
            }
            rejectedItemCursor = 0;
        }

        private void clearRejectedItemAccepts() {
            for (int i = 0; i < rejectedItemAccepts.length; i++) {
                rejectedItemAccepts[i] = null;
                rejectedItemAcceptUntil[i] = 0L;
            }
            rejectedItemAcceptCursor = 0;
        }

        private void clearRejectedFluidAccepts() {
            for (int i = 0; i < rejectedFluidAccepts.length; i++) {
                rejectedFluidAccepts[i] = null;
                rejectedFluidAcceptUntil[i] = 0L;
            }
            rejectedFluidAcceptCursor = 0;
        }

        private void clearRejectedChemicalAccepts() {
            for (int i = 0; i < rejectedChemicalAccepts.length; i++) {
                rejectedChemicalAccepts[i] = null;
                rejectedChemicalAcceptUntil[i] = 0L;
            }
            rejectedChemicalAcceptCursor = 0;
        }

        private void clearItemSlotCaches() {
            for (int i = 0; i < preferredItemSlots.length; i++) {
                preferredItemSlots[i] = -1;
                preferredItemSlotMisses[i] = 0;
            }
            for (int i = 0; i < emptyItemSlots.length; i++) {
                emptyItemSlots[i] = -1;
                emptyItemSlotUntil[i] = 0L;
            }
            preferredItemSlotCursor = 0;
            preferredItemSlotWriteCursor = 0;
            itemSlotDiscoveryRemaining = 0;
            emptyItemSlotCursor = 0;
        }

        private void clearFluidTankCaches() {
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
            emptyFluidTankCursor = 0;
        }

        private void clearChemicalTankCaches() {
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

        private static int delay(int failures) {
            if (failures <= 1) {
                return FIRST_FAILURE_RETRY_TICKS;
            }
            if (failures <= 4) {
                return NORMAL_FAILURE_RETRY_TICKS;
            }
            return MAX_FAILURE_RETRY_TICKS;
        }
    }
}
