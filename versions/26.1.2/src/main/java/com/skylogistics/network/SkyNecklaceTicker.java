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
import com.skylogistics.util.ItemHandler;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.TransferCompat;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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
            if (mode == SkyNecklaceItem.NecklaceMode.EXTRACT) {
                ACTIVE_EXTRACTORS.merge(lineId, 1, Integer::sum);
            } else {
                ACTIVE_INSERTERS.merge(lineId, 1, Integer::sum);
            }
            int priority = SkyNecklaceItem.priority(necklace);
            UUID playerId = player.getUUID();
            EQUIPPED_PLAYER_IDS.add(playerId);
            ACTIVE_DETAILS.computeIfAbsent(lineId, ignored -> new ArrayList<>())
                    .add(activeDetail(player, mode, priority));
            FilterListItem.CompiledFilter itemWhitelist = itemWhitelist(playerId, necklace);
            if (itemWhitelist != null) {
                ActiveNecklace active = PLAYER_NECKLACES.computeIfAbsent(playerId, ignored -> new ActiveNecklace());
                active.update(player, necklace, lineId, mode, priority, itemWhitelist);
                ACTIVE_NECKLACES.add(active);
                TRANSFER_PLAYER_IDS.add(playerId);
                if (mode == SkyNecklaceItem.NecklaceMode.INSERT) {
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
            if (active.mode() == SkyNecklaceItem.NecklaceMode.EXTRACT) {
                tryExtract(active.player(), active.necklace(), active.lineId(), active.itemWhitelist(), gameTime);
            } else {
                tryInsert(active.player(), active.necklace(), active.lineId(), active.itemWhitelist(), gameTime);
            }
        }
        EXTRACT_SLOT_CURSORS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        EXTRACT_TARGET_CURSORS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
        INSERT_ENDPOINT_CURSORS.keySet().removeIf(playerId -> !TRANSFER_PLAYER_IDS.contains(playerId));
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
        NodeFaceMode faceMode = mode == SkyNecklaceItem.NecklaceMode.EXTRACT
                ? NodeFaceMode.INPUT : NodeFaceMode.OUTPUT;
        Object dimensionKey = player.level().dimension();
        PlayerDetailCache cached = PLAYER_DETAILS.get(profile.id());
        ActiveNecklaceDetail cachedDetail = cached == null ? null : cached.detail();
        if (cachedDetail != null && cached.dimensionKey().equals(dimensionKey)
                && cachedDetail.playerName().equals(profile.name())
                && cachedDetail.profileTexture().equals(textureValue)
                && cachedDetail.profileTextureSignature().equals(textureSignature)
                && cachedDetail.pos().equals(pos) && cachedDetail.mode() == faceMode
                && cachedDetail.priority() == priority) {
            return cachedDetail;
        }
        String dimension = player.level().dimension().identifier().toString();
        ActiveNecklaceDetail detail = new ActiveNecklaceDetail(profile.id(), profile.name(), textureValue,
                textureSignature, dimension, pos, faceMode, priority);
        PLAYER_DETAILS.put(profile.id(), new PlayerDetailCache(dimensionKey, detail));
        return detail;
    }

    private static Property firstTexture(GameProfile profile) {
        for (Property property : profile.properties().get("textures")) {
            return property;
        }
        return null;
    }

    private static FilterListItem.CompiledFilter itemWhitelist(UUID playerId, ItemStack necklace) {
        ItemStack filter = SkyNecklaceItem.filterList(necklace);
        if (filter.isEmpty() || !FilterListItem.isWhitelist(filter)) {
            ITEM_WHITELISTS.remove(playerId);
            return null;
        }
        WhitelistCache cached = ITEM_WHITELISTS.get(playerId);
        if (cached != null && ItemStack.isSameItemSameComponents(cached.filter(), filter)) {
            return cached.compiled();
        }
        FilterListItem.CompiledFilter compiled = FilterListItem.compile(filter);
        if (!compiled.hasItemRules()) {
            ITEM_WHITELISTS.remove(playerId);
            return null;
        }
        ITEM_WHITELISTS.put(playerId, new WhitelistCache(filter.copy(), compiled));
        return compiled;
    }

    private static void tryExtract(ServerPlayer player, ItemStack necklace, UUID lineId,
            FilterListItem.CompiledFilter itemWhitelist, long gameTime) {
        List<CachedEndpoint> targets = SkyNetworkRegistry.lineItemOutputs(player.level().getServer(), player.level().dimension(), lineId);
        if (targets.isEmpty()) {
            return;
        }
        List<ItemHandler> sources = sources(player);
        int totalSlots = totalSlots(sources);
        if (totalSlots <= 0) {
            return;
        }
        UUID playerId = player.getUUID();
        int scanBudget = Math.min(totalSlots, SkyLogisticsConfig.skyNecklaceSlotScansPerTick());
        int slotLimit = SkyNecklaceItem.insertSlots(necklace);
        if (slotLimit > SkyNecklaceItem.MIN_INSERT_SLOTS && totalSlots <= scanBudget) {
            long countResult = countMatchingSlots(sources, itemWhitelist, slotLimit + 1);
            if (unpackHigh(countResult) <= slotLimit) return;
            scanBudget = Math.max(1, scanBudget - unpackLow(countResult));
        }
        int cursor = Math.floorMod(EXTRACT_SLOT_CURSORS.getOrDefault(playerId, 0), totalSlots);
        int transferLimit = SkyLogisticsConfig.nodeItemTransferLimit();
        int scanned = 0;
        while (scanned < scanBudget) {
            int flatIndex = (cursor + scanned) % totalSlots;
            long handlerSlot = handlerSlotAt(sources, flatIndex);
            scanned++;
            if (handlerSlot < 0L) {
                continue;
            }
            ItemHandler handler = sources.get(unpackHigh(handlerSlot));
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

    private static List<ItemHandler> sources(ServerPlayer player) {
        List<ItemHandler> sources = new ArrayList<>();
        sources.add(new PlayerMainInventoryHandler(player.getInventory(), null, PlayerMainInventoryHandler.MAIN_SLOTS));
        sources.addAll(SophisticatedBackpacksCompat.carriedBackpackHandlers(player));
        return sources;
    }

    private static long countMatchingSlots(List<ItemHandler> sources, FilterListItem.CompiledFilter itemWhitelist,
            int stopAt) {
        int matching = 0;
        int checks = 0;
        for (ItemHandler source : sources) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                checks++;
                ItemStack stack = source.getStackInSlot(slot);
                if (!stack.isEmpty() && !shouldSkipSourceStack(stack) && itemWhitelist.matches(stack)) {
                    matching++;
                    if (matching >= stopAt) {
                        return packInts(matching, checks);
                    }
                }
            }
        }
        return packInts(matching, checks);
    }

    private static int totalSlots(List<ItemHandler> sources) {
        int totalSlots = 0;
        for (ItemHandler source : sources) {
            totalSlots += source.getSlots();
        }
        return totalSlots;
    }

    private static long handlerSlotAt(List<ItemHandler> sources, int flatIndex) {
        int offset = flatIndex;
        for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
            ItemHandler source = sources.get(sourceIndex);
            int slots = source.getSlots();
            if (offset < slots) {
                return packInts(sourceIndex, offset);
            }
            offset -= slots;
        }
        return -1L;
    }

    private static void tryInsert(ServerPlayer player, ItemStack necklace, UUID lineId,
            FilterListItem.CompiledFilter itemWhitelist, long gameTime) {
        List<CachedEndpoint> sources = SkyNetworkRegistry.lineItemInputs(player.level().getServer(), player.level().dimension(), lineId);
        if (sources.isEmpty()) {
            return;
        }
        ItemHandler target = new PlayerMainInventoryHandler(player.getInventory(), itemWhitelist,
                SkyNecklaceItem.insertSlots(necklace));
        UUID playerId = player.getUUID();
        int sourceCursor = Math.floorMod(INSERT_ENDPOINT_CURSORS.getOrDefault(playerId, 0), sources.size());
        int slotScanBudget = SkyLogisticsConfig.skyNecklaceSlotScansPerTick();
        int scannedSlots = 0;
        int visitedEndpoints = 0;
        int transferLimit = SkyLogisticsConfig.nodeItemTransferLimit();
        for (int sourceOffset = 0; sourceOffset < sources.size() && scannedSlots < slotScanBudget; sourceOffset++) {
            int sourceIndex = (sourceCursor + sourceOffset) % sources.size();
            visitedEndpoints = sourceOffset + 1;
            CachedEndpoint sourceEndpoint = sources.get(sourceIndex);
            if (!sourceEndpoint.canTryItems(gameTime)
                    || !sourceEndpoint.node().isFaceRedstoneAllowed(sourceEndpoint.direction())
                    || !sourceEndpoint.node().isItemsEnabled(sourceEndpoint.direction())) {
                continue;
            }
            BlockEntity sourceBlockEntity = sourceEndpoint.targetBlockEntity();
            if (sourceBlockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost
                    && tryInsertFromDimensionSource(sourceEndpoint, sourceBlockEntity, itemWhitelist, target,
                    transferLimit, gameTime)) {
                INSERT_ENDPOINT_CURSORS.put(playerId, (sourceIndex + 1) % sources.size());
                return;
            }
            if (sourceBlockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
                continue;
            }
            ItemHandler source = sourceEndpoint.itemHandler(gameTime);
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
                    INSERT_ENDPOINT_CURSORS.put(playerId, (sourceIndex + 1) % sources.size());
                    return;
                }
            }
        }
        INSERT_ENDPOINT_CURSORS.put(playerId, (sourceCursor + Math.max(visitedEndpoints, 1)) % sources.size());
    }

    private static boolean shouldSkipSourceStack(ItemStack stack) {
        return stack.is(com.skylogistics.registry.ModItems.SKY_NECKLACE.get())
                || SophisticatedBackpacksCompat.isBackpackItem(stack);
    }

    private static boolean tryInsertFromDimensionSource(CachedEndpoint sourceEndpoint, BlockEntity sourceBlockEntity,
            FilterListItem.CompiledFilter itemWhitelist, ItemHandler target, int transferLimit, long gameTime) {
        for (ItemStack sample : itemWhitelist.itemSamples()) {
            BeyondDimensionsCompat.ItemResource resource = BeyondDimensionsCompat.itemResourceForStack(
                    sourceBlockEntity, sample);
            if (tryMoveDimensionToPlayer(sourceEndpoint, sourceBlockEntity, resource, itemWhitelist, target,
                    transferLimit, gameTime)) {
                return true;
            }
        }
        for (TagKey<Item> tag : itemWhitelist.itemTags()) {
            BeyondDimensionsCompat.ItemResource resource = BeyondDimensionsCompat.itemResourceForTag(
                    sourceBlockEntity, tag);
            if (tryMoveDimensionToPlayer(sourceEndpoint, sourceBlockEntity, resource, itemWhitelist, target,
                    transferLimit, gameTime)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryMoveDimensionToPlayer(CachedEndpoint sourceEndpoint, BlockEntity sourceBlockEntity,
            BeyondDimensionsCompat.ItemResource resource, FilterListItem.CompiledFilter itemWhitelist,
            ItemHandler target, int transferLimit, long gameTime) {
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
        ItemStack remainder = TransferCompat.insertItemStacked(target, simulated.copy(), true);
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
        ItemStack leftover = TransferCompat.insertItemStacked(target, extracted, false);
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

    private static int tryMove(ItemHandler source, int slot, ItemStack simulated,
            List<CachedEndpoint> targets, int startCursor, int attemptLimit, long gameTime) {
        int visited = 0;
        int limit = Math.min(targets.size(), Math.max(1, attemptLimit));
        while (visited < limit) {
            CachedEndpoint targetEndpoint = targets.get(Math.floorMod(startCursor + visited, targets.size()));
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
            ItemHandler target = targetEndpoint.itemHandler(gameTime);
            if (target == null) {
                continue;
            }
            ItemStack remainder = TransferCompat.insertItemStacked(target, simulated.copy(), true);
            int movable = simulated.getCount() - remainder.getCount();
            if (movable <= 0) {
                targetEndpoint.recordItemFailure(gameTime);
                continue;
            }
            ItemStack extracted = source.extractItem(slot, movable, false);
            if (extracted.isEmpty()) {
                return (startCursor + visited) % targets.size();
            }
            ItemStack leftover = TransferCompat.insertItemStacked(target, extracted, false);
            if (!leftover.isEmpty()) {
                ItemStack rollback = TransferCompat.insertItemStacked(source, leftover, false);
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

    private static boolean tryMoveToDimensionTarget(ItemHandler source, int slot, ItemStack simulated,
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
            ItemStack rollbackRemainder = TransferCompat.insertItemStacked(source, rollback, false);
            if (!rollbackRemainder.isEmpty()) {
                SkyLogistics.LOGGER.warn("Sky necklace dimension target rollback failed: extracted {}, inserted {}, rollback {}",
                        extracted, inserted, rollbackRemainder);
            }
        }
        targetEndpoint.recordItemSuccess();
        return true;
    }

    private static boolean tryMoveToPlayer(CachedEndpoint sourceEndpoint, ItemHandler source, int slot,
            ItemStack simulated, ItemHandler target, long gameTime) {
        ItemStack remainder = TransferCompat.insertItemStacked(target, simulated.copy(), true);
        int movable = simulated.getCount() - remainder.getCount();
        if (movable <= 0) {
            return false;
        }
        ItemStack extracted = source.extractItem(slot, movable, false);
        if (extracted.isEmpty()) {
            sourceEndpoint.recordItemFailure(gameTime);
            return false;
        }
        ItemStack leftover = TransferCompat.insertItemStacked(target, extracted, false);
        if (!leftover.isEmpty()) {
            ItemStack rollback = TransferCompat.insertItemStacked(source, leftover, false);
            if (!rollback.isEmpty()) {
                SkyLogistics.LOGGER.warn("Sky necklace player insert rollback failed: extracted {}, leftover {}, rollback {}",
                        extracted, leftover, rollback);
            }
        }
        sourceEndpoint.recordItemSlotSuccess(slot, source.getSlots());
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

    private record PlayerDetailCache(Object dimensionKey, ActiveNecklaceDetail detail) {
    }

    public record ActiveNecklaceDetail(UUID profileId, String playerName, String profileTexture,
                                       String profileTextureSignature, String dimension, BlockPos pos,
                                       NodeFaceMode mode, int priority) {
    }

    private static final class PlayerMainInventoryHandler implements ItemHandler {
        private static final int MAIN_SLOTS = 36;
        private final Inventory inventory;
        private final FilterListItem.CompiledFilter insertFilter;
        private final int insertSlotLimit;

        private PlayerMainInventoryHandler(Inventory inventory, FilterListItem.CompiledFilter insertFilter,
                int insertSlotLimit) {
            this.inventory = inventory;
            this.insertFilter = insertFilter;
            this.insertSlotLimit = Math.max(SkyNecklaceItem.MIN_INSERT_SLOTS,
                    Math.min(MAIN_SLOTS, insertSlotLimit));
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
            if (isInsertLimited() && matchingWhitelistSlots() >= insertSlotLimit) {
                return stack;
            }
            if (existing.isEmpty()) {
                int inserted = Math.min(limit, stack.getCount());
                if (!simulate) {
                    ItemStack copy = stack.copyWithCount(inserted);
                    inventory.setItem(slot, copy);
                    inventory.setChanged();
                }
                return remainder(stack, inserted);
            }
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }
            int inserted = Math.min(limit - existing.getCount(), stack.getCount());
            if (inserted <= 0) {
                return stack;
            }
            if (!simulate) {
                existing.grow(inserted);
                inventory.setChanged();
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

        private int matchingWhitelistSlots() {
            int matching = 0;
            for (int slot = 0; slot < MAIN_SLOTS; slot++) {
                ItemStack existing = inventory.getItem(slot);
                if (existing.isEmpty() || !insertFilter.matches(existing)) {
                    continue;
                }
                matching++;
            }
            return matching;
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
