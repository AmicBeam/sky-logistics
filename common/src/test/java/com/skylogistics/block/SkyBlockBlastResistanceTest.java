package com.skylogistics.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SkyBlockBlastResistanceTest {
    private static final Pattern STRENGTH_CALL = Pattern.compile("\\.strength\\([^,]+,\\s*([^)]+)\\)");

    @Test
    void everyNonVaultLogisticsBlockUsesAtLeastObsidianBlastResistance() throws IOException {
        Path sourcePath = findVersionSource();
        String source = Files.readString(sourcePath);
        String nonVaultSource = withoutRegistration(withoutRegistration(source, "ITEM_VAULT"), "FLUID_VAULT");
        Matcher matcher = STRENGTH_CALL.matcher(nonVaultSource);
        int strengthCalls = 0;

        assertTrue(source.contains("private static final float OBSIDIAN_BLAST_RESISTANCE = 1_200.0F;"),
                sourcePath.toString());
        while (matcher.find()) {
            strengthCalls++;
            String blastResistance = matcher.group(1).trim();
            assertTrue(blastResistance.equals("OBSIDIAN_BLAST_RESISTANCE")
                            || blastResistance.equals("3_600_000.0F"),
                    "Unexpected blast resistance " + blastResistance + " in " + sourcePath);
        }
        assertEquals(expectedNonVaultStrengthCallCount(source), strengthCalls, sourcePath.toString());
    }

    private static int expectedNonVaultStrengthCallCount(String source) {
        return source.contains("RegistryObject<Block>") ? 11 : 9;
    }

    private static String withoutRegistration(String source, String fieldName) {
        int start = source.indexOf(fieldName + " = ");
        int end = source.indexOf(";", start);
        assertTrue(start >= 0 && end > start, "Missing registration for " + fieldName);
        return source.substring(0, start) + source.substring(end + 1);
    }

    private static Path findVersionSource() throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("src/main/java/com/skylogistics/registry/ModBlocks.java");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IOException("Could not locate version ModBlocks.java");
    }
}
