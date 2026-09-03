package com.skylogistics.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KleisDominionWandTeleportConfigTest {
    @Test
    void entityTeleportHasAnEnabledByDefaultSwitchAndConfigurableHeight() throws IOException {
        String config = readJava("config/SkyLogisticsConfig.java");
        assertTrue(config.contains("define(\"enableEntityTeleport\", true)"), config);
        assertTrue(config.contains("defineInRange(\"teleportY\", 256, -20_000_000, 20_000_000)"), config);
    }

    @Test
    void wandReadsBothTeleportSettingsInsteadOfUsingAHardcodedHeight() throws IOException {
        String wand = readJava("item/KleisDominionWandItem.java");
        assertTrue(wand.contains("enableKleisDominionWandEntityTeleport()"), wand);
        assertTrue(wand.contains("kleisDominionWandTeleportY()"), wand);
        assertFalse(wand.contains("targetY = 256.0D"), wand);
    }

    private static String readJava(String relativePath) throws IOException {
        return Files.readString(versionRoot().resolve("src/main/java/com/skylogistics").resolve(relativePath));
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
