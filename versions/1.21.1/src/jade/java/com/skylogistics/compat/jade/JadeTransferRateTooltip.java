package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.NetworkEndpointBlockEntity;
import com.skylogistics.compat.astages.TransferResource;
import com.skylogistics.util.SimplePipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ITooltip;

final class JadeTransferRateTooltip {
    private JadeTransferRateTooltip() {}

    static CompoundTag write(NetworkEndpointBlockEntity endpoint, SimplePipeType pipeType) {
        CompoundTag data = new CompoundTag();
        for (TransferResource resource : TransferResource.values()) {
            if (!applies(resource, pipeType)) continue;
            long limit = endpoint.getProgressionTransferLimit(resource);
            if (limit < endpoint.getConfiguredTransferLimit(resource)) data.putLong(resource.configKey(), limit);
        }
        return data;
    }

    static void append(ITooltip tooltip, CompoundTag data) {
        for (TransferResource resource : TransferResource.values()) {
            if (!data.contains(resource.configKey(), Tag.TAG_LONG)) continue;
            tooltip.add(Component.translatable("jade.skylogistics.progression_rate",
                    Component.translatable("jade.skylogistics.resource." + resource.configKey()),
                    Component.literal(Long.toString(data.getLong(resource.configKey()))))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean applies(TransferResource resource, SimplePipeType pipeType) {
        if (pipeType == null) return true;
        return switch (pipeType) {
            case ITEM -> resource == TransferResource.ITEMS;
            case FLUID -> resource == TransferResource.FLUIDS || resource == TransferResource.CHEMICALS
                    || resource == TransferResource.SOULS;
            case ENERGY -> resource == TransferResource.ENERGY || resource == TransferResource.MANA
                    || resource == TransferResource.SOURCE;
        };
    }
}
