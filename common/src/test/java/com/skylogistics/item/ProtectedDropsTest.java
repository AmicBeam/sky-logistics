package com.skylogistics.item;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtectedDropsTest {
    private static final String[] PROTECTED_ITEMS = {
            "skylogistics:kleis_dominion_wand",
            "skylogistics:chora_nectar",
            "skylogistics:chora_nectar_block",
            "skylogistics:item_vault",
            "skylogistics:fluid_vault"
    };

    @Test
    void protectedDropTagContainsTheWholeItemFamily() throws IOException {
        String tag = Files.readString(protectedDropTag());
        for (String item : PROTECTED_ITEMS) assertTrue(tag.contains(item), item);
    }

    @Test
    void protectedDropsNeverExpireOrTakeDamage() throws IOException {
        String source = Files.readString(versionRoot().resolve("src/main/java/com/skylogistics/SkyLogistics.java"));
        assertAll(
                () -> assertTrue(source.contains("EntityJoinLevelEvent"), source),
                () -> assertTrue(source.contains("setUnlimitedLifetime()"), source),
                () -> assertTrue(source.contains("setInvulnerable(true)"), source));
    }

    private static Path protectedDropTag() throws IOException {
        Path tags = versionRoot().resolve("src/main/resources/data/skylogistics/tags");
        Path plural = tags.resolve("items/protected_drops.json");
        return Files.isRegularFile(plural) ? plural : tags.resolve("item/protected_drops.json");
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
