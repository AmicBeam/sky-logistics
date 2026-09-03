package com.skylogistics.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KleisDominionWandProtectionTest {
    @Test
    void droppedWandSurvivesFireAndLava() throws IOException {
        String source = Files.readString(versionRoot().resolve(
                "src/main/java/com/skylogistics/registry/ModItems.java"));
        int start = source.indexOf("KLEIS_DOMINION_WAND");
        int end = source.indexOf(";", start);
        assertTrue(start >= 0 && end > start, "Missing Kleis Dominion Wand registration");
        String registration = source.substring(start, end);
        assertTrue(registration.contains(".fireResistant()"), registration);
    }

    private static Path versionRoot() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isDirectory(directory.resolve("src/main/java/com/skylogistics"))) return directory;
            directory = directory.getParent();
        }
        throw new IOException("Could not locate version root");
    }
}
