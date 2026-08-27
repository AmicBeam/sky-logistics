package com.skylogistics.compat.distributor;

/** Runtime settings shared by distributor item, fluid, and chemical machine routing. */
public record AdaptiveRoutingConfig(boolean enabled, int routeCacheSize, int hotTicks, int warmTicks,
        int coolTicks, int fallbackTicks, int missesPerDemotion) {
    public static final AdaptiveRoutingConfig DISABLED = new AdaptiveRoutingConfig(false, 1, 1, 5, 20, 40, 3);
}
