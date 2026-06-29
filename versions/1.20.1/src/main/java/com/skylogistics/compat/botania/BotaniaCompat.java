package com.skylogistics.compat.botania;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

public final class BotaniaCompat {
    private static final String BOTANIA = "botania";
    private static Capability<?> manaReceiverCapability;
    private static Capability<?> sparkAttachableCapability;
    private static boolean capabilitiesResolved;

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
        Object receiver = getCapability(target, manaReceiverCapability(), side);
        if (receiver == null) {
            return null;
        }
        return ReflectiveManaHandlerBridge.create(receiver, getCapability(target, sparkAttachableCapability(), side));
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
}
