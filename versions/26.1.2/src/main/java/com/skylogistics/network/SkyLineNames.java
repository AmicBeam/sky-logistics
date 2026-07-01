package com.skylogistics.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class SkyLineNames extends SavedData {
    private static final String DATA_NAME = "skylogistics_line_names";
    private static final SavedDataType<SkyLineNames> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(DATA_NAME),
            SkyLineNames::new,
            CompoundTag.CODEC.xmap(
                    tag -> load(tag, com.skylogistics.util.StackData.builtinRegistries()),
                    data -> data.save(new CompoundTag(), com.skylogistics.util.StackData.builtinRegistries())),
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private static final String LINES = "Lines";
    private static final String ID = "Id";
    private static final String ASSIGNED_NAME = "AssignedName";
    private static final String DISPLAY_NAME = "DisplayName";
    private static final int MAX_LINE_NAME_LENGTH = 48;

    private final Map<UUID, Entry> lines = new HashMap<>();

    public static SkyLineNames get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static Entry ensure(MinecraftServer server, UUID lineId, String assignedFallback) {
        return get(server).ensure(lineId, assignedFallback);
    }

    public static Entry ensure(MinecraftServer server, UUID lineId, String assignedFallback, String displayFallback) {
        return get(server).ensure(lineId, assignedFallback, displayFallback);
    }

    public static Entry rename(MinecraftServer server, UUID lineId, String requestedName, String assignedFallback) {
        return get(server).rename(lineId, requestedName, assignedFallback);
    }

    public static String displayName(MinecraftServer server, UUID lineId, String fallback) {
        return get(server).displayName(lineId, fallback);
    }

    public static String displayName(MinecraftServer server, UUID lineId, String assignedFallback,
            String displayFallback) {
        return get(server).displayName(lineId, assignedFallback, displayFallback);
    }

    public static String assignedName(MinecraftServer server, UUID lineId, String fallback) {
        return get(server).assignedName(lineId, fallback);
    }

    public Entry ensure(UUID lineId, String assignedFallback) {
        return ensure(lineId, assignedFallback, assignedFallback);
    }

    public Entry ensure(UUID lineId, String assignedFallback, String displayFallback) {
        if (lineId == null) {
            String assigned = validLineName(assignedFallback, "");
            return new Entry(assigned, validLineName(displayFallback, assigned));
        }
        String fallback = validLineName(assignedFallback, "Line-0");
        Entry entry = lines.get(lineId);
        if (entry == null) {
            entry = new Entry(fallback, validLineName(displayFallback, fallback));
            lines.put(lineId, entry);
            setDirty();
        } else if (entry.assignedName().isBlank()) {
            entry = new Entry(fallback, validLineName(entry.displayName(), fallback));
            lines.put(lineId, entry);
            setDirty();
        }
        return entry;
    }

    public Entry rename(UUID lineId, String requestedName, String assignedFallback) {
        Entry entry = ensure(lineId, assignedFallback);
        String displayName = validLineName(requestedName, entry.assignedName());
        if (!entry.displayName().equals(displayName)) {
            entry = new Entry(entry.assignedName(), displayName);
            lines.put(lineId, entry);
            setDirty();
        }
        return entry;
    }

    public String displayName(UUID lineId, String fallback) {
        Entry entry = ensure(lineId, fallback);
        return entry.displayName();
    }

    public String displayName(UUID lineId, String assignedFallback, String displayFallback) {
        Entry entry = ensure(lineId, assignedFallback, displayFallback);
        return entry.displayName();
    }

    public String assignedName(UUID lineId, String fallback) {
        Entry entry = ensure(lineId, fallback);
        return entry.assignedName();
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> line : lines.entrySet()) {
            CompoundTag entry = new CompoundTag();
            com.skylogistics.util.NbtCompat.putUuid(entry, ID, line.getKey());
            entry.putString(ASSIGNED_NAME, line.getValue().assignedName());
            entry.putString(DISPLAY_NAME, line.getValue().displayName());
            list.add(entry);
        }
        tag.put(LINES, list);
        return tag;
    }

    private static SkyLineNames load(CompoundTag tag, HolderLookup.Provider registries) {
        SkyLineNames data = new SkyLineNames();
        if (!tag.contains(LINES)) {
            return data;
        }
        ListTag list = tag.getListOrEmpty(LINES);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            if (!com.skylogistics.util.NbtCompat.hasUuid(entry, ID)) {
                continue;
            }
            String assigned = entry.contains(ASSIGNED_NAME)
                    ? entry.getStringOr(ASSIGNED_NAME, "")
                    : "Line-0";
            assigned = validLineName(assigned, "Line-0");
            String display = entry.contains(DISPLAY_NAME)
                    ? entry.getStringOr(DISPLAY_NAME, "")
                    : assigned;
            data.lines.put(com.skylogistics.util.NbtCompat.getUuid(entry, ID), new Entry(assigned, validLineName(display, assigned)));
        }
        return data;
    }

    public static String validLineName(String name, String fallback) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isBlank()) {
            clean = fallback == null ? "" : fallback.trim().replaceAll("\\s+", " ");
        }
        return clean.length() > MAX_LINE_NAME_LENGTH ? clean.substring(0, MAX_LINE_NAME_LENGTH) : clean;
    }

    public record Entry(String assignedName, String displayName) {
    }
}
