package com.skylogistics.compat.jade;

import com.skylogistics.SkyLogistics;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IJadeProvider;

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
