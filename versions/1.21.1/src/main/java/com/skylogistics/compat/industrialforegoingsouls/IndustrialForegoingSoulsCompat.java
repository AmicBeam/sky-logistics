package com.skylogistics.compat.industrialforegoingsouls;

import com.skylogistics.SkyLogistics;
import com.skylogistics.config.SkyLogisticsConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import com.skylogistics.block.entity.SkyMEInterfaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;

public final class IndustrialForegoingSoulsCompat {
    public static final String MOD_ID = "industrialforegoingsouls";
    public static final String SOULPLIED_ENERGISTICS_MOD_ID = "soulplied_energistics";
    private static final String CAPABILITIES_CLASS =
            "com.buuz135.industrialforegoingsouls.capabilities.SoulCapabilities";
    private static final String ACTION_CLASS =
            "com.buuz135.industrialforegoingsouls.capabilities.ISoulHandler$Action";
    private static boolean warned;
    private static Object capability;
    private static Object simulateAction;
    private static Object executeAction;

    private IndustrialForegoingSoulsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isSoulpliedEnergisticsLoaded() {
        return ModList.get().isLoaded(SOULPLIED_ENERGISTICS_MOD_ID);
    }

    public static boolean canTransfer() {
        return isLoaded() && SkyLogisticsConfig.allowFluidSoulTransfer();
    }

    @SuppressWarnings("unchecked")
    public static SoulHandlerBridge soulHandler(Level level, BlockPos pos, Direction side) {
        if (!canTransfer()) return null;
        try {
            Object handler = level.getCapability((BlockCapability<Object, Direction>) capability(), pos, side);
            return wrap(handler);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static SoulHandlerBridge wrap(Object handler) {
        if (!canTransfer() || handler == null) return null;
        try {
            return new ReflectiveSoulHandler(handler);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    public static Object rawCapability() {
        if (!isLoaded()) return null;
        try {
            return capability();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void registerCapabilities(RegisterCapabilitiesEvent event,
            BlockEntityType<SkyMEInterfaceBlockEntity> type) {
        if (!canTransfer()) return;
        try {
            BlockCapability soulCapability = (BlockCapability) capability();
            Class<?> soulHandlerType = Class.forName(
                    "com.buuz135.industrialforegoingsouls.capabilities.ISoulHandler");
            event.registerBlockEntity(soulCapability, type, (host, side) -> {
                SoulHandlerBridge handler = ((SkyMEInterfaceBlockEntity) host).exposedSoulHandler();
                return handler == null ? null : Proxy.newProxyInstance(soulHandlerType.getClassLoader(),
                        new Class<?>[] {soulHandlerType}, (proxy, method, args) -> switch (method.getName()) {
                            case "getSoulTanks" -> handler.getSoulTanks();
                            case "getSoulInTank" -> handler.getSoulInTank((int) args[0]);
                            case "getTankCapacity" -> handler.getTankCapacity((int) args[0]);
                            case "fill" -> handler.fill((int) args[0], isSimulate(args[1]));
                            case "drain" -> handler.drain((int) args[0], isSimulate(args[1]));
                            case "toString" -> "SkyLogisticsSoulHandler[" + host.getBlockPos() + "]";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new UnsupportedOperationException(method.toString());
                        });
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    public static Object action(boolean simulate) throws ReflectiveOperationException {
        if (simulateAction == null || executeAction == null) {
            Class<?> action = Class.forName(ACTION_CLASS);
            for (Object constant : action.getEnumConstants()) {
                if (!(constant instanceof Enum<?> value)) continue;
                if ("SIMULATE".equals(value.name())) simulateAction = value;
                if ("EXECUTE".equals(value.name())) executeAction = value;
            }
            if (simulateAction == null || executeAction == null) {
                throw new NoSuchFieldException(ACTION_CLASS + " SIMULATE/EXECUTE");
            }
        }
        return simulate ? simulateAction : executeAction;
    }

    private static Object capability() throws ReflectiveOperationException {
        if (capability == null) capability = Class.forName(CAPABILITIES_CLASS).getField("BLOCK").get(null);
        return capability;
    }

    private static boolean isSimulate(Object action) {
        return action instanceof Enum<?> value && "SIMULATE".equals(value.name());
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn(
                    "Industrial Foregoing Souls compat is disabled because the loaded API is not compatible.", error);
        }
    }

    private static final class ReflectiveSoulHandler implements SoulHandlerBridge {
        private final Object handler;
        private final Method getSoulTanks;
        private final Method getSoulInTank;
        private final Method getTankCapacity;
        private final Method fill;
        private final Method drain;

        private ReflectiveSoulHandler(Object handler) throws ReflectiveOperationException {
            this.handler = handler;
            Class<?> type = handler.getClass();
            Class<?> action = Class.forName(ACTION_CLASS);
            getSoulTanks = type.getMethod("getSoulTanks");
            getSoulInTank = type.getMethod("getSoulInTank", int.class);
            getTankCapacity = type.getMethod("getTankCapacity", int.class);
            fill = type.getMethod("fill", int.class, action);
            drain = type.getMethod("drain", int.class, action);
        }

        @Override public int getSoulTanks() { return invoke(getSoulTanks, 0); }
        @Override public int getSoulInTank(int tank) { return invoke(getSoulInTank, 0, tank); }
        @Override public int getTankCapacity(int tank) { return invoke(getTankCapacity, 0, tank); }
        @Override public int fill(int amount, boolean simulate) { return move(fill, amount, simulate); }
        @Override public int drain(int amount, boolean simulate) { return move(drain, amount, simulate); }

        private int move(Method method, int amount, boolean simulate) {
            if (amount <= 0) return 0;
            try {
                return Math.max(0, invoke(method, 0, amount, action(simulate)));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return 0;
            }
        }

        private int invoke(Method method, int fallback, Object... args) {
            try {
                Object result = method.invoke(handler, args);
                return result instanceof Number number ? number.intValue() : fallback;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn(error);
                return fallback;
            }
        }
    }
}
