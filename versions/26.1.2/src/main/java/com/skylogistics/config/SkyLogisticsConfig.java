package com.skylogistics.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SkyLogisticsConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    public static int maxVaultTypes() {
        return SERVER.maxVaultTypes.get();
    }

    public static int maxVaultItemEntryNbtBytes() {
        return SERVER.maxVaultItemEntryNbtBytes.get();
    }

    public static int maxVaultFluidEntryNbtBytes() {
        return SERVER.maxVaultFluidEntryNbtBytes.get();
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

    public static boolean allowAe2AppFluxEnergyTransfer() {
        return SERVER.allowAe2AppFluxEnergyTransfer.get();
    }

    public static boolean allowAe2AppliedMekanisticsChemicalTransfer() {
        return SERVER.allowAe2AppliedMekanisticsChemicalTransfer.get();
    }

    public static boolean allowBeyondDimensionsMekanismChemicalTransfer() {
        return SERVER.allowBeyondDimensionsMekanismChemicalTransfer.get();
    }

    public static boolean allowAe2AppliedBotanicsManaTransfer() {
        return SERVER.allowAe2AppliedBotanicsManaTransfer.get();
    }

    public static boolean allowAe2ArsEnergistiqueSourceTransfer() {
        return SERVER.allowAe2ArsEnergistiqueSourceTransfer.get();
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
        public final ModConfigSpec.IntValue maxVaultTypes;
        public final ModConfigSpec.IntValue maxVaultItemEntryNbtBytes;
        public final ModConfigSpec.IntValue maxVaultFluidEntryNbtBytes;
        public final ModConfigSpec.IntValue nodeItemTransferLimit;
        public final ModConfigSpec.IntValue nodeEnergyTransferLimit;
        public final ModConfigSpec.IntValue serverOpsPerTick;
        public final ModConfigSpec.IntValue lineOpsPerTick;
        public final ModConfigSpec.IntValue endpointTargetAttempts;
        public final ModConfigSpec.IntValue externalTankScansPerEndpoint;
        public final ModConfigSpec.IntValue preferredItemSlotCacheSize;
        public final ModConfigSpec.IntValue skyNecklaceTickInterval;
        public final ModConfigSpec.IntValue skyRitualMinY;
        public final ModConfigSpec.IntValue eulogiaCrystalChargeSeconds;
        public final ModConfigSpec.LongValue skyContainerTransferLimit;
        public final ModConfigSpec.BooleanValue allowFluidChemicalTransfer;
        public final ModConfigSpec.BooleanValue allowEnergyManaTransfer;
        public final ModConfigSpec.BooleanValue allowEnergySourceTransfer;
        public final ModConfigSpec.BooleanValue allowAe2AppFluxEnergyTransfer;
        public final ModConfigSpec.BooleanValue allowAe2AppliedMekanisticsChemicalTransfer;
        public final ModConfigSpec.BooleanValue allowBeyondDimensionsMekanismChemicalTransfer;
        public final ModConfigSpec.BooleanValue allowAe2AppliedBotanicsManaTransfer;
        public final ModConfigSpec.BooleanValue allowAe2ArsEnergistiqueSourceTransfer;

        private Server(ModConfigSpec.Builder builder) {
            builder.push("vaults");
            maxVaultTypes = builder
                    .comment("Maximum item/fluid type slots a Celestial Vault can be expanded to with capacity nectar.")
                    .defineInRange("maxVaultTypes", 36, 1, 63);
            maxVaultItemEntryNbtBytes = builder
                    .comment("Maximum serialized NBT bytes allowed for one distinct item stored in an item vault.")
                    .defineInRange("maxVaultItemEntryNbtBytes", 8192, 128, 1_048_576);
            maxVaultFluidEntryNbtBytes = builder
                    .comment("Maximum serialized NBT bytes allowed for one distinct fluid stored in a fluid vault.")
                    .defineInRange("maxVaultFluidEntryNbtBytes", 4096, 128, 1_048_576);
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
            allowAe2AppFluxEnergyTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer AppFlux FE stored in AE2 networks.")
                    .define("allowAe2AppFluxEnergyTransfer", true);
            allowAe2AppliedMekanisticsChemicalTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer Applied Mekanistics chemicals stored in AE2 networks.")
                    .define("allowAe2AppliedMekanisticsChemicalTransfer", true);
            allowBeyondDimensionsMekanismChemicalTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer Mekanism chemicals stored in Beyond Dimensions networks.")
                    .define("allowBeyondDimensionsMekanismChemicalTransfer", true);
            allowAe2AppliedBotanicsManaTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer Applied Botanics mana stored in AE2 networks.")
                    .define("allowAe2AppliedBotanicsManaTransfer", true);
            allowAe2ArsEnergistiqueSourceTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer Ars Energistique source stored in AE2 networks.")
                    .define("allowAe2ArsEnergistiqueSourceTransfer", true);
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
