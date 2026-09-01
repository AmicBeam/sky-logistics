package com.skylogistics.compat.botania;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

final class ReflectiveManaHandlerBridge implements ManaHandlerBridge {
    private static final int UNKNOWN_RECEIVE_SPACE = 1000;
    private static final String MANA_POOL_BLOCK_ENTITY =
            "vazkii.botania.common.block.block_entity.mana.ManaPoolBlockEntity";

    private final Object receiver;
    private final Object sparkAttachable;
    private final Method getCurrentMana;
    private final Method isFull;
    private final Method receiveMana;
    private final Method getMaxMana;
    private final Method getAvailableSpaceForMana;
    private final boolean creativeManaPool;

    private ReflectiveManaHandlerBridge(Object receiver, Object sparkAttachable, Method getCurrentMana,
            Method isFull, Method receiveMana, Method getMaxMana, Method getAvailableSpaceForMana,
            boolean creativeManaPool) {
        this.receiver = receiver;
        this.sparkAttachable = sparkAttachable;
        this.getCurrentMana = getCurrentMana;
        this.isFull = isFull;
        this.receiveMana = receiveMana;
        this.getMaxMana = getMaxMana;
        this.getAvailableSpaceForMana = getAvailableSpaceForMana;
        this.creativeManaPool = creativeManaPool;
    }

    static ManaHandlerBridge create(Object receiver, Object sparkAttachable) {
        if (receiver == null) {
            return null;
        }
        Method getCurrentMana = findMethod(receiver.getClass(), "getCurrentMana");
        Method isFull = findMethod(receiver.getClass(), "isFull");
        Method receiveMana = findMethod(receiver.getClass(), "receiveMana", int.class);
        if (getCurrentMana == null || isFull == null || receiveMana == null) {
            return null;
        }
        return new ReflectiveManaHandlerBridge(receiver, sparkAttachable, getCurrentMana, isFull, receiveMana,
                findMethod(receiver.getClass(), "getMaxMana"),
                sparkAttachable == null ? null : findMethod(sparkAttachable.getClass(), "getAvailableSpaceForMana"),
                isCreativeManaPool(receiver));
    }

    @Override
    public int getCurrentMana() {
        return Math.max(0, invokeInt(receiver, getCurrentMana, 0));
    }

    @Override
    public int getMaxMana() {
        int current = getCurrentMana();
        int knownMax = knownMaxMana();
        if (knownMax >= current) {
            return knownMax;
        }
        int space = availableSparkSpace();
        if (space > 0) {
            return saturatingAdd(current, space);
        }
        return isFull() ? current : saturatingAdd(current, UNKNOWN_RECEIVE_SPACE);
    }

    @Override
    public boolean canExtract() {
        return getCurrentMana() > 0;
    }

    @Override
    public boolean canReceive() {
        return acceptedMana(Integer.MAX_VALUE) > 0;
    }

    @Override
    public int extractMana(int amount, boolean simulate) {
        if (amount <= 0 || !canExtract()) {
            return 0;
        }
        int requested = Math.min(amount, getCurrentMana());
        if (simulate) {
            return requested;
        }
        int before = getCurrentMana();
        if (!receiveMana(-requested)) {
            return 0;
        }
        int extracted = Math.min(requested, Math.max(0, before - getCurrentMana()));
        // Botania creative pools intentionally keep getCurrentMana() pinned at their capacity.
        // Their void receiveMana API therefore has no observable before/after delta even though
        // Botania treats the requested mana as supplied.
        return extracted > 0 || !creativeManaPool ? extracted : requested;
    }

    @Override
    public int insertMana(int amount, boolean simulate) {
        int accepted = acceptedMana(amount);
        if (accepted <= 0) {
            return 0;
        }
        if (simulate) {
            return accepted;
        }
        int before = getCurrentMana();
        if (!receiveMana(accepted)) {
            return 0;
        }
        return Math.min(accepted, Math.max(0, getCurrentMana() - before));
    }

    private int acceptedMana(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int current = getCurrentMana();
        int knownMax = knownMaxMana();
        if (knownMax >= current) {
            return Math.min(amount, knownMax - current);
        }
        int space = availableSparkSpace();
        if (space > 0) {
            return Math.min(amount, space);
        }
        return isFull() ? 0 : amount;
    }

    private int knownMaxMana() {
        if (getMaxMana == null) {
            return -1;
        }
        return Math.max(-1, invokeInt(receiver, getMaxMana, -1));
    }

    private int availableSparkSpace() {
        if (sparkAttachable == null || getAvailableSpaceForMana == null) {
            return 0;
        }
        return Math.max(0, invokeInt(sparkAttachable, getAvailableSpaceForMana, 0));
    }

    private boolean isFull() {
        return invokeBoolean(receiver, isFull, true);
    }

    private boolean receiveMana(int mana) {
        try {
            receiveMana.invoke(receiver, mana);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isCreativeManaPool(Object receiver) {
        if (!hasTypeName(receiver.getClass(), MANA_POOL_BLOCK_ENTITY)) {
            return false;
        }
        Object state = invokeObject(receiver, findMethod(receiver.getClass(), "getBlockState"));
        Object block = state == null ? null : invokeObject(state, findMethod(state.getClass(), "getBlock"));
        if (block == null) {
            return false;
        }
        Method isCreative = findMethod(block.getClass(), "isCreative");
        if (isCreative != null) {
            return invokeBoolean(block, isCreative, false);
        }
        Object variant = readField(block, "variant");
        return variant instanceof Enum<?> value && "CREATIVE".equals(value.name());
    }

    private static boolean hasTypeName(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (name.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static Object invokeObject(Object target, Method method) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static int invokeInt(Object target, Method method, int fallback) {
        try {
            Object result = method.invoke(target);
            return result instanceof Number number ? number.intValue() : fallback;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean invokeBoolean(Object target, Method method, boolean fallback) {
        try {
            Object result = method.invoke(target);
            return result instanceof Boolean value ? value : fallback;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        return findMethod(type, name, parameterTypes, new HashSet<>());
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] parameterTypes, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) {
            return null;
        }
        try {
            Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            // Try public interfaces and superclasses below.
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            Method method = findMethod(interfaceType, name, parameterTypes, visited);
            if (method != null) {
                return method;
            }
        }
        return findMethod(type.getSuperclass(), name, parameterTypes, visited);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Try the superclass below.
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static int saturatingAdd(int left, int right) {
        long sum = (long) left + right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
