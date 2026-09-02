package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KleisEndpointPolicyTest {
    @Test
    void autoDetectionAcceptsAnySupportedResource() {
        assertTrue(KleisEndpointPolicy.supportsConfiguration(true,
                false, false, false, false, true, false));
        assertFalse(KleisEndpointPolicy.supportsConfiguration(true,
                true, true, true, false, false, false));
    }

    @Test
    void manualConfigurationRequiresAnEnabledSupportedResource() {
        assertTrue(KleisEndpointPolicy.supportsConfiguration(false,
                false, false, true, false, false, true));
        assertFalse(KleisEndpointPolicy.supportsConfiguration(false,
                true, false, false, false, true, true));
        assertFalse(KleisEndpointPolicy.supportsConfiguration(false,
                false, false, false, true, true, true));
    }
}
