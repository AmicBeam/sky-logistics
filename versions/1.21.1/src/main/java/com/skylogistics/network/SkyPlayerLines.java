package com.skylogistics.network;

import com.skylogistics.item.ConfiguratorItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

public final class SkyPlayerLines extends SavedData {
    private static final String DATA_NAME = "skylogistics_player_lines";
    private static final String PLAYERS = "Players";
    private static final String PLAYER_ID = "PlayerId";
    private static final String LINES = "Lines";
    private static final String LINE_ID = "Id";
    private static final String ASSIGNED_NAME = "AssignedName";

    private final Map<UUID, PlayerLines> players = new HashMap<>();

    public static SkyPlayerLines get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SkyPlayerLines::new, SkyPlayerLines::load, null), DATA_NAME);
    }

    public static LineSelection selection(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        if (server == null || player == null) {
            return fallbackSelection(currentLineId, assignedFallback, displayFallback);
        }
        return get(server).selectCurrent(server, player, currentLineId, assignedFallback, displayFallback);
    }

    public static LineSelection selectFirst(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        return get(server).select(server, player, currentLineId, assignedFallback, displayFallback, SelectAction.FIRST);
    }

    public static LineSelection selectPrevious(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        return get(server).select(server, player, currentLineId, assignedFallback, displayFallback,
                SelectAction.PREVIOUS);
    }

    public static LineSelection selectNextOrCreate(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        return get(server).select(server, player, currentLineId, assignedFallback, displayFallback,
                SelectAction.NEXT_OR_CREATE);
    }

    public static LineSelection selectLast(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        return get(server).select(server, player, currentLineId, assignedFallback, displayFallback, SelectAction.LAST);
    }

    public static LineSelection removeCurrent(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        return get(server).removeSelected(server, player, currentLineId, assignedFallback, displayFallback);
    }

    private LineSelection selectCurrent(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        PlayerLines playerLines = playerLines(player);
        int index = ensureLine(server, playerLines, player, currentLineId, assignedFallback, displayFallback);
        return selectionAt(server, playerLines, index, displayFallback);
    }

    private LineSelection select(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback, SelectAction action) {
        PlayerLines playerLines = playerLines(player);
        int index = ensureLine(server, playerLines, player, currentLineId, assignedFallback, displayFallback);
        switch (action) {
            case FIRST -> index = 0;
            case PREVIOUS -> index = Math.max(0, index - 1);
            case NEXT_OR_CREATE -> {
                if (index < playerLines.lines.size() - 1) {
                    index++;
                } else {
                    LineEntry line = createLine(player, playerLines.lines);
                    playerLines.lines.add(line);
                    index = playerLines.lines.size() - 1;
                    setDirty();
                }
            }
            case LAST -> index = playerLines.lines.size() - 1;
        }
        return selectionAt(server, playerLines, index, displayFallback);
    }

    private LineSelection removeSelected(MinecraftServer server, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        PlayerLines playerLines = playerLines(player);
        int index = ensureLine(server, playerLines, player, currentLineId, assignedFallback, displayFallback);
        if (playerLines.lines.size() <= 1) {
            return selectionAt(server, playerLines, 0, displayFallback);
        }
        playerLines.lines.remove(index);
        setDirty();
        return selectionAt(server, playerLines, Math.min(index, playerLines.lines.size() - 1), displayFallback);
    }

    private PlayerLines playerLines(Player player) {
        PlayerLines lines = players.computeIfAbsent(player.getUUID(), ignored -> new PlayerLines());
        if (lines.lines.isEmpty()) {
            lines.lines.add(createLine(player, List.of()));
            setDirty();
        }
        return lines;
    }

    private int ensureLine(MinecraftServer server, PlayerLines playerLines, Player player, UUID currentLineId,
            String assignedFallback, String displayFallback) {
        if (playerLines.lines.isEmpty()) {
            playerLines.lines.add(createLine(player, List.of()));
            setDirty();
        }
        UUID lineId = currentLineId;
        String assignedName = validLineName(assignedFallback, displayFallback);
        if (lineId == null) {
            LineEntry first = playerLines.lines.get(0);
            SkyLineNames.ensure(server, first.lineId(), first.assignedName(), first.assignedName());
            return 0;
        }
        int index = indexOfLine(playerLines.lines, lineId);
        if (index < 0) {
            if (assignedName.isBlank()) {
                assignedName = nextLineName(player, playerLines.lines);
            }
            playerLines.lines.add(new LineEntry(lineId, assignedName));
            index = playerLines.lines.size() - 1;
            setDirty();
        }
        LineEntry line = playerLines.lines.get(index);
        SkyLineNames.ensure(server, line.lineId(), line.assignedName(), displayFallback);
        return index;
    }

    private LineSelection selectionAt(MinecraftServer server, PlayerLines playerLines, int index,
            String displayFallback) {
        int clamped = Math.max(0, Math.min(index, playerLines.lines.size() - 1));
        LineEntry line = playerLines.lines.get(clamped);
        SkyLineNames.Entry name = SkyLineNames.ensure(server, line.lineId(), line.assignedName(), displayFallback);
        return new LineSelection(line.lineId(), name.assignedName(), name.displayName(), clamped,
                playerLines.lines.size());
    }

    private static LineSelection fallbackSelection(UUID currentLineId, String assignedFallback, String displayFallback) {
        String assignedName = validLineName(assignedFallback, displayFallback);
        UUID lineId = currentLineId == null ? ConfiguratorItem.lineIdForName(assignedName) : currentLineId;
        String displayName = validLineName(displayFallback, assignedName);
        return new LineSelection(lineId, assignedName, displayName, 0, 1);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerTags = new ListTag();
        for (Map.Entry<UUID, PlayerLines> playerEntry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(PLAYER_ID, playerEntry.getKey());
            ListTag lineTags = new ListTag();
            for (LineEntry line : playerEntry.getValue().lines) {
                CompoundTag lineTag = new CompoundTag();
                lineTag.putUUID(LINE_ID, line.lineId());
                lineTag.putString(ASSIGNED_NAME, line.assignedName());
                lineTags.add(lineTag);
            }
            playerTag.put(LINES, lineTags);
            playerTags.add(playerTag);
        }
        tag.put(PLAYERS, playerTags);
        return tag;
    }

    private static SkyPlayerLines load(CompoundTag tag, HolderLookup.Provider registries) {
        SkyPlayerLines data = new SkyPlayerLines();
        if (!tag.contains(PLAYERS, Tag.TAG_LIST)) {
            return data;
        }
        ListTag playerTags = tag.getList(PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < playerTags.size(); i++) {
            CompoundTag playerTag = playerTags.getCompound(i);
            if (!playerTag.hasUUID(PLAYER_ID) || !playerTag.contains(LINES, Tag.TAG_LIST)) {
                continue;
            }
            PlayerLines playerLines = new PlayerLines();
            ListTag lineTags = playerTag.getList(LINES, Tag.TAG_COMPOUND);
            for (int lineIndex = 0; lineIndex < lineTags.size(); lineIndex++) {
                CompoundTag lineTag = lineTags.getCompound(lineIndex);
                if (!lineTag.hasUUID(LINE_ID)) {
                    continue;
                }
                String assignedName = lineTag.contains(ASSIGNED_NAME, Tag.TAG_STRING)
                        ? lineTag.getString(ASSIGNED_NAME)
                        : ConfiguratorItem.lineName("Line", lineIndex);
                playerLines.lines.add(new LineEntry(lineTag.getUUID(LINE_ID),
                        validLineName(assignedName, ConfiguratorItem.lineName("Line", lineIndex))));
            }
            if (!playerLines.lines.isEmpty()) {
                data.players.put(playerTag.getUUID(PLAYER_ID), playerLines);
            }
        }
        return data;
    }

    private static LineEntry createLine(Player player, List<LineEntry> existing) {
        String assignedName = nextLineName(player, existing);
        return new LineEntry(ConfiguratorItem.lineIdForName(assignedName), assignedName);
    }

    private static String nextLineName(Player player, List<LineEntry> existing) {
        String prefix = ConfiguratorItem.linePrefix(player);
        String marker = prefix + "-";
        int next = 0;
        for (LineEntry line : existing) {
            String assignedName = line.assignedName();
            if (!assignedName.startsWith(marker)) {
                continue;
            }
            try {
                next = Math.max(next, Integer.parseInt(assignedName.substring(marker.length())) + 1);
            } catch (NumberFormatException ignored) {
                next++;
            }
        }
        return ConfiguratorItem.lineName(prefix, next);
    }

    private static int indexOfLine(List<LineEntry> lines, UUID lineId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).lineId().equals(lineId)) {
                return i;
            }
        }
        return -1;
    }

    private static String validLineName(String name, String fallback) {
        return SkyLineNames.validLineName(name,
                SkyLineNames.validLineName(fallback, ConfiguratorItem.lineName("Line", 0)));
    }

    private enum SelectAction {
        FIRST,
        PREVIOUS,
        NEXT_OR_CREATE,
        LAST
    }

    public record LineSelection(UUID lineId, String assignedName, String displayName, int index, int count) {
    }

    private record LineEntry(UUID lineId, String assignedName) {
    }

    private static final class PlayerLines {
        private final List<LineEntry> lines = new ArrayList<>();
    }
}
