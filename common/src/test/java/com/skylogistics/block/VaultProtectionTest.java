package com.skylogistics.block;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class VaultProtectionTest {
    private static final List<String> VAULT_FIELDS = List.of("ITEM_VAULT", "FLUID_VAULT");
    private static final List<String> VAULT_IDS = List.of("skylogistics:item_vault", "skylogistics:fluid_vault");

    @Test
    void vaultsRemainMineableAndResistExplosionsAndFluidFlow() throws IOException {
        String source = readJava("registry/ModBlocks.java");
        for (String field : VAULT_FIELDS) {
            String registration = registrationBlock(source, field);
            assertAll(field,
                    () -> assertTrue(registration.contains(".strength(3.0F, 3_600_000.0F)"), registration),
                    () -> assertTrue(registration.contains(".forceSolidOn()"), registration));
        }
    }

    @Test
    void recoveredVaultItemsSurviveFireAndLava() throws IOException {
        String source = readJava("registry/ModItems.java");
        for (String field : VAULT_FIELDS) {
            String registration = registrationBlock(source, field);
            assertTrue(registration.contains(".fireResistant()"), registration);
        }
    }

    @Test
    void vaultsAreImmuneToWitherAndDragonDestruction() throws IOException {
        Path tags = minecraftBlockTagDirectory();
        assertAll(
                () -> assertTagContainsVaults(tags.resolve("wither_immune.json")),
                () -> assertTagContainsVaults(tags.resolve("dragon_immune.json")));
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

    private static void assertTagContainsVaults(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path.toString());
        String source = Files.readString(path);
        for (String id : VAULT_IDS) {
            assertTrue(source.contains(id), path + " is missing " + id);
        }
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
