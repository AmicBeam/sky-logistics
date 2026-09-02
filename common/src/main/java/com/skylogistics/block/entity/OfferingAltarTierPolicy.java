package com.skylogistics.block.entity;

/** Pure tier rules shared by all supported Minecraft versions. */
public final class OfferingAltarTierPolicy {
    public static final int TIER_THREE = 3;

    private OfferingAltarTierPolicy() {
    }

    public static boolean isFrameCorner(int dx, int dz, int radius) {
        return Math.abs(dx) == radius && Math.abs(dz) == radius;
    }

    public static int workProgressPerTick(int structureTier, int tierThreeMultiplier) {
        return structureTier >= TIER_THREE ? Math.max(1, tierThreeMultiplier) : 1;
    }
}
