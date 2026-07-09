package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import com.skylogistics.compat.curios.CuriosCompat;
import com.skylogistics.compat.sophisticated.SophisticatedBackpacksCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.SkyNecklaceItem;
import com.skylogistics.network.SkyNetworkRegistry.CachedEndpoint;
import com.skylogistics.util.NodeFaceMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class SkyNecklaceTicker {
    private static final Map<UUID, Integer> ACTIVE_EXTRACTORS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_INSERTERS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_ITEM_INSERTERS = new HashMap<>();
    private static final Map<UUID, List<ActiveNecklaceDetail>> ACTIVE_DETAILS = new HashMap<>();

    private SkyNecklaceTicker() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % SkyLogisticsConfig.skyNecklaceTickInterval() != 0L) {
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
    }

    private static void process(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        Map<UUID, Integer> activeExtractors = new HashMap<>();
        Map<UUID, Integer> activeInserters = new HashMap<>();
        Map<UUID, Integer> activeItemInserters = new HashMap<>();
        Map<UUID, List<ActiveNecklaceDetail>> activeDetails = new HashMap<>();
        List<ActiveNecklace> activeNecklaces = new ArrayList<>();
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
                activeExtractors.merge(lineId, 1, Integer::sum);
            } else {
                activeInserters.merge(lineId, 1, Integer::sum);
            }
            int priority = SkyNecklaceItem.priority(necklace);
            activeDetails.computeIfAbsent(lineId, ignored -> new ArrayList<>())
                    .add(activeDetail(player, mode, priority));
            FilterListItem.CompiledFilter itemWhitelist = itemWhitelist(necklace);
            if (itemWhitelist != null) {
                activeNecklaces.add(new ActiveNecklace(player, necklace, lineId, mode, priority, itemWhitelist));
                if (mode == SkyNecklaceItem.NecklaceMode.INSERT) {
                    activeItemInserters.merge(lineId, 1, Integer::sum);
                }
            }
        }
        activeNecklaces.sort(Comparator.comparingInt(ActiveNecklace::priority).reversed());
        for (List<ActiveNecklaceDetail> details : activeDetails.values()) {
            details.sort(Comparator.comparingInt(ActiveNecklaceDetail::priority).reversed());
        }
        for (ActiveNecklace active : activeNecklaces) {
            if (active.mode() == SkyNecklaceItem.NecklaceMode.EXTRACT) {
                tryExtract(active.player(), active.necklace(), active.lineId(), active.itemWhitelist(), gameTime);
            } else {
                tryInsert(active.player(), active.necklace(), active.lineId(), active.itemWhitelist(), gameTime);
            }
        }
        ACTIVE_EXTRACTORS.clear();
        ACTIVE_EXTRACTORS.putAll(activeExtractors);
        ACTIVE_INSERTERS.clear();
        ACTIVE_INSERTERS.putAll(activeInserters);
        ACTIVE_ITEM_INSERTERS.clear();
        ACTIVE_ITEM_INSERTERS.putAll(activeItemInserters);
        ACTIVE_DETAILS.clear();
        ACTIVE_DETAILS.putAll(activeDetails);
    }

    private static ItemStack activeNecklace(ServerPlayer player) {
        for (ItemStack stack : CuriosCompat.equippedSkyNecklaces(player)) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ActiveNecklaceDetail activeDetail(ServerPlayer player, SkyNecklaceItem.NecklaceMode mode,
            int priority) {
        return new ActiveNecklaceDetail(player.getGameProfile().name(),
                player.level().dimension().identifier().toString(), player.blockPosition().immutable(),
                mode == SkyNecklaceItem.NecklaceMode.EXTRACT ? NodeFaceMode.INPUT : NodeFaceMode.OUTPUT,
                priority);
    }

    private static FilterListItem.CompiledFilter itemWhitelist(ItemStack necklace) {
        ItemStack filter = SkyNecklaceItem.filterList(necklace);
        if (filter.isEmpty() || !FilterListItem.isWhitelist(filter)) {
            return null;
        }
        FilterListItem.CompiledFilter compiled = FilterListItem.compile(filter);
        return compiled.hasItemRules() ? compiled : null;
    }

    private static void tryExtract(ServerPlayer player, ItemStack necklace, UUID lineId,
            FilterListItem.CompiledFilter itemWhitelist, long gameTime) {
        List<CachedEndpoint> targets = SkyNetworkRegistry.lineItemOutputs(player.level().getServer(), player.level().dimension(), lineId);
        if (targets.isEmpty()) {
            return;
        }
        List<IItemHandler> sources = sources(player);
        int slotLimit = SkyNecklaceItem.insertSlots(necklace);
        if (slotLimit > SkyNecklaceItem.MIN_INSERT_SLOTS
                && countMatchingSlots(sources, itemWhitelist) <= slotLimit) {
            return;
        }
        int transferLimit = SkyLogisticsConfig.nodeItemTransferLimit();
        for (IItemHandler source : sources) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack simulated = source.extractItem(slot, transferLimit, true);
                if (simulated.isEmpty() || shouldSkipSourceStack(simulated) || !itemWhitelist.matches(simulated)) {
                    continue;
                }
                if (tryMove(source, slot, simulated, targets, gameTime)) {
                    return;
                }
            }
        }
    }

    private static List<IItemHandler> sources(ServerPlayer player) {
        List<IItemHandler> sources = new ArrayList<>();
        sources.add(new PlayerMainInventoryHandler(player.getInventory(), null, PlayerMainInventoryHandler.MAIN_SLOTS));
        sources.addAll(SophisticatedBackpacksCompat.carriedBackpackHandlers(player));
        return sources;
    }

    private static int countMatchingSlots(List<IItemHandler> sources, FilterListItem.CompiledFilter itemWhitelist) {
        int matching = 0;
        for (IItemHandler source : sources) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack stack = source.getStackInSlot(slot);
                if (!stack.isEmpty() && !shouldSkipSourceStack(stack) && itemWhitelist.matches(stack)) {
                    matching++;
                }
            }
        }
        return matching;
    }

    private static void tryInsert(ServerPlayer player, ItemStack necklace, UUID lineId,
            FilterListItem.CompiledFilter itemWhitelist, long gameTime) {
        List<CachedEndpoint> sources = SkyNetworkRegistry.lineItemInputs(player.level().getServer(), player.level().dimension(), lineId);
        if (sources.isEmpty()) {
            return;
        }
        IItemHandler target = new PlayerMainInventoryHandler(player.getInventory(), itemWhitelist,
                SkyNecklaceItem.insertSlots(necklace));
        int transferLimit = SkyLogisticsConfig.nodeItemTransferLimit();
        for (CachedEndpoint sourceEndpoint : sources) {
            if (!sourceEndpoint.canTryItems(gameTime)
                    || !sourceEndpoint.node().isFaceRedstoneAllowed(sourceEndpoint.direction())
                    || !sourceEndpoint.node().isItemsEnabled(sourceEndpoint.direction())) {
                continue;
            }
            BlockEntity sourceBlockEntity = sourceEndpoint.targetBlockEntity();
            if (sourceBlockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost
                    && tryInsertFromDimensionSource(sourceEndpoint, sourceBlockEntity, itemWhitelist, target,
                    transferLimit, gameTime)) {
                return;
            }
            if (sourceBlockEntity instanceof BeyondDimensionsCompat.NetworkBoundHost) {
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
            for (int attempts = 0; attempts < slots; attempts++) {
                int slot = sourceEndpoint.node().nextItemStart(slots);
                ItemStack simulated = source.extractItem(slot, transferLimit, true);
                if (simulated.isEmpty()
                        || !sourceEndpoint.node().allowsItem(sourceEndpoint.direction(), simulated)
                        || !itemWhitelist.matches(simulated)) {
                    continue;
                }
                if (tryMoveToPlayer(sourceEndpoint, source, slot, simulated, target, gameTime)) {
                    return;
                }
            }
        }
    }

    private static boolean shouldSkipSourceStack(ItemStack stack) {
        return stack.is(com.skylogistics.registry.ModItems.SKY_NECKLACE.get())
                || SophisticatedBackpacksCompat.isBackpackItem(stack);
    }

    private static boolean tryInsertFromDimensionSource(CachedEndpoint sourceEndpoint, BlockEntity sourceBlockEntity,
            FilterListItem.CompiledFilter itemWhitelist, IItemHandler target, int transferLimit, long gameTime) {
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

    private static boolean tryMove(IItemHandler source, int slot, ItemStack simulated, List<CachedEndpoint> targets,
            long gameTime) {
        for (CachedEndpoint targetEndpoint : targets) {
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
                    return true;
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
                return false;
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
            return true;
        }
        return false;
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
        sourceEndpoint.recordItemSlotSuccess(slot, source.getSlots());
        sourceEndpoint.recordItemSuccess();
        return true;
    }

    private record ActiveNecklace(ServerPlayer player, ItemStack necklace, UUID lineId,
                                  SkyNecklaceItem.NecklaceMode mode, int priority,
                                  FilterListItem.CompiledFilter itemWhitelist) {
    }

    public record ActiveNecklaceDetail(String playerName, String dimension, BlockPos pos, NodeFaceMode mode,
                                       int priority) {
    }

    private static final class PlayerMainInventoryHandler implements IItemHandler {
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
