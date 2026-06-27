package com.skylogistics.compat.jade;

import com.skylogistics.SkyLogistics;
import com.skylogistics.util.AmountFormatter;
import net.minecraft.resources.ResourceLocation;

abstract class BaseSkyLogisticsJadeProvider {
    private final ResourceLocation uid;

    protected BaseSkyLogisticsJadeProvider(String path) {
        uid = ResourceLocation.fromNamespaceAndPath(SkyLogistics.MOD_ID, path);
    }

    public final ResourceLocation getUid() {
        return uid;
    }

    public int getDefaultPriority() {
        return 5000;
    }

    protected static String compact(long value) {
        return AmountFormatter.compact(value);
    }
}
