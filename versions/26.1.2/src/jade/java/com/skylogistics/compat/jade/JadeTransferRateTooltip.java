package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.NetworkEndpointBlockEntity;
import com.skylogistics.compat.astages.TransferResource;
import com.skylogistics.util.AmountFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ITooltip;

final class JadeTransferRateTooltip {
    private static final String RESOURCE = "Resource";
    private static final String RATE = "Rate";

    private JadeTransferRateTooltip() {}

    static CompoundTag write(NetworkEndpointBlockEntity endpoint, net.minecraft.core.Direction direction) {
        CompoundTag data = new CompoundTag();
        TransferResource resource = endpoint.firstEnabledTransferResource(direction);
        if (resource == null) return data;
        long rate = endpoint.getProgressionTransferLimit(resource);
        if (rate >= endpoint.getConfiguredTransferLimit(resource)) return data;
        data.putString(RESOURCE, resource.configKey());
        data.putLong(RATE, rate);
        return data;
    }

    static void append(ITooltip tooltip, CompoundTag data) {
        String key = data.getStringOr(RESOURCE, "");
        if (key.isEmpty()) return;
        TransferResource resource = null;
        for (TransferResource candidate : TransferResource.values()) {
            if (candidate.configKey().equals(key)) {
                resource = candidate;
                break;
            }
        }
        if (resource == null) return;
        long rate = data.getLongOr(RATE, Long.MAX_VALUE);
        tooltip.add(Component.translatable("jade.skylogistics.progression_rate",
                Component.translatable("jade.skylogistics.resource." + key),
                Component.literal(AmountFormatter.compact(rate) + unit(resource))));
    }

    private static String unit(TransferResource resource) {
        return switch (resource) {
            case ITEMS, CHEMICALS, SOULS -> "/t";
            case FLUIDS -> " mB/t";
            case ENERGY -> " FE/t";
            case MANA -> " mana/t";
            case SOURCE -> " Source/t";
        };
    }
}
