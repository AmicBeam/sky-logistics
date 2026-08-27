package com.skylogistics.compat.advancements;

import com.skylogistics.compat.astages.TransferRates;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes the config-owned advancement chain as a world datapack. */
public final class AdvancementDataPackGenerator {
    public static final String PACK_DIRECTORY = "skylogistics_progression";
    public static final String PACK_ID = "file/" + PACK_DIRECTORY;

    private AdvancementDataPackGenerator() {
    }

    public static void generate(Path datapacksDirectory, int packFormat, boolean legacyIconFormat,
            List<AdvancementDisplayEntry> entries) throws IOException {
        Path pack = datapacksDirectory.resolve(PACK_DIRECTORY);
        Path nodes = pack.resolve("data/skylogistics/")
                .resolve(legacyIconFormat ? "advancements" : "advancement")
                .resolve("transfer_rates");
        Files.createDirectories(nodes);
        try (var files = Files.list(nodes)) {
            for (Path file : files.filter(path -> path.getFileName().toString().startsWith("entry_")
                    && path.getFileName().toString().endsWith(".json")).toList()) {
                Files.deleteIfExists(file);
            }
        }
        String metadata = "{\"pack\":{\"pack_format\":" + packFormat
                + ",\"description\":\"Sky Logistics configured progression\"}}";
        Files.writeString(pack.resolve("pack.mcmeta"), metadata, StandardCharsets.UTF_8);
        for (int index = 0; index < entries.size(); index++) {
            AdvancementDisplayEntry entry = entries.get(index);
            String parent = index == 0 ? "skylogistics:transfer_rates/root"
                    : "skylogistics:transfer_rates/entry_" + (index - 1);
            Files.writeString(nodes.resolve("entry_" + index + ".json"),
                    nodeJson(parent, entry, legacyIconFormat), StandardCharsets.UTF_8);
        }
    }

    private static String nodeJson(String parent, AdvancementDisplayEntry entry, boolean legacyIconFormat) {
        String icon = legacyIconFormat
                ? "{\"item\":\"" + escape(entry.icon()) + "\"}"
                : "{\"id\":\"" + escape(entry.icon()) + "\",\"count\":1}";
        String frame = switch (entry.frame().toLowerCase(java.util.Locale.ROOT)) {
            case "goal", "challenge" -> entry.frame().toLowerCase(java.util.Locale.ROOT);
            default -> "task";
        };
        TransferRates rates = entry.rates();
        String description = "{\"translate\":\"advancements.skylogistics.transfer_rates.dynamic.description\","
                + "\"with\":[\"" + display(rates.items(), "/t") + "\",\""
                + display(rates.fluids(), " mB/t") + "\",\""
                + display(rates.energy(), " FE/t") + "\"]}";
        return "{\"parent\":\"" + parent + "\",\"display\":{\"icon\":" + icon
                + ",\"title\":{\"translate\":\"" + escape(entry.title()) + "\"},\"description\":"
                + description + ",\"frame\":\"" + frame
                + "\",\"show_toast\":false,\"announce_to_chat\":false,\"hidden\":false},"
                + "\"criteria\":{\"unlocked\":{\"trigger\":\"minecraft:impossible\"}},"
                + "\"requirements\":[[\"unlocked\"]]}";
    }

    private static String display(long value, String unit) {
        return value == Long.MAX_VALUE ? "∞" : compact(value) + unit;
    }

    private static String compact(long value) {
        if (value < 1_000L) return Long.toString(value);
        if (value == Integer.MAX_VALUE) return "2G";
        String[] units = {"K", "M", "G", "T", "P", "E"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1_000D && unit + 1 < units.length) {
            scaled /= 1_000D;
            unit++;
        }
        if (scaled < 10D && Math.abs(scaled - Math.rint(scaled)) > 0.0001D) {
            return String.format(java.util.Locale.ROOT, "%.1f%s", scaled, units[unit]);
        }
        return (long) scaled + units[unit];
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
