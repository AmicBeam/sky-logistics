package com.skylogistics.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OfferingAltarTierPolicyTest {
    @Test
    void tierThreeUsesConfiguredWorkMultiplierWithoutChangingLowerTiers() {
        assertEquals(1, OfferingAltarTierPolicy.workProgressPerTick(1, 4));
        assertEquals(1, OfferingAltarTierPolicy.workProgressPerTick(2, 4));
        assertEquals(4, OfferingAltarTierPolicy.workProgressPerTick(3, 4));
        assertEquals(1, OfferingAltarTierPolicy.workProgressPerTick(3, 0));
    }

    @Test
    void onlyTheFourOuterFrameCornersAreTierThreeNectarPositions() {
        assertTrue(OfferingAltarTierPolicy.isFrameCorner(-2, -2, 2));
        assertTrue(OfferingAltarTierPolicy.isFrameCorner(2, -2, 2));
        assertTrue(OfferingAltarTierPolicy.isFrameCorner(-2, 2, 2));
        assertTrue(OfferingAltarTierPolicy.isFrameCorner(2, 2, 2));
        assertFalse(OfferingAltarTierPolicy.isFrameCorner(0, -2, 2));
        assertFalse(OfferingAltarTierPolicy.isFrameCorner(2, 1, 2));
    }
}
