package com.skylogistics.compat.sophisticated;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SophisticatedStackUpgradeContractTest {
    private static final String COMPAT_PATH =
            "src/main/java/com/skylogistics/compat/sophisticated/SophisticatedStorageCompat.java";

    @Test
    void placedBackpacksAndStorageUseIndependentLoadedModAndConfigGuards() throws IOException {
        String source = read(COMPAT_PATH);
        String supports = section(source, "public static boolean supports(", "public static ItemStack fullSlotCandidate(");
        assertTrue(supports.contains("if (blockEntity == null) return false;"), supports);
        String storageBranch = section(supports, "if (className.startsWith(PACKAGE_PREFIX))", "\n        }");
        assertTrue(storageBranch.contains("allowSophisticatedStorageStackUpgradeTransfer()"), storageBranch);
        assertTrue(storageBranch.contains("ModList.get().isLoaded(MOD_ID)"), storageBranch);
        assertFalse(storageBranch.contains("allowSophisticatedBackpacksStackUpgradeTransfer"), storageBranch);
        String backpackBranch = supports.substring(supports.indexOf("return className.startsWith(BACKPACKS_PACKAGE_PREFIX)"));
        assertTrue(backpackBranch.contains("allowSophisticatedBackpacksStackUpgradeTransfer()"), backpackBranch);
        assertTrue(backpackBranch.contains("ModList.get().isLoaded(BACKPACKS_MOD_ID)"), backpackBranch);
        assertFalse(backpackBranch.contains("allowSophisticatedStorageStackUpgradeTransfer"), backpackBranch);
        assertTrue(source.contains("BACKPACKS_MOD_ID = \"sophisticatedbackpacks\""), source);
        assertTrue(source.contains("BACKPACKS_PACKAGE_PREFIX = \"net.p3pp3rf1y.sophisticatedbackpacks.\""), source);
    }

    @Test
    void backpackOnlyInstallUsesTheSharedCoreInterface() throws IOException {
        String source = read(COMPAT_PATH);
        String init = section(source, "private static boolean initDirectAccess()", "private static boolean sameStack(");
        assertTrue(init.contains("!ModList.get().isLoaded(MOD_ID) && !ModList.get().isLoaded(BACKPACKS_MOD_ID)"), init);
        assertTrue(init.contains("net.p3pp3rf1y.sophisticatedcore.controller.IControllableStorage"), init);
        assertTrue(init.contains("controllableStorageClass.getMethod(\"getStorageWrapper\")"), init);
        assertFalse(init.contains("net.p3pp3rf1y.sophisticatedstorage."), init);
        assertFalse(init.contains("net.p3pp3rf1y.sophisticatedbackpacks."), init);
        assertTrue(init.contains("catch (ReflectiveOperationException | LinkageError error)"), init);
    }

    @Test
    void directAccessUsesTheVersionSpecificInventoryApi() throws IOException {
        String source = read(COMPAT_PATH);
        if (Files.readString(versionRoot().resolve("gradle.properties")).contains("minecraft_version=26.1.2")) {
            assertTrue(source.contains("getSlots = inventoryHandlerClass.getMethod(\"size\")"), source);
            assertTrue(source.contains("getMethod(\"getCapacityAsLong\", int.class, ItemResource.class)"), source);
            assertTrue(source.contains("getCapacityAsLong.invoke(direct.inventory(), slot, ItemResource.of(stored))"), source);
            assertTrue(source.contains("getCapacityAsLong.invoke(direct.inventory(), slot, ItemResource.of(restored))"), source);
            assertFalse(source.contains("getMethod(\"getStackLimit\""), source);
        } else {
            assertTrue(source.contains("getSlots = inventoryHandlerClass.getMethod(\"getSlots\")"), source);
            assertTrue(source.contains("getMethod(\"getStackLimit\", int.class, ItemStack.class)"), source);
        }
    }

    @Test
    void directAccessRetainsSlotAndUpgradeSafetyChecks() throws IOException {
        String source = read(COMPAT_PATH);
        String direct = section(source, "private static DirectInventory directInventory(", "private static boolean initDirectAccess()");
        assertTrue(direct.contains("!supports(blockEntity) || !initDirectAccess() || !controllableStorageClass.isInstance(blockEntity)"), direct);
        assertTrue(direct.contains("getExtractResponseUpgrades.invoke(upgradeHandler, extractResponseUpgradeClass)"), direct);
        assertTrue(direct.contains("!collection.isEmpty()"), direct);
        assertTrue(direct.contains("slot < 0 || slot >= (int) getSlots.invoke(inventory)"), direct);
        assertTrue(direct.contains("!(boolean) isSlotAccessible.invoke(inventory, slot)"), direct);
        assertTrue(direct.contains("(boolean) isInfinite.invoke(inventory, slot)"), direct);
        assertTrue(direct.contains("\"default\".equals(getPartName.invoke(part))"), direct);
        assertTrue(source.contains("if (direct == null || simulated.isEmpty()) return simulated;"), source);
        assertTrue(source.contains("if (direct == null) return DirectExtraction.UNSUPPORTED;"), source);
    }

    @Test
    void schedulerUsesTheSameBridgeForCapacityExtractionAndRollback() throws IOException {
        String source = read("src/main/java/com/skylogistics/network/SkyNetworkTicker.java");
        assertTrue(source.contains("SophisticatedStorageCompat.supports(endpoint.targetBlockEntity())\n"
                + "                ? target.getSlotLimit(slot)"), source);
        assertTrue(source.contains("SophisticatedStorageCompat.fullSlotCandidate("), source);
        assertTrue(source.contains("SophisticatedStorageCompat.extractDirect("), source);
        assertTrue(source.contains("SophisticatedStorageCompat.restoreDirect("), source);
    }

    @Test
    void backpackConfigDefaultsToEnabledInTransferIntegrations() throws IOException {
        String source = read("src/main/java/com/skylogistics/config/SkyLogisticsConfig.java");
        assertTrue(source.contains("return SERVER.allowSophisticatedBackpacksStackUpgradeTransfer.get();"), source);
        int category = source.indexOf(".push(\"integrations\")");
        int definition = source.indexOf(".define(\"allowSophisticatedBackpacksStackUpgradeTransfer\", true)");
        int nextCategory = source.indexOf("builder.push(", category);
        assertTrue(category >= 0 && definition > category && nextCategory > definition, source);
        assertTrue(source.contains(".define(\"allowSophisticatedStorageStackUpgradeTransfer\", true)"), source);
    }

    @Test
    void backpackDependencyIsOptionalOnEveryLoader() throws IOException {
        Path root = versionRoot();
        Path forgeMetadata = root.resolve("src/main/resources/META-INF/mods.toml");
        String metadata = Files.readString(Files.isRegularFile(forgeMetadata) ? forgeMetadata
                : root.resolve("src/main/templates/META-INF/neoforge.mods.toml"));
        String dependency = section(metadata, "modId=\"sophisticatedbackpacks\"", "[[dependencies.");
        assertTrue(dependency.contains("mandatory=false") || dependency.contains("type=\"optional\""), dependency);
        assertTrue(dependency.contains("ordering=\"AFTER\""), dependency);
    }

    private static String read(String path) throws IOException {
        return Files.readString(versionRoot().resolve(path));
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0 && end > start, "Missing section: " + startMarker);
        return source.substring(start, end);
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
