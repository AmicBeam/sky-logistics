package com.skylogistics.compat.curios;

import com.skylogistics.SkyLogistics;
import com.skylogistics.registry.ModItems;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class CuriosCompat {
    private static final String MOD_ID = "curios";
    private static boolean initialized;
    private static boolean available;
    private static boolean warned;
    private static Method getCuriosInventory;
    private static Method getEquippedCurios;
    private static Method getSlots;
    private static Method getStackInSlot;

    private CuriosCompat() {
    }

    public static List<ItemStack> equippedStacks(Player player) {
        return equippedStacksMatching(player, stack -> true);
    }

    public static List<ItemStack> equippedSkyNecklaces(Player player) {
        return equippedStacksMatching(player, stack -> stack.is(ModItems.SKY_NECKLACE.get()));
    }

    private static List<ItemStack> equippedStacksMatching(Player player, Predicate<ItemStack> predicate) {
        Object handler = equippedHandler(player);
        if (handler == null) {
            return List.of();
        }
        try {
            int slots = ((Number) getSlots.invoke(handler)).intValue();
            List<ItemStack> stacks = new ArrayList<>();
            for (int slot = 0; slot < slots; slot++) {
                Object value = getStackInSlot.invoke(handler, slot);
                if (value instanceof ItemStack stack && !stack.isEmpty() && predicate.test(stack)) {
                    stacks.add(stack);
                }
            }
            return stacks;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warnOnce(error);
            return List.of();
        }
    }

    private static Object equippedHandler(Player player) {
        if (!init()) {
            return null;
        }
        try {
            Object handler = resolveOptional(getCuriosInventory.invoke(null, player));
            if (handler == null) {
                return null;
            }
            return getEquippedCurios.invoke(handler);
        } catch (ReflectiveOperationException | RuntimeException error) {
            warnOnce(error);
            return null;
        }
    }

    private static Object resolveOptional(Object value) throws ReflectiveOperationException {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        if (value == null) {
            return null;
        }
        Method resolve;
        try {
            resolve = value.getClass().getMethod("resolve");
        } catch (NoSuchMethodException ignored) {
            return value;
        }
        Object resolved = resolve.invoke(value);
        return resolved instanceof Optional<?> optional ? optional.orElse(null) : resolved;
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
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            getCuriosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            getEquippedCurios = handler.getMethod("getEquippedCurios");
            Class<?> equippedHandler = getEquippedCurios.getReturnType();
            getSlots = equippedHandler.getMethod("getSlots");
            getStackInSlot = equippedHandler.getMethod("getStackInSlot", int.class);
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
            SkyLogistics.LOGGER.warn("Curios compatibility disabled; API lookup failed.", error);
        }
    }
}
