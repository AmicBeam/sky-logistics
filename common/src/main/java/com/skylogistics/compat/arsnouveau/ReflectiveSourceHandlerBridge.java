package com.skylogistics.compat.arsnouveau;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

final class ReflectiveSourceHandlerBridge implements SourceHandlerBridge {
    private final Object handler;
    private final Method getSource;
    private final Method getMaxSource;
    private final Method getSourceCapacity;
    private final Method canAcceptSource;
    private final Method canAcceptSourceAmount;
    private final Method canProvideSource;
    private final Method canProvideSourceAmount;
    private final Method canReceive;
    private final Method canExtract;
    private final Method receiveSource;
    private final Method extractSource;
    private final Method addSourceSimulated;
    private final Method addSource;
    private final Method removeSourceSimulated;
    private final Method removeSource;

    private ReflectiveSourceHandlerBridge(Object handler, Method getSource, Method getMaxSource,
            Method getSourceCapacity, Method canAcceptSource, Method canAcceptSourceAmount, Method canProvideSource,
            Method canProvideSourceAmount, Method canReceive, Method canExtract, Method receiveSource,
            Method extractSource, Method addSourceSimulated, Method addSource, Method removeSourceSimulated,
            Method removeSource) {
        this.handler = handler;
        this.getSource = getSource;
        this.getMaxSource = getMaxSource;
        this.getSourceCapacity = getSourceCapacity;
        this.canAcceptSource = canAcceptSource;
        this.canAcceptSourceAmount = canAcceptSourceAmount;
        this.canProvideSource = canProvideSource;
        this.canProvideSourceAmount = canProvideSourceAmount;
        this.canReceive = canReceive;
        this.canExtract = canExtract;
        this.receiveSource = receiveSource;
        this.extractSource = extractSource;
        this.addSourceSimulated = addSourceSimulated;
        this.addSource = addSource;
        this.removeSourceSimulated = removeSourceSimulated;
        this.removeSource = removeSource;
    }

    static SourceHandlerBridge create(Object handler) {
        if (handler == null) {
            return null;
        }
        Class<?> type = handler.getClass();
        Method getSource = findMethod(type, "getSource");
        Method getMaxSource = findMethod(type, "getMaxSource");
        Method getSourceCapacity = findMethod(type, "getSourceCapacity");
        Method receiveSource = findMethod(type, "receiveSource", int.class, boolean.class);
        Method extractSource = findMethod(type, "extractSource", int.class, boolean.class);
        Method addSourceSimulated = findMethod(type, "addSource", int.class, boolean.class);
        Method addSource = findMethod(type, "addSource", int.class);
        Method removeSourceSimulated = findMethod(type, "removeSource", int.class, boolean.class);
        Method removeSource = findMethod(type, "removeSource", int.class);
        if (getSource == null || (getMaxSource == null && getSourceCapacity == null)
                || (receiveSource == null && addSourceSimulated == null && addSource == null)
                || (extractSource == null && removeSourceSimulated == null && removeSource == null)) {
            return null;
        }
        return new ReflectiveSourceHandlerBridge(handler, getSource, getMaxSource, getSourceCapacity,
                findMethod(type, "canAcceptSource"), findMethod(type, "canAcceptSource", int.class),
                findMethod(type, "canProvideSource"), findMethod(type, "canProvideSource", int.class),
                findMethod(type, "canReceive"), findMethod(type, "canExtract"), receiveSource, extractSource,
                addSourceSimulated, addSource, removeSourceSimulated, removeSource);
    }

    @Override
    public int getCurrentSource() {
        return Math.max(0, invokeInt(handler, getSource, 0));
    }

    @Override
    public int getMaxSource() {
        Method method = getSourceCapacity == null ? getMaxSource : getSourceCapacity;
        return Math.max(0, invokeInt(handler, method, 0));
    }

    @Override
    public boolean canExtract() {
        if (extractSource != null || removeSourceSimulated != null) {
            return simulateExtract(Integer.MAX_VALUE) > 0;
        }
        if (canExtract != null && !invokeBoolean(handler, canExtract, false)) {
            return false;
        }
        if (canProvideSource != null && !invokeBoolean(handler, canProvideSource, false)) {
            return false;
        }
        if (canProvideSourceAmount != null && !invokeBoolean(handler, canProvideSourceAmount, false, 1)) {
            return false;
        }
        return getCurrentSource() > 0;
    }

    @Override
    public boolean canReceive() {
        if (receiveSource != null || addSourceSimulated != null) {
            return simulateInsert(Integer.MAX_VALUE) > 0;
        }
        if (canReceive != null && !invokeBoolean(handler, canReceive, false)) {
            return false;
        }
        if (canAcceptSource != null && !invokeBoolean(handler, canAcceptSource, false)) {
            return false;
        }
        int space = getMaxSource() - getCurrentSource();
        if (space <= 0) {
            return false;
        }
        return canAcceptSourceAmount == null || invokeBoolean(handler, canAcceptSourceAmount, false,
                Math.min(space, Integer.MAX_VALUE));
    }

    @Override
    public int extractSource(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (extractSource != null) {
            return Math.max(0, invokeInt(handler, extractSource, 0, amount, simulate));
        }
        if (removeSourceSimulated != null) {
            return Math.max(0, invokeInt(handler, removeSourceSimulated, 0, amount, simulate));
        }
        int extracted = Math.min(amount, getCurrentSource());
        if (extracted <= 0 || !canExtract()) {
            return 0;
        }
        if (simulate) {
            return extracted;
        }
        int before = getCurrentSource();
        int after = invokeInt(handler, removeSource, before, extracted);
        return Math.min(extracted, Math.max(0, before - after));
    }

    @Override
    public int insertSource(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (receiveSource != null) {
            return Math.max(0, invokeInt(handler, receiveSource, 0, amount, simulate));
        }
        if (addSourceSimulated != null) {
            return Math.max(0, invokeInt(handler, addSourceSimulated, 0, amount, simulate));
        }
        int accepted = Math.min(amount, Math.max(0, getMaxSource() - getCurrentSource()));
        if (accepted <= 0 || !canReceive()) {
            return 0;
        }
        if (simulate) {
            return accepted;
        }
        int before = getCurrentSource();
        int after = invokeInt(handler, addSource, before, accepted);
        return Math.min(accepted, Math.max(0, after - before));
    }

    private int simulateExtract(int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (extractSource != null) {
            return Math.max(0, invokeInt(handler, extractSource, 0, amount, true));
        }
        if (removeSourceSimulated != null) {
            return Math.max(0, invokeInt(handler, removeSourceSimulated, 0, amount, true));
        }
        return Math.min(amount, getCurrentSource());
    }

    private int simulateInsert(int amount) {
        if (amount <= 0) {
            return 0;
        }
        if (receiveSource != null) {
            return Math.max(0, invokeInt(handler, receiveSource, 0, amount, true));
        }
        if (addSourceSimulated != null) {
            return Math.max(0, invokeInt(handler, addSourceSimulated, 0, amount, true));
        }
        return Math.min(amount, Math.max(0, getMaxSource() - getCurrentSource()));
    }

    private static int invokeInt(Object target, Method method, int fallback, Object... args) {
        if (method == null) {
            return fallback;
        }
        try {
            Object result = method.invoke(target, args);
            return result instanceof Number number ? number.intValue() : fallback;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean invokeBoolean(Object target, Method method, boolean fallback, Object... args) {
        if (method == null) {
            return fallback;
        }
        try {
            Object result = method.invoke(target, args);
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
}
