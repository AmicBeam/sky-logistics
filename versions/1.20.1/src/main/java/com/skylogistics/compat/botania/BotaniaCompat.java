package com.skylogistics.compat.botania;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

public final class BotaniaCompat {
    private static final String BOTANIA = "botania";
    private static Capability<?> manaReceiverCapability;
    private static Capability<?> sparkAttachableCapability;
    private static boolean capabilitiesResolved;
    private static Method findCapability;
    private static boolean findCapabilityResolved;

    private BotaniaCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(BOTANIA);
    }

    public static ManaHandlerBridge manaHandler(Level level, BlockPos pos, Direction side) {
        if (!isLoaded() || level == null || pos == null) {
            return null;
        }
        BlockEntity target = level.getBlockEntity(pos);
        if (target == null) {
            return null;
        }
        Object receiver = findCapability(level, pos, target, manaReceiverCapability(), side);
        if (receiver == null) {
            return null;
        }
        Object sparkAttachable = findCapability(level, pos, target, sparkAttachableCapability(), side);
        return ReflectiveManaHandlerBridge.create(receiver, sparkAttachable);
    }

    public static ManaHandlerBridge wrapManaHandler(Object receiver, Object sparkAttachable) {
        if (!isLoaded()) {
            return null;
        }
        return ReflectiveManaHandlerBridge.create(receiver, sparkAttachable);
    }

    private static Capability<?> manaReceiverCapability() {
        resolveCapabilities();
        return manaReceiverCapability;
    }

    private static Capability<?> sparkAttachableCapability() {
        resolveCapabilities();
        return sparkAttachableCapability;
    }

    private static void resolveCapabilities() {
        if (capabilitiesResolved) {
            return;
        }
        capabilitiesResolved = true;
        manaReceiverCapability = capabilityField("MANA_RECEIVER");
        sparkAttachableCapability = capabilityField("SPARK_ATTACHABLE");
    }

    private static Capability<?> capabilityField(String name) {
        try {
            Object value = Class.forName("vazkii.botania.api.BotaniaForgeCapabilities").getField(name).get(null);
            return value instanceof Capability<?> capability ? capability : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getCapability(BlockEntity target, Capability<?> capability, Direction side) {
        if (capability == null) {
            return null;
        }
        LazyOptional<?> optional = target.getCapability((Capability) capability, side);
        return optional.orElse(null);
    }

    private static Object findCapability(Level level, BlockPos pos, BlockEntity target,
            Capability<?> capability, Direction side) {
        Object value = getCapability(target, capability, side);
        if (value == null && side != null) {
            value = getCapability(target, capability, null);
        }
        if (value != null || capability == null) {
            return value;
        }
        Method finder = findCapabilityMethod();
        if (finder == null) {
            return null;
        }
        try {
            BlockState state = level.getBlockState(pos);
            return finder.invoke(null, capability, level, pos, state, target, side);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findCapabilityMethod() {
        if (findCapabilityResolved) {
            return findCapability;
        }
        findCapabilityResolved = true;
        try {
            findCapability = Class.forName("vazkii.botania.forge.CapabilityUtil").getMethod(
                    "findCapability", Capability.class, Level.class, BlockPos.class, BlockState.class,
                    BlockEntity.class, Direction.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            findCapability = null;
        }
        return findCapability;
    }
}
