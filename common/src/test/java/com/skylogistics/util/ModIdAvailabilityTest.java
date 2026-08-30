package com.skylogistics.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModIdAvailabilityTest {
    @Test
    void enablesWhenAnyConfiguredModIsLoaded() {
        Set<String> loaded = Set.of("custom_storage");
        assertTrue(ModIdAvailability.anyLoaded(
                List.of("mekanism_extras", "custom_storage"), loaded::contains));
    }

    @Test
    void disablesWhenNoConfiguredModIsLoaded() {
        Set<String> loaded = Set.of("unrelated_mod");
        assertFalse(ModIdAvailability.anyLoaded(
                List.of("mekanism_extras", "custom_storage"), loaded::contains));
    }

    @Test
    void emptyConfigurationDisablesAvailability() {
        assertFalse(ModIdAvailability.anyLoaded(List.of(), modId -> true));
    }
}
