package com.skylogistics.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SkyLogisticsConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    public static int maxVaultTypes() {
        return SERVER.maxVaultTypes.get();
    }

    public static int nodeItemTransferLimit() {
        return SERVER.nodeItemTransferLimit.get();
    }

    public static long skyContainerTransferLimit() {
        return SERVER.skyContainerTransferLimit.get();
    }

    public static int nodeEnergyTransferLimit() {
        return SERVER.nodeEnergyTransferLimit.get();
    }

    public static int serverOpsPerTick() {
        return SERVER.serverOpsPerTick.get();
    }

    public static int lineOpsPerTick() {
        return SERVER.lineOpsPerTick.get();
    }

    public static int endpointTargetAttempts() {
        return SERVER.endpointTargetAttempts.get();
    }

    public static int externalTankScansPerEndpoint() {
        return SERVER.externalTankScansPerEndpoint.get();
    }

    public static boolean allowFluidChemicalTransfer() {
        return SERVER.allowFluidChemicalTransfer.get();
    }

    public static boolean allowEnergyManaTransfer() {
        return SERVER.allowEnergyManaTransfer.get();
    }

    public static boolean allowEnergySourceTransfer() {
        return SERVER.allowEnergySourceTransfer.get();
    }

    public static int preferredItemSlotCacheSize() {
        return SERVER.preferredItemSlotCacheSize.get();
    }

    public static int skyNecklaceTickInterval() {
        return SERVER.skyNecklaceTickInterval.get();
    }

    public static int skyRitualMinY() {
        return SERVER.skyRitualMinY.get();
    }

    public static int eulogiaCrystalChargeSeconds() {
        return SERVER.eulogiaCrystalChargeSeconds.get();
    }

    public static final class Server {
        public final ForgeConfigSpec.IntValue maxVaultTypes;
        public final ForgeConfigSpec.IntValue nodeItemTransferLimit;
        public final ForgeConfigSpec.IntValue nodeEnergyTransferLimit;
        public final ForgeConfigSpec.IntValue serverOpsPerTick;
        public final ForgeConfigSpec.IntValue lineOpsPerTick;
        public final ForgeConfigSpec.IntValue endpointTargetAttempts;
        public final ForgeConfigSpec.IntValue externalTankScansPerEndpoint;
        public final ForgeConfigSpec.IntValue preferredItemSlotCacheSize;
        public final ForgeConfigSpec.IntValue skyNecklaceTickInterval;
        public final ForgeConfigSpec.IntValue skyRitualMinY;
        public final ForgeConfigSpec.IntValue eulogiaCrystalChargeSeconds;
        public final ForgeConfigSpec.LongValue skyContainerTransferLimit;
        public final ForgeConfigSpec.BooleanValue allowFluidChemicalTransfer;
        public final ForgeConfigSpec.BooleanValue allowEnergyManaTransfer;
        public final ForgeConfigSpec.BooleanValue allowEnergySourceTransfer;

        private Server(ForgeConfigSpec.Builder builder) {
            builder.push("vaults");
            maxVaultTypes = builder
                    .comment("Maximum item/fluid type slots a Celestial Vault can be expanded to with capacity nectar.")
                    .defineInRange("maxVaultTypes", 36, 1, 63);
            builder.pop();

            builder.push("transfers");
            nodeItemTransferLimit = builder
                    .comment("Maximum item count moved by a logistics node per normal item transfer operation.")
                    .defineInRange("nodeItemTransferLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
            nodeEnergyTransferLimit = builder
                    .comment("Maximum energy moved by a logistics node per energy transfer operation.")
                    .defineInRange("nodeEnergyTransferLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
            skyContainerTransferLimit = builder
                    .comment("Maximum amount moved per direct transfer operation between Sky Logistics vault containers.")
                    .defineInRange("skyContainerTransferLimit", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
            serverOpsPerTick = builder
                    .comment("Maximum endpoint, slot, tank, and energy transfer operations Sky Logistics may process per server tick.")
                    .defineInRange("serverOpsPerTick", 2048, 1, 1_000_000);
            lineOpsPerTick = builder
                    .comment("Maximum endpoint, slot, tank, and energy transfer operations one logistics line may consume per server tick.")
                    .defineInRange("lineOpsPerTick", 256, 1, 1_000_000);
            endpointTargetAttempts = builder
                    .comment("Maximum receiving endpoints one source endpoint may try for one transfer candidate.")
                    .defineInRange("endpointTargetAttempts", 16, 1, 1_000_000);
            externalTankScansPerEndpoint = builder
                    .comment("Maximum external fluid tanks one source endpoint may scan per tick. Node operation rate still applies.")
                    .defineInRange("externalTankScansPerEndpoint", 8, 1, 1_000_000);
            allowFluidChemicalTransfer = builder
                    .comment("Whether fluid-enabled logistics faces may also transfer Mekanism chemicals.")
                    .define("allowFluidChemicalTransfer", true);
            allowEnergyManaTransfer = builder
                    .comment("Whether energy-enabled logistics faces may also transfer Botania mana.")
                    .define("allowEnergyManaTransfer", true);
            allowEnergySourceTransfer = builder
                    .comment("Whether energy-enabled logistics faces may also transfer Ars Nouveau source.")
                    .define("allowEnergySourceTransfer", true);
            preferredItemSlotCacheSize = builder
                    .comment("Number of successful item source slots remembered as hot slots per source endpoint.")
                    .defineInRange("preferredItemSlotCacheSize", 9, 1, 256);
            builder.pop();

            builder.push("necklaces");
            skyNecklaceTickInterval = builder
                    .comment("Server ticks between Sky Necklace work scans. Higher values reduce player inventory and backpack scanning frequency.")
                    .defineInRange("skyNecklaceTickInterval", 10, 1, 1200);
            builder.pop();

            builder.push("rituals");
            skyRitualMinY = builder
                    .comment("Minimum block Y for Eulogia Crystals to charge and sky offering altars to work.")
                    .defineInRange("skyRitualMinY", 200, -64, 320);
            eulogiaCrystalChargeSeconds = builder
                    .comment("Seconds an uncharged Eulogia Crystal must spend at or above skyRitualMinY before it becomes charged. One second is 20 ticks.")
                    .defineInRange("eulogiaCrystalChargeSeconds", 60, 1, 3600);
            builder.pop();
        }
    }

    private SkyLogisticsConfig() {
    }
}
