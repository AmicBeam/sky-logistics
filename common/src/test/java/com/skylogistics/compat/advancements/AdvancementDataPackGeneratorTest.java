package com.skylogistics.compat.advancements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skylogistics.compat.astages.TransferRates;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvancementDataPackGeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsOnlyActualContentChanges() throws Exception {
        List<AdvancementDisplayEntry> entries = List.of(entry("minecraft:iron_ingot", 16L));

        assertTrue(AdvancementDataPackGenerator.generate(temporaryDirectory, 48, false, entries));
        assertFalse(AdvancementDataPackGenerator.generate(temporaryDirectory, 48, false, entries));
        assertTrue(AdvancementDataPackGenerator.generate(temporaryDirectory, 48, false,
                List.of(entry("minecraft:diamond", 64L))));
        assertFalse(AdvancementDataPackGenerator.generate(temporaryDirectory, 48, false,
                List.of(entry("minecraft:diamond", 64L))));
    }

    @Test
    void removesEntriesNoLongerPresent() throws Exception {
        List<AdvancementDisplayEntry> entries = List.of(
                entry("minecraft:iron_ingot", 16L), entry("minecraft:diamond", 64L));
        AdvancementDataPackGenerator.generate(temporaryDirectory, 48, false, entries);
        Path secondEntry = temporaryDirectory.resolve("skylogistics_progression/data/skylogistics/advancement/"
                + "transfer_rates/entry_1.json");
        assertTrue(Files.exists(secondEntry));

        assertTrue(AdvancementDataPackGenerator.generate(temporaryDirectory, 48, false, entries.subList(0, 1)));
        assertFalse(Files.exists(secondEntry));
        assertFalse(AdvancementDataPackGenerator.generate(temporaryDirectory, 48, false, entries.subList(0, 1)));
    }

    private static AdvancementDisplayEntry entry(String icon, long itemRate) {
        return new AdvancementDisplayEntry("minecraft:story/smelt_iron", icon,
                "advancements.story.smelt_iron.title", "task",
                new TransferRates(itemRate, 625L, 625L, 6_250L, 3L, 3L));
    }
}
