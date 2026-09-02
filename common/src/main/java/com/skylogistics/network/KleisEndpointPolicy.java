package com.skylogistics.network;

/** Pure decision rules shared by the version-specific Kleis capability probes. */
public final class KleisEndpointPolicy {
    private KleisEndpointPolicy() {
    }

    public static boolean hasEnabledCapability(boolean itemsEnabled, boolean fluidsEnabled, boolean energyEnabled,
            boolean supportsItems, boolean supportsFluids, boolean supportsEnergy) {
        return itemsEnabled && supportsItems
                || fluidsEnabled && supportsFluids
                || energyEnabled && supportsEnergy;
    }

    public static boolean supportsConfiguration(boolean autoDetectResources,
            boolean itemsEnabled, boolean fluidsEnabled, boolean energyEnabled,
            boolean supportsItems, boolean supportsFluids, boolean supportsEnergy) {
        if (autoDetectResources) return supportsItems || supportsFluids || supportsEnergy;
        return hasEnabledCapability(itemsEnabled, fluidsEnabled, energyEnabled,
                supportsItems, supportsFluids, supportsEnergy);
    }

    public static boolean revisionMatches(int expectedRevision, int currentRevision) {
        return expectedRevision < 0 || expectedRevision == currentRevision;
    }

    public static int addEndpointMode(int mask, boolean extracting) {
        return mask | (extracting ? 1 : 2);
    }

    public static boolean hasMixedEndpointModes(int mask) {
        return (mask & 3) == 3;
    }
}
