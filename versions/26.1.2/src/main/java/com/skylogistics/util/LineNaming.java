package com.skylogistics.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class LineNaming {
    public static final String DEFAULT_PREFIX = "Line";
    private static final int MAX_PREFIX_LENGTH = 24;
    private static final int MAX_NAME_LENGTH = 48;

    private LineNaming() {
    }

    public static String cleanPrefix(String prefix) {
        String clean = prefix == null ? "" : prefix.trim();
        if (clean.isEmpty()) {
            clean = DEFAULT_PREFIX;
        }
        clean = clean.replaceAll("\\s+", "_");
        return clean.length() > MAX_PREFIX_LENGTH ? clean.substring(0, MAX_PREFIX_LENGTH) : clean;
    }

    public static String indexedName(String prefix, int index) {
        return cleanPrefix(prefix) + "-" + Math.max(0, index);
    }

    public static String validName(String name, String fallback) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isBlank()) {
            clean = fallback == null ? "" : fallback.trim().replaceAll("\\s+", " ");
        }
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
    }

    public static UUID idForName(String lineName) {
        String normalized = validName(lineName, indexedName(DEFAULT_PREFIX, 0));
        return UUID.nameUUIDFromBytes(("skylogistics:line:" + normalized).getBytes(StandardCharsets.UTF_8));
    }
}
