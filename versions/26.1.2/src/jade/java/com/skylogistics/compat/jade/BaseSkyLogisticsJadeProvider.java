package com.skylogistics.compat.jade;

import com.skylogistics.SkyLogistics;
import com.skylogistics.util.AmountFormatter;
import net.minecraft.resources.Identifier;

abstract class BaseSkyLogisticsJadeProvider {
    private final Identifier uid;

    protected BaseSkyLogisticsJadeProvider(String path) {
        uid = Identifier.fromNamespaceAndPath(SkyLogistics.MOD_ID, path);
    }

    public final Identifier getUid() {
        return uid;
    }

    public int getDefaultPriority() {
        return 5000;
    }

    protected static String compact(long value) {
        return AmountFormatter.compact(value);
    }
}
