package com.skylogistics.compat.advancements;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdvancementTransferLimiterTest {
    @Test
    void keepsCachedRatesForFiveSeconds() {
        assertFalse(AdvancementTransferLimiter.cacheExpired(200L, 299L));
        assertTrue(AdvancementTransferLimiter.cacheExpired(200L, 300L));
    }

    @Test
    void refreshesAfterGameTimeMovesBackwards() {
        assertTrue(AdvancementTransferLimiter.cacheExpired(200L, 199L));
    }
}
