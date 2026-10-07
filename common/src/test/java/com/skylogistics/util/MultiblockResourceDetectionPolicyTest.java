package com.skylogistics.util;

import static com.skylogistics.util.MultiblockResourceDetectionPolicy.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MultiblockResourceDetectionPolicyTest {
    @Test
    void spsChemicalResourceRecoversOnlyWhenTheFormedCapabilityAppears() {
        int enabled = ENERGY;
        enabled = recover(enabled, 0, FLUIDS);
        assertEquals(ENERGY, enabled, "An unformed SPS must not invent a chemical capability");
        enabled = recover(enabled, FLUIDS | ENERGY, FLUIDS);
        assertEquals(FLUIDS | ENERGY, enabled);
        assertEquals(enabled, recover(enabled, 0, FLUIDS), "Temporary capability loss must not disable resources");
    }

    @Test
    void aResourceWhitelistNeverAuthorizesOtherResourceTypes() {
        assertEquals(FLUIDS, recover(0, ALL, FLUIDS));
        assertEquals(ENERGY, recover(0, ALL, ENERGY));
        assertEquals(ITEMS, recover(0, ALL, ITEMS));
        assertEquals(0, recover(0, ALL, 0), "Empty whitelists must disable recovery");
    }

    @Test
    void savedDisabledResourcesRecoverWithoutRequiringAnAutoConfigurationFlag() {
        int savedEnabled = ITEMS;
        assertEquals(ALL, recover(savedEnabled, ALL, FLUIDS | ENERGY));
        assertEquals(ITEMS, recover(savedEnabled, 0, ALL));
    }

    @Test
    void defaultWhitelistsCoverAllMekanismMultiblockFamiliesWithSeparateResources() {
        var items = defaultBlockWhitelist(ITEMS);
        var fluids = defaultBlockWhitelist(FLUIDS);
        var energy = defaultBlockWhitelist(ENERGY);
        assertTrue(fluids.contains("mekanism:sps_port"));
        assertTrue(energy.contains("mekanism:sps_port"));
        assertFalse(items.contains("mekanism:sps_port"));
        assertTrue(items.contains("mekanism:dynamic_valve"));
        assertTrue(fluids.contains("mekanism:dynamic_valve"));
        assertTrue(energy.contains("mekanism:induction_port"));
        assertFalse(fluids.contains("mekanism:induction_port"));
        assertTrue(fluids.contains("mekanism:thermal_evaporation_valve"));
        assertTrue(fluids.contains("mekanism:boiler_valve"));
        assertTrue(fluids.contains("mekanismgenerators:fission_reactor_port"));
        assertTrue(fluids.contains("mekanismgenerators:fusion_reactor_port"));
        assertTrue(energy.contains("mekanismgenerators:fusion_reactor_port"));
        assertTrue(fluids.contains("mekanismgenerators:turbine_valve"));
        assertTrue(energy.contains("mekanismgenerators:turbine_vent"));
        for (int resource : new int[] {ITEMS, FLUIDS, ENERGY}) {
            var whitelist = defaultBlockWhitelist(resource);
            assertEquals(whitelist.size(), whitelist.stream().distinct().count());
            assertTrue(whitelist.stream().allMatch(MultiblockResourceDetectionPolicy::validBlockId));
        }
    }

    @Test
    void whitelistEntriesAreExactNamespacedBlockIds() {
        assertTrue(validBlockId("mekanism:sps_port"));
        assertTrue(validBlockId("custom:machine/port"));
        assertFalse(validBlockId("mekanism:*"));
        assertFalse(validBlockId("mekanism"));
        assertFalse(validBlockId("Mekanism:sps_port"));
        assertFalse(validBlockId(null));
    }
}
