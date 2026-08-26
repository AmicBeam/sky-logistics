package com.skylogistics.compat.astages;

public enum TransferResource {
    ITEMS("items"),
    FLUIDS("fluids"),
    CHEMICALS("chemicals"),
    SOULS("souls"),
    ENERGY("energy"),
    MANA("mana"),
    SOURCE("source");

    private final String configKey;

    TransferResource(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
