package com.skylogistics.compat.beyonddimensions;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.EmptyExternalHandlers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class BeyondDimensionsCompat {
    private static final String BEYOND_DIMENSIONS = "beyonddimensions";
    private static boolean warned;

    private BeyondDimensionsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(BEYOND_DIMENSIONS);
    }

    public static IItemHandler createItemHandler(BlockEntity host) {
        return isLoaded() ? new ItemHandler(host) : EmptyExternalHandlers.Items.INSTANCE;
    }

    public static IFluidHandler createFluidHandler(BlockEntity host) {
        return isLoaded() ? new FluidHandler(host) : EmptyExternalHandlers.Fluids.INSTANCE;
    }

    public static void bindOnPlaced(Level level, BlockPos pos, LivingEntity placer) {
        if (!isLoaded() || level.isClientSide || !(placer instanceof Player player)) {
            return;
        }
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof NetworkBoundHost host) || host.getDimensionNetworkId() >= 0) {
                return;
            }
            Object net = netFromPlayer(player);
            if (net != null && Boolean.TRUE.equals(Reflect.invoke(net, "isManager", player))) {
                int netId = ((Number) Reflect.invoke(net, "getId")).intValue();
                host.setDimensionNetworkId(netId);
                player.sendSystemMessage(Component.translatable("msg.beyonddimensions.block_net_bound", netId));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
        }
    }

    public static boolean handleBindingUse(Level level, BlockPos pos, Player player) {
        if (!isLoaded() || !player.getMainHandItem().isEmpty() || !player.isShiftKeyDown()) {
            return false;
        }
        if (level.isClientSide) {
            return true;
        }
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof NetworkBoundHost host)) {
                return true;
            }
            int currentNetId = host.getDimensionNetworkId();
            if (currentNetId < 0) {
                bindFromPlayerNetwork(level, player, host);
            } else {
                unbindFromPlayerNetwork(level, player, host, currentNetId);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
        }
        return true;
    }

    private static void bindFromPlayerNetwork(Level level, Player player, NetworkBoundHost host)
            throws ReflectiveOperationException {
        Object net = netFromPlayer(player);
        if (net == null) {
            return;
        }
        if (Boolean.TRUE.equals(Reflect.invoke(net, "isManager", player))) {
            int netId = ((Number) Reflect.invoke(net, "getId")).intValue();
            host.setDimensionNetworkId(netId);
            playClick(level, player);
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.block_net_bound", netId));
        } else {
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.no_right_to_bound_block"));
        }
    }

    private static void unbindFromPlayerNetwork(Level level, Player player, NetworkBoundHost host, int currentNetId)
            throws ReflectiveOperationException {
        Object net = netFromPlayer(player);
        if (net == null) {
            return;
        }
        Object currentNet = netFromId(currentNetId);
        int playerNetId = ((Number) Reflect.invoke(net, "getId")).intValue();
        if (playerNetId != currentNetId && currentNet != null) {
            return;
        }
        if (Boolean.TRUE.equals(Reflect.invoke(net, "isManager", player))) {
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.block_net_unbound", currentNetId));
            host.clearDimensionNetworkId();
            playClick(level, player);
        } else {
            player.sendSystemMessage(Component.translatable("msg.beyonddimensions.no_right_to_bound_block"));
        }
    }

    private static void playClick(Level level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(),
                SoundSource.PLAYERS, 0.5F, 1.0F);
    }

    private static Object storage(BlockEntity host) {
        if (!(host instanceof NetworkBoundHost networkHost)) {
            return null;
        }
        int netId = networkHost.getDimensionNetworkId();
        if (netId < 0) {
            return null;
        }
        try {
            Object net = netFromId(netId);
            return net == null || deleted(net) ? null : Reflect.invoke(net, "getUnifiedStorage");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static Object netFromPlayer(Player player) throws ReflectiveOperationException {
        return Reflect.invokeStatic(dimensionsNetClass(), "getNetFromPlayer", player);
    }

    private static Object netFromId(int netId) throws ReflectiveOperationException {
        return Reflect.invokeStatic(dimensionsNetClass(), "getNetFromId", netId);
    }

    private static boolean deleted(Object net) throws ReflectiveOperationException {
        Field field = net.getClass().getField("deleted");
        return field.getBoolean(net);
    }

    private static Class<?> dimensionsNetClass() throws ClassNotFoundException {
        return Class.forName("com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet");
    }

    private static IItemHandler itemHandler(BlockEntity host) {
        Object storage = storage(host);
        if (storage == null) {
            return EmptyExternalHandlers.Items.INSTANCE;
        }
        try {
            Class<?> type = Class.forName(
                    "com.wintercogs.beyonddimensions.api.capability.helper.unordered.ItemUnifiedStorageHandler");
            Constructor<?> constructor = type.getConstructor(Class.forName(
                    "com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage"));
            Object handler = constructor.newInstance(storage);
            return handler instanceof IItemHandler itemHandler ? itemHandler : EmptyExternalHandlers.Items.INSTANCE;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Items.INSTANCE;
        }
    }

    private static IFluidHandler fluidHandler(BlockEntity host) {
        Object storage = storage(host);
        if (storage == null) {
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
        try {
            Class<?> type = Class.forName(
                    "com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler");
            Constructor<?> constructor = type.getConstructor(Class.forName(
                    "com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage"));
            Object handler = constructor.newInstance(storage);
            return handler instanceof IFluidHandler fluidHandler ? fluidHandler : EmptyExternalHandlers.Fluids.INSTANCE;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return EmptyExternalHandlers.Fluids.INSTANCE;
        }
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            SkyLogistics.LOGGER.warn(
                    "Beyond Dimensions compat is disabled because the loaded Beyond Dimensions API is not compatible.",
                    error);
        }
    }

    public interface NetworkBoundHost {
        int getDimensionNetworkId();

        void setDimensionNetworkId(int netId);

        void clearDimensionNetworkId();
    }

    private record ItemHandler(BlockEntity host) implements IItemHandler {
        @Override
        public int getSlots() {
            return itemHandler(host).getSlots();
        }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            return itemHandler(host).getStackInSlot(slot);
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack,
                boolean simulate) {
            return itemHandler(host).insertItem(slot, stack, simulate);
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return itemHandler(host).extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return itemHandler(host).getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return itemHandler(host).isItemValid(slot, stack);
        }
    }

    private record FluidHandler(BlockEntity host) implements IFluidHandler {
        @Override
        public int getTanks() {
            return fluidHandler(host).getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fluidHandler(host).getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return fluidHandler(host).getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return fluidHandler(host).isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fluidHandler(host).fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return fluidHandler(host).drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return fluidHandler(host).drain(maxDrain, action);
        }
    }

    private static final class Reflect {
        private Reflect() {
        }

        static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
            Method method = method(target.getClass(), name, args);
            return method.invoke(target, args);
        }

        static Object invokeStatic(Class<?> owner, String name, Object... args) throws ReflectiveOperationException {
            Method method = method(owner, name, args);
            return method.invoke(null, args);
        }

        private static Method method(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
            Method method = findMethod(type, name, args);
            if (method == null) {
                throw new NoSuchMethodException(type.getName() + "#" + name + "/" + args.length);
            }
            return method;
        }

        private static Method findMethod(Class<?> type, String name, Object[] args) {
            Class<?> current = type;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (matches(method, name, args)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            for (Class<?> iface : type.getInterfaces()) {
                Method method = findMethod(iface, name, args);
                if (method != null) {
                    return method;
                }
            }
            return null;
        }

        private static boolean matches(Method method, String name, Object[] args) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                return false;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!isAssignable(parameterTypes[i], args[i])) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isAssignable(Class<?> parameterType, Object arg) {
            if (arg == null) {
                return !parameterType.isPrimitive();
            }
            Class<?> boxed = parameterType.isPrimitive() ? boxed(parameterType) : parameterType;
            return boxed.isInstance(arg);
        }

        private static Class<?> boxed(Class<?> primitive) {
            if (primitive == boolean.class) {
                return Boolean.class;
            }
            if (primitive == byte.class) {
                return Byte.class;
            }
            if (primitive == short.class) {
                return Short.class;
            }
            if (primitive == int.class) {
                return Integer.class;
            }
            if (primitive == long.class) {
                return Long.class;
            }
            if (primitive == float.class) {
                return Float.class;
            }
            if (primitive == double.class) {
                return Double.class;
            }
            if (primitive == char.class) {
                return Character.class;
            }
            return primitive;
        }
    }
}
