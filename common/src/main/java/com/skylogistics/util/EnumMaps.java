package com.skylogistics.util;

import java.util.EnumMap;
import java.util.Map;

public final class EnumMaps {
    private EnumMaps() {
    }

    public static <K extends Enum<K>, V> EnumMap<K, V> copyOf(Class<K> keyType, Map<K, V> source) {
        EnumMap<K, V> copy = new EnumMap<>(keyType);
        copy.putAll(source);
        return copy;
    }
}
