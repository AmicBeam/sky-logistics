package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KleisEndpointLineChangeContractTest {
    @Test
    void runtimeChangesInvalidateTheVirtualEndpointLineIndex() throws IOException {
        String source = Files.readString(versionRoot().resolve(
                "src/main/java/com/skylogistics/network/KleisEndpointSavedData.java"));
        int start = source.indexOf("private void updateFromRuntime");
        int end = source.indexOf("private static void closeMenus", start);
        assertTrue(start >= 0 && end > start, "Missing runtime update method");
        String updateMethod = source.substring(start, end);
        assertTrue(updateMethod.contains("SkyNetworkRegistry.markVirtualDirty"), updateMethod);
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
