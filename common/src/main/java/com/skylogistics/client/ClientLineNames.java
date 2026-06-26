package com.skylogistics.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientLineNames {
    private static final Map<UUID, Entry> NAMES = new HashMap<>();

    private ClientLineNames() {
    }

    public static void apply(UUID lineId, String assignedName, String displayName) {
        if (lineId == null) {
            return;
        }
        String assigned = sanitize(assignedName, displayName);
        String display = sanitize(displayName, assigned);
        NAMES.put(lineId, new Entry(assigned, display));
    }

    public static String displayName(UUID lineId, String fallback) {
        Entry entry = NAMES.get(lineId);
        return entry == null ? sanitize(fallback, "") : sanitize(entry.displayName(), fallback);
    }

    public static String assignedName(UUID lineId, String fallback) {
        Entry entry = NAMES.get(lineId);
        return entry == null ? sanitize(fallback, "") : sanitize(entry.assignedName(), fallback);
    }

    public static String editedName(UUID lineId, String requestedName, String fallback) {
        return sanitize(requestedName, assignedName(lineId, fallback));
    }

    private static String sanitize(String name, String fallback) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isBlank()) {
            clean = fallback == null ? "" : fallback.trim().replaceAll("\\s+", " ");
        }
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }

    private record Entry(String assignedName, String displayName) {
    }
}
