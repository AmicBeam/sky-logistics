package com.skylogistics.compat.jade;

import com.skylogistics.SkyLogistics;
import com.skylogistics.util.AmountFormatter;
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
        return AmountFormatter.compact(value);
    }
}
