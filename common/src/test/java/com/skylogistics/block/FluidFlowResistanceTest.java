package com.skylogistics.block;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FluidFlowResistanceTest {
    @Test
    void logisticsNodeForcesSolidMotionBlockingDespiteItsNarrowShape() throws IOException {
        assertConstructorForcesSolid("SkyNodeBlock.java");
    }

    @Test
    void everySimplePipeForcesSolidMotionBlockingDespiteItsNarrowShape() throws IOException {
        assertConstructorForcesSolid("SimplePipeBlock.java");
    }

    private static void assertConstructorForcesSolid(String sourceFile) throws IOException {
        Path path = findVersionSource(sourceFile);
        String source = Files.readString(path);
        assertTrue(source.contains("super(properties.forceSolidOn());"), path.toString());
    }

    private static Path findVersionSource(String sourceFile) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("src/main/java/com/skylogistics/block").resolve(sourceFile);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IOException("Could not locate version source file " + sourceFile);
    }
}
