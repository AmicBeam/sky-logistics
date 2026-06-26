package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.curios.CuriosCompat;
import com.skylogistics.compat.sophisticated.SophisticatedBackpacksCompat;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.SkyNecklaceItem;
import com.skylogistics.network.SkyNetworkRegistry.CachedEndpoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class SkyNecklaceTicker {
    private static final int TICK_INTERVAL = 5;
    private static final Map<UUID, Integer> ACTIVE_EXTRACTORS = new HashMap<>();

    private SkyNecklaceTicker() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        process(event.getServer());
    }

    public static int activeExtractorCount(UUID lineId) {
        return ACTIVE_EXTRACTORS.getOrDefault(lineId, 0);
    }

    public static void clear() {
        ACTIVE_EXTRACTORS.clear();
    }

    private static void process(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        boolean moveThisTick = gameTime % TICK_INTERVAL == 0L;
        Map<UUID, Integer> active = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack necklace = activeNecklace(player);
            if (necklace.isEmpty()) {
                continue;
            }
            UUID lineId = SkyNecklaceItem.lineId(necklace);
            if (lineId == null) {
                continue;
            }
            active.merge(lineId, 1, Integer::sum);
            if (moveThisTick && SkyNecklaceItem.hasValidItemWhitelist(necklace)) {
                tryExtract(player, necklace, lineId, gameTime);
            }
        }
        ACTIVE_EXTRACTORS.clear();
        ACTIVE_EXTRACTORS.putAll(active);
    }

    private static ItemStack activeNecklace(ServerPlayer player) {
        for (ItemStack stack : CuriosCompat.equippedSkyNecklaces(player)) {
            if (SkyNecklaceItem.mode(stack) == SkyNecklaceItem.NecklaceMode.EXTRACT) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void tryExtract(ServerPlayer player, ItemStack necklace, UUID lineId, long gameTime) {
        List<CachedEndpoint> targets = SkyNetworkRegistry.lineItemOutputs(player.server, player.level().dimension(), lineId);
        if (targets.isEmpty()) {
            return;
        }
        int transferLimit = SkyLogisticsConfig.nodeItemTransferLimit();
        for (IItemHandler source : sources(player)) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack simulated = source.extractItem(slot, transferLimit, true);
                if (simulated.isEmpty() || shouldSkipSourceStack(simulated)
                        || !SkyNecklaceItem.matchesWhitelist(necklace, simulated)) {
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
        sources.add(new PlayerMainInventoryHandler(player.getInventory()));
        sources.addAll(SophisticatedBackpacksCompat.carriedBackpackHandlers(player));
        return sources;
    }

    private static boolean shouldSkipSourceStack(ItemStack stack) {
        return stack.is(com.skylogistics.registry.ModItems.SKY_NECKLACE.get())
                || SophisticatedBackpacksCompat.isBackpackItem(stack);
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
            targetEndpoint.node().setChanged();
            return true;
        }
        return false;
    }

    private static final class PlayerMainInventoryHandler implements IItemHandler {
        private static final int MAIN_SLOTS = 36;
        private final Inventory inventory;

        private PlayerMainInventoryHandler(Inventory inventory) {
            this.inventory = inventory;
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
            return valid(slot);
        }

        private static boolean valid(int slot) {
            return slot >= 0 && slot < MAIN_SLOTS;
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
