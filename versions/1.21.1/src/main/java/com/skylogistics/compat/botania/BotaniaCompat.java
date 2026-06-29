package com.skylogistics.compat.botania;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;

public final class BotaniaCompat {
    private static final String BOTANIA = "botania";
    @SuppressWarnings("rawtypes")
    private static BlockCapability manaReceiverCapability;
    @SuppressWarnings("rawtypes")
    private static BlockCapability sparkAttachableCapability;
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
        Object receiver = getCapability(level, manaReceiverCapability(), pos, side);
        Object sparkAttachable = getCapability(level, sparkAttachableCapability(), pos, side);
        ManaHandlerBridge bridge = ReflectiveManaHandlerBridge.create(receiver, sparkAttachable);
        if (bridge != null) {
            return bridge;
        }
        BlockEntity target = level.getBlockEntity(pos);
        return ReflectiveManaHandlerBridge.create(target, sparkAttachable);
    }

    public static ManaHandlerBridge wrapManaHandler(Object receiver, Object sparkAttachable) {
        if (!isLoaded()) {
            return null;
        }
        return ReflectiveManaHandlerBridge.create(receiver, sparkAttachable);
    }

    @SuppressWarnings("rawtypes")
    private static BlockCapability manaReceiverCapability() {
        resolveCapabilities();
        return manaReceiverCapability;
    }

    @SuppressWarnings("rawtypes")
    private static BlockCapability sparkAttachableCapability() {
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

    @SuppressWarnings("rawtypes")
    private static BlockCapability capabilityField(String name) {
        for (String className : new String[] {
                "vazkii.botania.api.BotaniaCapabilities",
                "vazkii.botania.api.BotaniaForgeCapabilities"
        }) {
            try {
                Object value = Class.forName(className).getField(name).get(null);
                if (value instanceof BlockCapability capability) {
                    return capability;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getCapability(Level level, BlockCapability capability, BlockPos pos, Direction side) {
        return capability == null ? null : level.getCapability(capability, pos, side);
    }
}
