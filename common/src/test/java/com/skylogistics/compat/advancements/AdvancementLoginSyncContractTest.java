package com.skylogistics.compat.advancements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Protects login packet ordering without requiring Spectrum in the test runtime. */
class AdvancementLoginSyncContractTest {
    @Test
    void milestoneSyncLeavesPacketDeliveryToThePlayerTick() throws IOException {
        Path repository = findRepository();
        Path source = repository.resolve("common/src/main/java/com/skylogistics/compat/advancements");
        try (var files = Files.list(source)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file);
                // An eager flush consumes initial progress before Revelationary's login
                // registry packet calls cloakAll(), so unlocked items stay cloaked.
                assertFalse(code.contains("flushDirty"), file.toString());
                assertFalse(code.contains("m_135992_"), file.toString());
                assertFalse(code.contains("AdvancementAccess.flush("), file.toString());
            }
        }
        for (String version : new String[] {"1.20.1", "1.21.1", "26.1.2"}) {
            Path handler = repository.resolve("versions/" + version
                    + "/src/main/java/com/skylogistics/event/ManualGiftHandler.java");
            assertTrue(Files.readString(handler).contains("AdvancementDisplaySync.sync(player)"),
                    "Keep milestone mirroring enabled on " + version);
        }
    }

    private static Path findRepository() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isDirectory(directory.resolve("common/src/main/java"))) return directory;
            directory = directory.getParent();
        }
        throw new IOException("Could not locate shared advancement sources");
    }
}
