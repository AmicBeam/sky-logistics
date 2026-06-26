package com.skylogistics.compat.sophisticated;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.curios.CuriosCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public final class SophisticatedBackpacksCompat {
    private static final String MOD_ID = "sophisticatedbackpacks";
    private static boolean initialized;
    private static boolean available;
    private static boolean warned;
    private static Class<?> backpackItemClass;

    private SophisticatedBackpacksCompat() {
    }

    public static boolean isBackpackItem(ItemStack stack) {
        return !stack.isEmpty() && init() && backpackItemClass.isInstance(stack.getItem());
    }

    public static List<IItemHandler> carriedBackpackHandlers(ServerPlayer player) {
        if (!init()) {
            return List.of();
        }
        List<IItemHandler> handlers = new ArrayList<>();
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            addBackpackHandler(player.getInventory().getItem(slot), seen, handlers);
        }
        for (ItemStack stack : CuriosCompat.equippedStacks(player)) {
            addBackpackHandler(stack, seen, handlers);
        }
        return handlers;
    }

    private static void addBackpackHandler(ItemStack stack, Set<ItemStack> seen, List<IItemHandler> handlers) {
        if (stack.isEmpty() || !seen.add(stack) || !isBackpackItem(stack)) {
            return;
        }
        IItemHandler handler = inventoryHandler(stack);
        if (handler != null) {
            handlers.add(handler);
        }
    }

    private static IItemHandler inventoryHandler(ItemStack stack) {
        try {
            return stack.getCapability(Capabilities.ItemHandler.ITEM);
        } catch (RuntimeException error) {
            warnOnce(error);
            return null;
        }
    }

    private static boolean init() {
        if (initialized) {
            return available;
        }
        initialized = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return false;
        }
        try {
            backpackItemClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem");
            available = true;
        } catch (ReflectiveOperationException error) {
            warnOnce(error);
            available = false;
        }
        return available;
    }

    private static void warnOnce(Exception error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn("Sophisticated Backpacks compatibility disabled; API lookup failed.", error);
        }
    }
}
