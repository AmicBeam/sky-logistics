package com.skylogistics.compat.jade;

import com.skylogistics.SkyLogistics;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.IJadeProvider;
import snownee.jade.api.ITooltip;

abstract class BaseSkyLogisticsJadeProvider implements IJadeProvider {
    private final ResourceLocation uid;

    protected BaseSkyLogisticsJadeProvider(String path) {
        uid = new ResourceLocation(SkyLogistics.MOD_ID, path);
    }

    @Override
    public final ResourceLocation getUid() {
        return uid;
    }

    @Override
    public int getDefaultPriority() {
        return 5000;
    }

    protected static void appendDisplayedItem(ITooltip tooltip, ItemStack stack) {
        if (!stack.isEmpty()) {
            tooltip.append(Component.translatable("jade.skylogistics.display_item",
                    stack.getHoverName(), stack.getCount()));
        }
    }

    protected static String shortLine(UUID lineId) {
        String raw = lineId.toString().replace("-", "").toUpperCase(Locale.ROOT);
        return raw.substring(0, 4) + "-" + raw.substring(4, 6);
    }

    protected static String compact(long value) {
        if (value >= 1_000_000_000L) {
            return (value / 1_000_000_000L) + "B";
        }
        if (value >= 1_000_000L) {
            return (value / 1_000_000L) + "M";
        }
        if (value >= 1_000L) {
            return (value / 1_000L) + "K";
        }
        return Long.toString(value);
    }
}
