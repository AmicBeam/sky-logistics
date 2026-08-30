package com.skylogistics.network;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import com.skylogistics.compat.curios.CuriosCompat;
import com.skylogistics.compat.sophisticated.SophisticatedBackpacksCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.SkyNecklaceItem;
import com.skylogistics.network.SkyNetworkRegistry.CachedEndpoint;
import com.skylogistics.util.MaintainedSlotPolicy;
import com.skylogistics.util.NodeFaceMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class SkyNecklaceTicker {
    private static final Map<UUID, Integer> ACTIVE_EXTRACTORS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_INSERTERS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_ITEM_INSERTERS = new HashMap<>();
    private static final Map<UUID, List<ActiveNecklaceDetail>> ACTIVE_DETAILS = new HashMap<>();
    private static final Map<UUID, ActiveNecklace> PLAYER_NECKLACES = new HashMap<>();
    private static final Map<UUID, PlayerDetailCache> PLAYER_DETAILS = new HashMap<>();
    private static final List<ActiveNecklace> ACTIVE_NECKLACES = new ArrayList<>();
    private static final Set<UUID> EQUIPPED_PLAYER_IDS = new HashSet<>();
    private static final Set<UUID> TRANSFER_PLAYER_IDS = new HashSet<>();
    private static final Map<UUID, Integer> EXTRACT_SLOT_CURSORS = new HashMap<>();
    private static final Map<UUID, Integer> EXTRACT_TARGET_CURSORS = new HashMap<>();
    private static final Map<UUID, Integer> INSERT_ENDPOINT_CURSORS = new HashMap<>();
    private static final Map<UUID, Integer> INSERT_DIMENSION_CANDIDATE_CURSORS = new HashMap<>();
    private static final Map<UUID, InventoryCountScan> INVENTORY_COUNT_SCANS = new HashMap<>();
    private static final Map<UUID, ExtractCountScan> EXTRACT_COUNT_SCANS = new HashMap<>();
    private static final Map<UUID, WhitelistCache> ITEM_WHITELISTS = new HashMap<>();

    private SkyNecklaceTicker() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld() == null
                || server.overworld().getGameTime() % SkyLogisticsConfig.skyNecklaceTickInterval() != 0L) {
            return;
        }
        process(server);
    }

    public static int activeExtractorCount(UUID lineId) {
        return ACTIVE_EXTRACTORS.getOrDefault(lineId, 0);
    }

    public static int activeInserterCount(UUID lineId) {
        return ACTIVE_INSERTERS.getOrDefault(lineId, 0);
    }

    public static int activeItemInserterCount(UUID lineId) {
        return ACTIVE_ITEM_INSERTERS.getOrDefault(lineId, 0);
    }

    public static List<ActiveNecklaceDetail> activeDetails(UUID lineId) {
        return ACTIVE_DETAILS.getOrDefault(lineId, List.of());
    }

    public static void clear() {
        ACTIVE_EXTRACTORS.clear();
        ACTIVE_INSERTERS.clear();
        ACTIVE_ITEM_INSERTERS.clear();
        ACTIVE_DETAILS.clear();
        PLAYER_NECKLACES.clear();
        PLAYER_DETAILS.clear();
        ACTIVE_NECKLACES.clear();
        EQUIPPED_PLAYER_IDS.clear();
        TRANSFER_PLAYER_IDS.clear();
        EXTRACT_SLOT_CURSORS.clear();
        EXTRACT_TARGET_CURSORS.clear();
        INSERT_ENDPOINT_CURSORS.clear();
        INSERT_DIMENSION_CANDIDATE_CURSORS.clear();
        INVENTORY_COUNT_SCANS.clear();
        EXTRACT_COUNT_SCANS.clear();
        ITEM_WHITELISTS.clear();
    }

    private static void process(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        ACTIVE_EXTRACTORS.clear();
        ACTIVE_INSERTERS.clear();
        ACTIVE_ITEM_INSERTERS.clear();
        for (List<ActiveNecklaceDetail> details : ACTIVE_DETAILS.values()) {
            details.clear();
        }
        ACTIVE_NECKLACES.clear();
        EQUIPPED_PLAYER_IDS.clear();
        TRANSFER_PLAYER_IDS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack necklace = activeNecklace(player);
            if (necklace.isEmpty()) {
                continue;
            }
            UUID lineId = SkyNecklaceItem.lineId(necklace);
            if (lineId == null) {
                continue;
            }
            SkyNecklaceItem.NecklaceMode mode = SkyNecklaceItem.mode(necklace);
            if (mode == SkyNecklaceItem.NecklaceMode.EXTRACT || mode == SkyNecklaceItem.NecklaceMode.MAINTAIN) {
                ACTIVE_EXTRACTORS.merge(lineId, 1, Integer::sum);
            }
            if (mode == SkyNecklaceItem.NecklaceMode.INSERT || mode == SkyNecklaceItem.NecklaceMode.MAINTAIN) {
                ACTIVE_INSERTERS.merge(lineId, 1, Integer::sum);
            }
            int priority = SkyNecklaceItem.priority(necklace);
            UUID playerId = player.getUUID();
            EQUIPPED_PLAYER_IDS.add(playerId);
            ACTIVE_DETAILS.computeIfAbsent(lineId, ignored -> new ArrayList<>())
                    .add(activeDetail(player, mode, priority));
            FilterListItem.CompiledFilter itemWhitelist = itemWhitelist(playerId, necklace, player.registryAccess());
            if (itemWhitelist != null) {
                ActiveNecklace active = PLAYER_NECKLACES.computeIfAbsent(playerId, ignored -> new ActiveNecklace());
                active.update(player, necklace, lineId, mode, priority, itemWhitelist);
                ACTIVE_NECKLACES.add(active);
                TRANSFER_PLAYER_IDS.add(playerId);
                if (mode != SkyNecklaceItem.NecklaceMode.EXTRACT) {
                    ACTIVE_ITEM_INSERTERS.merge(lineId, 1, Integer::sum);
                }
            }
        }
        ACTIVE_NECKLACES.sort(Comparator.comparingInt(ActiveNecklace::priority).reversed());
        ACTIVE_DETAILS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        for (List<ActiveNecklaceDetail> details : ACTIVE_DETAILS.values()) {
            details.sort(Comparator.comparingInt(ActiveNecklaceDetail::priority).reversed());
        }
        for (ActiveNecklace active : ACTIVE_NECKLACES) {
            switch (active.mode()) {
                case EXTRACT -> tryExtract(active.player(), active.necklace(), active.lineId(), active.itemWhitelist(),
                        gameTime, false, Integer.MAX_VALUE, SkyLogisticsConfig.skyNecklaceSlotScansPerTick(), false);
                case INSERT -> tryInsert(active.player(), active.necklace(), active.lineId(), active.itemWhitelist(),
                        gameTime, SkyNecklaceItem.maintainByItems(active.necklace())
                                ? SkyNecklaceItem.maintainAmount(active.necklace()) : 0,
                        SkyLogisticsConfig.skyNecklaceSlotScansPerTick());
                case MAINTAIN -> tryMaintain(active.player(), active.necklace(), active.lineId(),
                        active.itemWhitelist(), gameTime);
            }
        }
        EXTRACT_SLOT_CURSORS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        EXTRACT_TARGET_CURSORS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        INSERT_ENDPOINT_CURSORS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        INSERT_DIMENSION_CANDIDATE_CURSORS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        INVENTORY_COUNT_SCANS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        EXTRACT_COUNT_SCANS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        ITEM_WHITELISTS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        PLAYER_NECKLACES.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        PLAYER_DETAILS.keySet().removeIf(playerId -> !EQUIPPED_PLAYER_IDS.contains(playerId));
    }

    private static ItemStack activeNecklace(ServerPlayer player) {
        for (ItemStack stack : CuriosCompat.equippedSkyNecklaces(player)) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ActiveNecklaceDetail activeDetail(ServerPlayer player, SkyNecklaceItem.NecklaceMode mode,
            int priority) {
        GameProfile profile = player.getGameProfile();
        Property texture = firstTexture(profile);
        String textureValue = texture == null ? "" : texture.value();
        String textureSignature = texture != null && texture.hasSignature() ? texture.signature() : "";
        BlockPos pos = player.blockPosition().immutable();
        boolean maintainMode = mode == SkyNecklaceItem.NecklaceMode.MAINTAIN;
        NodeFaceMode faceMode = mode == SkyNecklaceItem.NecklaceMode.EXTRACT ? NodeFaceMode.INPUT
                : mode == SkyNecklaceItem.NecklaceMode.INSERT ? NodeFaceMode.OUTPUT : NodeFaceMode.NONE;
        Object dimensionKey = player.level().dimension();
        PlayerDetailCache cached = PLAYER_DETAILS.get(profile.getId());
        ActiveNecklaceDetail cachedDetail = cached == null ? null : cached.detail();
        if (cachedDetail != null && cached.dimensionKey().equals(dimensionKey)
                && cachedDetail.playerName().equals(profile.getName())
                && cachedDetail.profileTexture().equals(textureValue)
                && cachedDetail.profileTextureSignature().equals(textureSignature)
                && cachedDetail.pos().equals(pos) && cachedDetail.mode() == faceMode
                && cachedDetail.maintainMode() == maintainMode
                && cachedDetail.priority() == priority) {
            return cachedDetail;
        }
        String dimension = player.level().dimension().location().toString();
        ActiveNecklaceDetail detail = new ActiveNecklaceDetail(profile.getId(), profile.getName(), textureValue,
                textureSignature, dimension, pos, faceMode, maintainMode, priority);
        PLAYER_DETAILS.put(profile.getId(), new PlayerDetailCache(dimensionKey, detail));
        return detail;
    }

    private static Property firstTexture(GameProfile profile) {
        for (Property property : profile.getProperties().get("textures")) {
            return property;
        }
        return null;
    }

    private static FilterListItem.CompiledFilter itemWhitelist(UUID playerId, ItemStack necklace,
            net.minecraft.core.HolderLookup.Provider registries) {
        ItemStack filter = SkyNecklaceItem.filterList(necklace);
        if (filter.isEmpty() || !FilterListItem.isWhitelist(filter)) {
            ITEM_WHITELISTS.remove(playerId);
            return null;
        }
        WhitelistCache cached = ITEM_WHITELISTS.get(playerId);
        if (cached != null && ItemStack.isSameItemSameComponents(cached.filter(), filter)) {
            return cached.compiled();
        }
        FilterListItem.CompiledFilter compiled = FilterListItem.compile(filter, registries);
        if (!compiled.hasItemRules()) {
            ITEM_WHITELISTS.remove(playerId);
            return null;
        }
        ITEM_WHITELISTS.put(playerId, new WhitelistCache(filter.copy(), compiled));
        return compiled;
    }

    private static void tryExtract(ServerPlayer player, ItemStack necklace, UUID lineId,
            FilterListItem.CompiledFilter itemWhitelist, long gameTime, boolean mainOnly, int maxExtract,
            int requestedScanBudget, boolean quantityAlreadyChecked) {
        List<CachedEndpoint> targets = SkyNecklaceItem.hasDimensionUpgrade(necklace)
                ? SkyNetworkRegistry.globalItemOutputs(lineId)
                : SkyNetworkRegistry.lineItemOutputs(player.server, player.level().dimension(), lineId);
        if (targets.isEmpty()) {
            return;
        }
        List<IItemHandler> sources = mainOnly ? List.of(new PlayerMainInventoryHandler(player.getInventory(), null,
                PlayerMainInventoryHandler.MAIN_SLOTS, 0)) : sources(player);
        int totalSlots = totalSlots(sources);
        if (totalSlots <= 0) {
            return;
        }
        UUID playerId = player.getUUID();
        int scanBudget = Math.min(totalSlots, Math.max(0, requestedScanBudget));
        if (scanBudget <= 0) return;
        int slotLimit = SkyNecklaceItem.insertSlots(necklace);
        boolean exact = SkyNecklaceItem.maintainByItems(necklace);
        if (!quantityAlreadyChecked && (exact || slotLimit > SkyNecklaceItem.MIN_INSERT_SLOTS)) {
            ExtractCountResult count = scanExtractSources(playerId, sources, itemWhitelist, exact, scanBudget);
            if (!count.complete()) return;
            scanBudget -= count.checks();
            if (scanBudget <= 0) return;
            if (exact) {
                int exactLimit = SkyNecklaceItem.maintainAmount(necklace);
                if (count.items() <= exactLimit) {
                    EXTRACT_COUNT_SCANS.remove(playerId);
                    return;
                }
                maxExtract = Math.min(maxExtract, count.items() - exactLimit);
            } else if (count.slots() <= slotLimit) {
                EXTRACT_COUNT_SCANS.remove(playerId);
                return;
            }
            EXTRACT_COUNT_SCANS.remove(playerId);
        }
        int cursor = Math.floorMod(EXTRACT_SLOT_CURSORS.getOrDefault(playerId, 0), totalSlots);
        int transferLimit = Math.min(SkyLogisticsConfig.nodeItemTransferLimit(), Math.max(1, maxExtract));
        int scanned = 0;
        while (scanned < scanBudget) {
            int flatIndex = (cursor + scanned) % totalSlots;
            long handlerSlot = handlerSlotAt(sources, flatIndex);
            scanned++;
            if (handlerSlot < 0L) {
                continue;
            }
            IItemHandler handler = sources.get(unpackHigh(handlerSlot));
            int slot = unpackLow(handlerSlot);
            ItemStack simulated = handler.extractItem(slot, transferLimit, true);
            if (simulated.isEmpty() || shouldSkipSourceStack(simulated) || !itemWhitelist.matches(simulated)) {
                continue;
            }
            int targetCursor = Math.floorMod(EXTRACT_TARGET_CURSORS.getOrDefault(playerId, 0), targets.size());
            int nextTargetCursor = tryMove(handler, slot, simulated, targets,
                    targetCursor, SkyLogisticsConfig.skyNecklaceTargetAttemptsPerWork(), gameTime);
            EXTRACT_TARGET_CURSORS.put(playerId, nextTargetCursor < 0 ? 0 : nextTargetCursor);
            if (nextTargetCursor < 0) {
                EXTRACT_SLOT_CURSORS.put(playerId, (flatIndex + 1) % totalSlots);
                return;
            }
        }
        EXTRACT_SLOT_CURSORS.put(playerId, (cursor + scanned) % totalSlots);
    }

    private static ExtractCountResult scanExtractSources(UUID playerId, List<IItemHandler> sources,
            FilterListItem.CompiledFilter filter, boolean exact, int budget) {
        int totalSlots = totalSlots(sources);
        ExtractCountScan scan = EXTRACT_COUNT_SCANS.get(playerId);
        if (scan == null || scan.filter != filter || scan.exact != exact || scan.totalSlots != totalSlots) {
            scan = new ExtractCountScan(filter, exact, totalSlots);
            EXTRACT_COUNT_SCANS.put(playerId, scan);
        }
        int checks = 0;
        while (scan.nextFlatSlot < totalSlots && checks < budget) {
            long handlerSlot = handlerSlotAt(sources, scan.nextFlatSlot++);
            checks++;
            if (handlerSlot < 0L) continue;
            ItemStack stack = sources.get(unpackHigh(handlerSlot)).getStackInSlot(unpackLow(handlerSlot));
            if (stack.isEmpty() || shouldSkipSourceStack(stack) || !filter.matches(stack)) continue;
            scan.slots++;
            scan.items = Math.min(Integer.MAX_VALUE, scan.items + (long)stack.getCount());
        }
        return new ExtractCountResult(scan.nextFlatSlot >= totalSlots, scan.slots, (int)scan.items, checks);
    }

    private static List<IItemHandler> sources(ServerPlayer player) {
        List<IItemHandler> sources = new ArrayList<>();
        sources.add(new PlayerMainInventoryHandler(player.getInventory(), null, PlayerMainInventoryHandler.MAIN_SLOTS, 0));
        sources.addAll(SophisticatedBackpacksCompat.carriedBackpackHandlers(player));
        return sources;
    }

    private static int totalSlots(List<IItemHandler> sources) {
        int totalSlots = 0;
        for (IItemHandler source : sources) {
            totalSlots += source.getSlots();
        }
        return totalSlots;
    }

    private static void tryMaintain(ServerPlayer player, ItemStack necklace, UUID lineId,
            FilterListItem.CompiledFilter itemWhitelist, long gameTime) {
        int configured = SkyNecklaceItem.maintainAmount(necklace);
        if (configured <= 0) return;
        boolean exact = SkyNecklaceItem.maintainByItems(necklace);
        int budget = SkyLogisticsConfig.skyNecklaceSlotScansPerTick();
        InventoryCountResult count = scanMainInventory(player.getUUID(), player.getInventory(), itemWhitelist, budget);
        if (!count.complete()) return;
        int remainingBudget = budget - count.checks();
        if (remainingBudget <= 0) return;
        int current = exact ? count.items() : count.slots();
        if (MaintainedSlotPolicy.shouldInsert(exact, current, configured,
                SkyLogisticsConfig.fillMaintainedItemSlots())) {
            tryInsert(player, necklace, lineId, itemWhitelist, gameTime, exact ? configured : 0, remainingBudget);
        } else if (current > configured) {
            int excess = exact ? current - configured : Integer.MAX_VALUE;
            tryExtract(player, necklace, lineId, itemWhitelist, gameTime, true, excess, remainingBudget, true);
            INVENTORY_COUNT_SCANS.remove(player.getUUID());
        } else {
            INVENTORY_COUNT_SCANS.remove(player.getUUID());
        }
    }

    private static long handlerSlotAt(List<IItemHandler> sources, int flatIndex) {
        int offset = flatIndex;
        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            IItemHandler source = sources.get(sourceIndex);
            int slots = source.getSlots();
            if (offset < slots) {
                return packInts(sourceIndex, offset);
            }
            offset -= slots;
        }
        return -1L;
    }

    private static void tryInsert(ServerPlayer player, ItemStack necklace, UUID lineId,
            FilterListItem.CompiledFilter itemWhitelist, long gameTime, int exactLimit, int requestedScanBudget) {
        List<CachedEndpoint> sources = SkyNecklaceItem.hasDimensionUpgrade(necklace)
                ? SkyNetworkRegistry.globalItemInputs(player.server, lineId)
                : SkyNetworkRegistry.lineItemInputs(player.server, player.level().dimension(), lineId);
        if (sources.isEmpty()) {
            return;
        }
        UUID playerId = player.getUUID();
        int slotScanBudget = Math.max(0, requestedScanBudget);
        InventoryCountResult count = scanMainInventory(playerId, player.getInventory(), itemWhitelist, slotScanBudget);
        if (!count.complete() || count.checks() >= slotScanBudget) return;
        IItemHandler target = new PlayerMainInventoryHandler(player.getInventory(), itemWhitelist,
                SkyNecklaceItem.insertSlots(necklace), exactLimit, count.slots(), count.items());
        int sourceCursor = Math.floorMod(INSERT_ENDPOINT_CURSORS.getOrDefault(playerId, 0), sources.size());
        int scannedSlots = count.checks();
        int visitedEndpoints = 0;
        int endpointBudget = SkyLogisticsConfig.skyNecklaceTargetAttemptsPerWork();
        int transferLimit = SkyLogisticsConfig.nodeItemTransferLimit();
        for (int sourceOffset = 0; sourceOffset < sources.size() && scannedSlots < slotScanBudget
                && visitedEndpoints < endpointBudget; sourceOffset++) {
            int sourceIndex = (sourceCursor + sourceOffset) % sources.size();
            visitedEndpoints++;
            CachedEndpoint sourceEndpoint = sources.get(sourceIndex);
            if (!sourceEndpoint.canTryItems(gameTime)
                    || !sourceEndpoint.node().isFaceRedstoneAllowed(sourceEndpoint.direction())
                    || !sourceEndpoint.node().isItemsEnabled(sourceEndpoint.direction())) {
                continue;
            }
            BlockEntity sourceBlockEntity = sourceEndpoint.targetBlockEntity();
            if (sourceBlockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
                DimensionInsertResult result = tryInsertFromDimensionSource(playerId, sourceEndpoint,
                        sourceBlockEntity, itemWhitelist, target, transferLimit, gameTime,
                        slotScanBudget - scannedSlots);
                scannedSlots += result.checks();
                if (result.moved()) {
                    INVENTORY_COUNT_SCANS.remove(playerId);
                    INSERT_ENDPOINT_CURSORS.put(playerId, (sourceIndex + 1) % sources.size());
                    return;
                }
                if (!result.complete()) {
                    INSERT_ENDPOINT_CURSORS.put(playerId, sourceIndex);
                    return;
                }
                continue;
            }
            IItemHandler source = sourceEndpoint.itemHandler(gameTime);
            if (source == null) {
                continue;
            }
            int slots = source.getSlots();
            if (slots <= 0) {
                sourceEndpoint.recordItemFailure(gameTime);
                continue;
            }
            int slotAttempts = Math.min(slots, slotScanBudget - scannedSlots);
            for (int attempts = 0; attempts < slotAttempts; attempts++) {
                scannedSlots++;
                int slot = sourceEndpoint.node().nextItemStart(slots);
                ItemStack simulated = source.extractItem(slot, transferLimit, true);
                if (simulated.isEmpty()
                        || !sourceEndpoint.node().allowsItem(sourceEndpoint.direction(), simulated)
                        || !itemWhitelist.matches(simulated)) {
                    continue;
                }
                if (tryMoveToPlayer(sourceEndpoint, source, slot, simulated, target, gameTime)) {
                    INVENTORY_COUNT_SCANS.remove(playerId);
                    INSERT_ENDPOINT_CURSORS.put(playerId, (sourceIndex + 1) % sources.size());
                    return;
                }
            }
        }
        INSERT_ENDPOINT_CURSORS.put(playerId, (sourceCursor + Math.max(visitedEndpoints, 1)) % sources.size());
        INVENTORY_COUNT_SCANS.remove(playerId);
    }

    private static InventoryCountResult scanMainInventory(UUID playerId, Inventory inventory,
            FilterListItem.CompiledFilter filter, int budget) {
        InventoryCountScan scan = INVENTORY_COUNT_SCANS.get(playerId);
        if (scan == null || scan.filter != filter) {
            scan = new InventoryCountScan(filter);
            INVENTORY_COUNT_SCANS.put(playerId, scan);
        }
        int checks = 0;
        while (scan.nextSlot < PlayerMainInventoryHandler.MAIN_SLOTS && checks < budget) {
            ItemStack stack = inventory.getItem(scan.nextSlot++);
            checks++;
            if (stack.isEmpty() || shouldSkipSourceStack(stack) || !filter.matches(stack)) continue;
            scan.slots++;
            scan.items = Math.min(Integer.MAX_VALUE, scan.items + (long)stack.getCount());
        }
        return new InventoryCountResult(scan.nextSlot >= PlayerMainInventoryHandler.MAIN_SLOTS,
                scan.slots, (int)scan.items, checks);
    }

    private static boolean shouldSkipSourceStack(ItemStack stack) {
        return stack.is(com.skylogistics.registry.ModItems.SKY_NECKLACE.get())
                || SophisticatedBackpacksCompat.isBackpackItem(stack);
    }

    private static DimensionInsertResult tryInsertFromDimensionSource(UUID playerId, CachedEndpoint sourceEndpoint,
            BlockEntity sourceBlockEntity,
            FilterListItem.CompiledFilter itemWhitelist, IItemHandler target, int transferLimit, long gameTime,
            int budget) {
        int sampleCount = itemWhitelist.itemSamples().size();
        int candidateCount = sampleCount + itemWhitelist.itemTags().size();
        if (candidateCount <= 0) return new DimensionInsertResult(false, 0, true);
        int cursor = Math.floorMod(INSERT_DIMENSION_CANDIDATE_CURSORS.getOrDefault(playerId, 0), candidateCount);
        int checks = 0;
        while (checks < candidateCount && checks < budget) {
            int candidate = (cursor + checks) % candidateCount;
            BeyondDimensionsCompat.ItemResource resource = candidate < sampleCount
                    ? BeyondDimensionsCompat.itemResourceForStack(sourceBlockEntity,
                            itemWhitelist.itemSamples().get(candidate))
                    : BeyondDimensionsCompat.itemResourceForTag(sourceBlockEntity,
                            itemWhitelist.itemTags().get(candidate - sampleCount));
            checks++;
            INSERT_DIMENSION_CANDIDATE_CURSORS.put(playerId, (candidate + 1) % candidateCount);
            if (tryMoveDimensionToPlayer(sourceEndpoint, sourceBlockEntity, resource, itemWhitelist, target,
                    transferLimit, gameTime)) {
                INSERT_DIMENSION_CANDIDATE_CURSORS.remove(playerId);
                return new DimensionInsertResult(true, checks, true);
            }
        }
        boolean complete = checks >= candidateCount;
        if (complete) INSERT_DIMENSION_CANDIDATE_CURSORS.remove(playerId);
        return new DimensionInsertResult(false, checks, complete);
    }

    private static boolean tryMoveDimensionToPlayer(CachedEndpoint sourceEndpoint, BlockEntity sourceBlockEntity,
            BeyondDimensionsCompat.ItemResource resource, FilterListItem.CompiledFilter itemWhitelist,
            IItemHandler target, int transferLimit, long gameTime) {
        if (resource.isEmpty()) {
            return false;
        }
        ItemStack simulated = resource.stack().copyWithCount((int) Math.min(Math.min(resource.amount(), transferLimit),
                Integer.MAX_VALUE));
        if (simulated.isEmpty()
                || !sourceEndpoint.node().allowsItem(sourceEndpoint.direction(), simulated)
                || !itemWhitelist.matches(simulated)) {
            return false;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, simulated.copy(), true);
        int movable = simulated.getCount() - remainder.getCount();
        if (movable <= 0) {
            return false;
        }
        long extractedAmount = BeyondDimensionsCompat.extractItem(sourceBlockEntity, simulated, movable, false);
        if (extractedAmount <= 0L) {
            sourceEndpoint.recordItemFailure(gameTime);
            return false;
        }
        ItemStack extracted = simulated.copyWithCount((int) extractedAmount);
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, extracted, false);
        if (!leftover.isEmpty()) {
            long rolledBack = BeyondDimensionsCompat.insertItem(sourceBlockEntity, leftover, leftover.getCount(),
                    false);
            if (rolledBack < leftover.getCount()) {
                SkyLogistics.LOGGER.warn(
                        "Sky necklace dimension insert rollback failed: extracted {}, leftover {}, rollback remainder {}",
                        extracted, leftover, leftover.getCount() - rolledBack);
            }
        }
        sourceEndpoint.recordItemSuccess();
        return true;
    }

    private static int tryMove(IItemHandler source, int slot, ItemStack simulated,
            List<CachedEndpoint> targets, int startCursor, int attemptLimit, long gameTime) {
        int visited = 0;
        int limit = Math.min(targets.size(), Math.max(1, attemptLimit));
        while (visited < limit) {
            int targetIndex = Math.floorMod(startCursor + visited, targets.size());
            CachedEndpoint targetEndpoint = targets.get(targetIndex);
            visited++;
            if (!targetEndpoint.canTryItems(gameTime)
                    || !targetEndpoint.node().isFaceRedstoneAllowed(targetEndpoint.direction())
                    || !targetEndpoint.node().isItemsEnabled(targetEndpoint.direction())
                    || targetEndpoint.isItemFilterRejected(simulated, gameTime)
                    || !targetEndpoint.node().allowsItem(targetEndpoint.direction(), simulated)) {
                continue;
            }
            BlockEntity targetBlockEntity = targetEndpoint.targetBlockEntity();
            if (targetBlockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
                if (tryMoveToDimensionTarget(source, slot, simulated, targetEndpoint, targetBlockEntity, gameTime)) {
                    return -1;
                }
                continue;
            }
            IItemHandler target = targetEndpoint.itemHandler(gameTime);
            if (target == null) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, simulated.copy(), true);
            int movable = simulated.getCount() - remainder.getCount();
            if (movable <= 0) {
                targetEndpoint.recordItemFailure(gameTime);
                continue;
            }
            ItemStack extracted = source.extractItem(slot, movable, false);
            if (extracted.isEmpty()) {
                return (startCursor + visited) % targets.size();
            }
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, extracted, false);
            if (!leftover.isEmpty()) {
                ItemStack rollback = ItemHandlerHelper.insertItemStacked(source, leftover, false);
                if (!rollback.isEmpty()) {
                    SkyLogistics.LOGGER.warn("Sky necklace item rollback failed: extracted {}, leftover {}, rollback {}",
                            extracted, leftover, rollback);
                }
            }
            targetEndpoint.recordItemSuccess();
            return -1;
        }
        return (startCursor + visited) % targets.size();
    }

    private static boolean tryMoveToDimensionTarget(IItemHandler source, int slot, ItemStack simulated,
            CachedEndpoint targetEndpoint, BlockEntity targetBlockEntity, long gameTime) {
        long accepted = BeyondDimensionsCompat.insertItem(targetBlockEntity, simulated, simulated.getCount(), true);
        if (accepted <= 0L) {
            targetEndpoint.recordItemFailure(gameTime);
            return false;
        }
        ItemStack extracted = source.extractItem(slot, (int) Math.min(Integer.MAX_VALUE, accepted), false);
        if (extracted.isEmpty()) {
            return false;
        }
        long inserted = BeyondDimensionsCompat.insertItem(targetBlockEntity, extracted, extracted.getCount(), false);
        if (inserted < extracted.getCount()) {
            ItemStack rollback = extracted.copyWithCount((int) (extracted.getCount() - inserted));
            ItemStack rollbackRemainder = ItemHandlerHelper.insertItemStacked(source, rollback, false);
            if (!rollbackRemainder.isEmpty()) {
                SkyLogistics.LOGGER.warn("Sky necklace dimension target rollback failed: extracted {}, inserted {}, rollback {}",
                        extracted, inserted, rollbackRemainder);
            }
        }
        targetEndpoint.recordItemSuccess();
        return true;
    }

    private static boolean tryMoveToPlayer(CachedEndpoint sourceEndpoint, IItemHandler source, int slot,
            ItemStack simulated, IItemHandler target, long gameTime) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, simulated.copy(), true);
        int movable = simulated.getCount() - remainder.getCount();
        if (movable <= 0) {
            return false;
        }
        ItemStack extracted = source.extractItem(slot, movable, false);
        if (extracted.isEmpty()) {
            sourceEndpoint.recordItemFailure(gameTime);
            return false;
        }
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, extracted, false);
        if (!leftover.isEmpty()) {
            ItemStack rollback = ItemHandlerHelper.insertItemStacked(source, leftover, false);
            if (!rollback.isEmpty()) {
                SkyLogistics.LOGGER.warn("Sky necklace player insert rollback failed: extracted {}, leftover {}, rollback {}",
                        extracted, leftover, rollback);
            }
        }
        sourceEndpoint.recordItemSlotSuccess(slot, source.getSlots(), gameTime);
        sourceEndpoint.recordItemSuccess();
        return true;
    }

    private static long packInts(int high, int low) {
        return ((long) high << 32) | (low & 0xffffffffL);
    }

    private static int unpackHigh(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackLow(long packed) {
        return (int) packed;
    }

    private static final class ActiveNecklace {
        private ServerPlayer player;
        private ItemStack necklace;
        private UUID lineId;
        private SkyNecklaceItem.NecklaceMode mode;
        private int priority;
        private FilterListItem.CompiledFilter itemWhitelist;

        private void update(ServerPlayer player, ItemStack necklace, UUID lineId,
                SkyNecklaceItem.NecklaceMode mode, int priority,
                FilterListItem.CompiledFilter itemWhitelist) {
            this.player = player;
            this.necklace = necklace;
            this.lineId = lineId;
            this.mode = mode;
            this.priority = priority;
            this.itemWhitelist = itemWhitelist;
        }

        private ServerPlayer player() { return player; }
        private ItemStack necklace() { return necklace; }
        private UUID lineId() { return lineId; }
        private SkyNecklaceItem.NecklaceMode mode() { return mode; }
        private int priority() { return priority; }
        private FilterListItem.CompiledFilter itemWhitelist() { return itemWhitelist; }
    }

    private record WhitelistCache(ItemStack filter, FilterListItem.CompiledFilter compiled) {
    }

    private record DimensionInsertResult(boolean moved, int checks, boolean complete) {
    }

    private static final class InventoryCountScan {
        private final FilterListItem.CompiledFilter filter;
        private int nextSlot;
        private int slots;
        private long items;

        private InventoryCountScan(FilterListItem.CompiledFilter filter) {
            this.filter = filter;
        }
    }

    private record InventoryCountResult(boolean complete, int slots, int items, int checks) {
    }

    private static final class ExtractCountScan {
        private final FilterListItem.CompiledFilter filter;
        private final boolean exact;
        private final int totalSlots;
        private int nextFlatSlot;
        private int slots;
        private long items;

        private ExtractCountScan(FilterListItem.CompiledFilter filter, boolean exact, int totalSlots) {
            this.filter = filter;
            this.exact = exact;
            this.totalSlots = totalSlots;
        }
    }

    private record ExtractCountResult(boolean complete, int slots, int items, int checks) {
    }

    private record PlayerDetailCache(Object dimensionKey, ActiveNecklaceDetail detail) {
    }

    public record ActiveNecklaceDetail(UUID profileId, String playerName, String profileTexture,
                                       String profileTextureSignature, String dimension, BlockPos pos,
                                       NodeFaceMode mode, boolean maintainMode, int priority) {
    }

    private static final class PlayerMainInventoryHandler implements IItemHandler {
        private static final int MAIN_SLOTS = 36;
        private final Inventory inventory;
        private final FilterListItem.CompiledFilter insertFilter;
        private final int insertSlotLimit;
        private final int exactItemLimit;
        private int matchingWhitelistSlots;
        private int matchingWhitelistItems;

        private PlayerMainInventoryHandler(Inventory inventory, FilterListItem.CompiledFilter insertFilter,
                int insertSlotLimit, int exactItemLimit) {
            this(inventory, insertFilter, insertSlotLimit, exactItemLimit, 0, 0);
        }

        private PlayerMainInventoryHandler(Inventory inventory, FilterListItem.CompiledFilter insertFilter,
                int insertSlotLimit, int exactItemLimit, int matchingWhitelistSlots, int matchingWhitelistItems) {
            this.inventory = inventory;
            this.insertFilter = insertFilter;
            this.insertSlotLimit = Math.max(SkyNecklaceItem.MIN_INSERT_SLOTS,
                    Math.min(MAIN_SLOTS, insertSlotLimit));
            this.exactItemLimit = Math.max(0, exactItemLimit);
            this.matchingWhitelistSlots = matchingWhitelistSlots;
            this.matchingWhitelistItems = matchingWhitelistItems;
        }

        @Override
        public int getSlots() {
            return MAIN_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return valid(slot) ? inventory.getItem(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!valid(slot) || stack.isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }
            ItemStack existing = inventory.getItem(slot);
            int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
            int exactRemaining = Integer.MAX_VALUE;
            if (exactItemLimit > 0) {
                exactRemaining = exactItemLimit - matchingWhitelistItems;
                if (exactRemaining <= 0) return stack;
            }
            if (isInsertLimited() && MaintainedSlotPolicy.blocksInsertionAtSlotLimit(
                    SkyLogisticsConfig.fillMaintainedItemSlots(), matchingWhitelistSlots,
                    insertSlotLimit, existing.isEmpty())) {
                return stack;
            }
            if (existing.isEmpty()) {
                int inserted = Math.min(Math.min(limit, exactRemaining), stack.getCount());
                if (!simulate) {
                    ItemStack copy = stack.copyWithCount(inserted);
                    inventory.setItem(slot, copy);
                    inventory.setChanged();
                    if (insertFilter != null && insertFilter.matches(copy)) {
                        matchingWhitelistSlots++;
                        matchingWhitelistItems = (int)Math.min(Integer.MAX_VALUE,
                                (long)matchingWhitelistItems + inserted);
                    }
                }
                return remainder(stack, inserted);
            }
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }
            int inserted = Math.min(Math.min(limit - existing.getCount(), exactRemaining), stack.getCount());
            if (inserted <= 0) {
                return stack;
            }
            if (!simulate) {
                existing.grow(inserted);
                inventory.setChanged();
                if (insertFilter != null && insertFilter.matches(existing)) {
                    matchingWhitelistItems = (int)Math.min(Integer.MAX_VALUE,
                            (long)matchingWhitelistItems + inserted);
                }
            }
            return remainder(stack, inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!valid(slot) || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int extracted = Math.min(amount, existing.getCount());
            ItemStack result = existing.copyWithCount(extracted);
            if (!simulate) {
                existing.shrink(extracted);
                if (existing.isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
                inventory.setChanged();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return valid(slot) && (insertFilter == null || insertFilter.matches(stack));
        }

        private static boolean valid(int slot) {
            return slot >= 0 && slot < MAIN_SLOTS;
        }

        private boolean isInsertLimited() {
            return insertFilter != null && insertSlotLimit > SkyNecklaceItem.MIN_INSERT_SLOTS;
        }

        private static ItemStack remainder(ItemStack original, int inserted) {
            if (inserted >= original.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = original.copy();
            remainder.shrink(inserted);
            return remainder;
        }
    }
}
