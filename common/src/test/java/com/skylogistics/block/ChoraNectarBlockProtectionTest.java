package com.skylogistics.block;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChoraNectarBlockProtectionTest {
    private static final String CHORA_NECTAR_BLOCK = "skylogistics:chora_nectar_block";

    @Test
    void blockIsUnbreakableAndExplosionResistant() throws IOException {
        String registration = registrationBlock(readJava("registry/ModBlocks.java"), "CHORA_NECTAR_BLOCK");
        assertAll(
                () -> assertTrue(registration.contains(".strength(-1.0F, 3_600_000.0F)"), registration),
                () -> assertTrue(registration.contains(".forceSolidOn()"), registration));
    }

    @Test
    void dismantledBlockItemSurvivesFireAndLava() throws IOException {
        String registration = registrationBlock(readJava("registry/ModItems.java"), "CHORA_NECTAR_BLOCK");
        assertTrue(registration.contains(".fireResistant()"), registration);
    }

    @Test
    void blockIsImmuneToWitherAndDragonDestruction() throws IOException {
        Path tagDirectory = minecraftBlockTagDirectory();
        assertAll(
                () -> assertTagContains(tagDirectory.resolve("wither_immune.json")),
                () -> assertTagContains(tagDirectory.resolve("dragon_immune.json")));
    }

    @Test
    void globalWrenchDismantlingStillIncludesChoraNectarBlock() throws IOException {
        String source = readJava("SkyLogistics.java");
        assertAll(
                () -> assertTrue(source.contains("tryDismantleWithWrench"), source),
                () -> assertTrue(source.contains("MOD_ID.equals(id.getNamespace())"), source));
    }

    private static String readJava(String relativePath) throws IOException {
        return Files.readString(versionRoot().resolve("src/main/java/com/skylogistics").resolve(relativePath));
    }

    private static String registrationBlock(String source, String fieldName) {
        int start = source.indexOf(fieldName + " = ");
        int end = source.indexOf(";", start);
        assertTrue(start >= 0 && end > start, "Missing registration for " + fieldName);
        return source.substring(start, end);
    }

    private static Path minecraftBlockTagDirectory() throws IOException {
        Path tags = versionRoot().resolve("src/main/resources/data/minecraft/tags");
        Path plural = tags.resolve("blocks");
        return Files.isDirectory(plural) ? plural : tags.resolve("block");
    }

    private static void assertTagContains(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path.toString());
        assertTrue(Files.readString(path).contains(CHORA_NECTAR_BLOCK), path.toString());
    }

    private static Path versionRoot() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isDirectory(directory.resolve("src/main/java/com/skylogistics"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IOException("Could not locate version root");
    }
}
