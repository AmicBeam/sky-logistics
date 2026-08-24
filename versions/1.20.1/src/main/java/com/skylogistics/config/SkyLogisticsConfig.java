package com.skylogistics.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.skylogistics.compat.astages.StageRateRules;
import com.skylogistics.compat.astages.StageTransferRates;
import com.skylogistics.compat.astages.TransferRates;
import com.skylogistics.compat.astages.TransferResource;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;

public final class SkyLogisticsConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    private static List<? extends Object> cachedAStagesEntries;
    private static TransferRates cachedAStagesInitial;
    private static StageRateRules cachedAStagesRules;

    static {
        ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();

        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
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

    public static boolean enableAStagesTransferRates() {
        return SERVER.enableAStagesTransferRates.get();
    }

    public static synchronized StageRateRules aStagesTransferRateRules() {
        TransferRates initial = new TransferRates(SERVER.aStagesInitialItems.get(),
                SERVER.aStagesInitialFluids.get(), SERVER.aStagesInitialChemicals.get(),
                SERVER.aStagesInitialEnergy.get(), SERVER.aStagesInitialMana.get(),
                SERVER.aStagesInitialSource.get());
        List<? extends Object> entries = SERVER.aStagesStageRates.get();
        if (cachedAStagesRules == null || cachedAStagesEntries != entries
                || !initial.equals(cachedAStagesInitial)) {
            Map<String, StageTransferRates> stages = new HashMap<>();
            for (Object value : entries) {
                UnmodifiableConfig entry = (UnmodifiableConfig) value;
                String stage = entry.get("stage");
                EnumMap<TransferResource, Long> rates = new EnumMap<>(TransferResource.class);
                for (TransferResource resource : TransferResource.values()) {
                    Number rate = entry.get(resource.configKey());
                    if (rate != null) rates.put(resource, rate.longValue());
                }
                StageTransferRates parsed = new StageTransferRates(rates);
                stages.merge(stage, parsed, StageTransferRates::mergeMax);
            }
            cachedAStagesEntries = entries;
            cachedAStagesInitial = initial;
            cachedAStagesRules = new StageRateRules(initial, stages);
        }
        return cachedAStagesRules;
    }

    private static boolean validAStagesStageRateEntry(Object value) {
        if (!(value instanceof UnmodifiableConfig entry)) return false;
        Object stage = entry.get("stage");
        if (!(stage instanceof String text) || text.isBlank()) return false;
        for (String key : entry.valueMap().keySet()) {
            if ("stage".equals(key)) continue;
            TransferResource resource = null;
            for (TransferResource candidate : TransferResource.values()) {
                if (candidate.configKey().equals(key)) {
                    resource = candidate;
                    break;
                }
            }
            if (resource == null) return false;
            Object rate = entry.get(key);
            if (!(rate instanceof Number number) || number.longValue() < 1L) return false;
        }
        return true;
    }

    public static boolean enableSimpleItemPipe() {
        return SERVER.enableSimpleItemPipe.get();
    }

    public static boolean enableSimpleFluidPipe() {
        return SERVER.enableSimpleFluidPipe.get();
    }

    public static boolean enableSimpleEnergyPipe() {
        return SERVER.enableSimpleEnergyPipe.get();
    }

    public static boolean enableDistributorItems() { return SERVER.enableDistributorItems.get(); }
    public static boolean enableDistributorFluids() { return SERVER.enableDistributorFluids.get(); }
    public static boolean enableDistributorEnergy() { return SERVER.enableDistributorEnergy.get(); }
    public static int distributorMaxTargets() { return SERVER.distributorMaxTargets.get(); }
    public static int distributorScanOpsPerTick() { return SERVER.distributorScanOpsPerTick.get(); }
    public static int distributorOpsPerTick() { return SERVER.distributorOpsPerTick.get(); }

    public static int simpleItemPipeTransferRate() {
        return SERVER.simpleItemPipeTransferRate.get();
    }

    public static int simpleFluidPipeTransferRate() {
        return SERVER.simpleFluidPipeTransferRate.get();
    }

    public static int simpleEnergyPipeTransferRate() {
        return SERVER.simpleEnergyPipeTransferRate.get();
    }

    public static int simpleChemicalPipeTransferRate() {
        return SERVER.simpleChemicalPipeTransferRate.get();
    }

    public static int simpleManaPipeTransferRate() {
        return SERVER.simpleManaPipeTransferRate.get();
    }

    public static int simpleSourcePipeTransferRate() {
        return SERVER.simpleSourcePipeTransferRate.get();
    }

    public static int simplePipeMaxConnectedBlocks() {
        return enforceSimplePipeConnectionLimit()
                ? SERVER.simplePipeMaxConnectedBlocks.get()
                : Integer.MAX_VALUE;
    }

    public static int maxSpeedUpgradesPerNode() {
        return SERVER.maxSpeedUpgradesPerNode.get();
    }

    public static boolean enforceSimplePipeConnectionLimit() {
        return SERVER.enforceSimplePipeConnectionLimit.get();
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

    public static int sourceSearchAttemptsPerEndpoint() {
        return SERVER.sourceSearchAttemptsPerEndpoint.get();
    }

    public static int maxItemSlotLimit() {
        return SERVER.maxItemSlotLimit.get();
    }

    public static boolean allowAe2ItemTransfer() {
        return SERVER.allowAe2ItemTransfer.get();
    }

    public static boolean allowSophisticatedStorageStackUpgradeTransfer() {
        return SERVER.allowSophisticatedStorageStackUpgradeTransfer.get();
    }

    public static boolean allowAe2FluidTransfer() {
        return SERVER.allowAe2FluidTransfer.get();
    }

    public static boolean allowRefinedStorageItemTransfer() {
        return SERVER.allowRefinedStorageItemTransfer.get();
    }

    public static boolean allowRefinedStorageFluidTransfer() {
        return SERVER.allowRefinedStorageFluidTransfer.get();
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

    public static boolean allowBeyondDimensionsItemTransfer() {
        return SERVER.allowBeyondDimensionsItemTransfer.get();
    }

    public static boolean allowBeyondDimensionsFluidTransfer() {
        return SERVER.allowBeyondDimensionsFluidTransfer.get();
    }

    public static boolean allowBeyondDimensionsEnergyTransfer() {
        return SERVER.allowBeyondDimensionsEnergyTransfer.get();
    }

    public static boolean allowBeyondDimensionsMekanismChemicalTransfer() {
        return SERVER.allowBeyondDimensionsMekanismChemicalTransfer.get();
    }

    public static boolean allowBeyondDimensionsManaTransfer() {
        return SERVER.allowBeyondDimensionsManaTransfer.get();
    }

    public static boolean allowBeyondDimensionsSourceTransfer() {
        return SERVER.allowBeyondDimensionsSourceTransfer.get();
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

    public static int targetItemInsertionCursorCount() {
        return SERVER.targetItemInsertionCursorCount.get();
    }

    public static int rejectedAcceptCacheSize() {
        return SERVER.rejectedAcceptCacheSize.get();
    }

    public static int transferRetryDelayTicks(int failures) {
        return SERVER.transferRetryDelayTicks(failures);
    }

    public static int skyNecklaceTickInterval() {
        return SERVER.skyNecklaceTickInterval.get();
    }

    public static int skyNecklaceSlotScansPerTick() {
        return SERVER.skyNecklaceSlotScansPerTick.get();
    }

    public static int skyNecklaceTargetAttemptsPerWork() {
        return SERVER.skyNecklaceTargetAttemptsPerWork.get();
    }

    public static int skyRitualMinY() {
        return SERVER.skyRitualMinY.get();
    }

    public static int eulogiaCrystalChargeSeconds() {
        return SERVER.eulogiaCrystalChargeSeconds.get();
    }

    public static boolean renderConfiguratorPlayerHeads() {
        return CLIENT.renderConfiguratorPlayerHeads.get();
    }

    public static final class Server {
        public final ForgeConfigSpec.IntValue maxVaultTypes;
        public final ForgeConfigSpec.IntValue maxVaultItemEntryNbtBytes;
        public final ForgeConfigSpec.IntValue maxVaultFluidEntryNbtBytes;
        public final ForgeConfigSpec.IntValue nodeItemTransferLimit;
        public final ForgeConfigSpec.IntValue nodeEnergyTransferLimit;
        public final ForgeConfigSpec.BooleanValue enableAStagesTransferRates;
        public final ForgeConfigSpec.LongValue aStagesInitialItems;
        public final ForgeConfigSpec.LongValue aStagesInitialFluids;
        public final ForgeConfigSpec.LongValue aStagesInitialChemicals;
        public final ForgeConfigSpec.LongValue aStagesInitialEnergy;
        public final ForgeConfigSpec.LongValue aStagesInitialMana;
        public final ForgeConfigSpec.LongValue aStagesInitialSource;
        public final ForgeConfigSpec.ConfigValue<List<? extends Object>> aStagesStageRates;
        public final ForgeConfigSpec.IntValue serverOpsPerTick;
        public final ForgeConfigSpec.IntValue lineOpsPerTick;
        public final ForgeConfigSpec.IntValue endpointTargetAttempts;
        public final ForgeConfigSpec.IntValue externalTankScansPerEndpoint;
        public final ForgeConfigSpec.IntValue sourceSearchAttemptsPerEndpoint;
        public final ForgeConfigSpec.IntValue maxItemSlotLimit;
        public final ForgeConfigSpec.IntValue preferredItemSlotCacheSize;
        public final ForgeConfigSpec.IntValue targetItemInsertionCursorCount;
        public final ForgeConfigSpec.IntValue rejectedAcceptCacheSize;
        public final ForgeConfigSpec.IntValue transferRetryFirstTicks;
        public final ForgeConfigSpec.IntValue transferRetrySecondTicks;
        public final ForgeConfigSpec.IntValue transferRetryThirdTicks;
        public final ForgeConfigSpec.IntValue transferRetryMaxTicks;
        public final ForgeConfigSpec.IntValue skyNecklaceTickInterval;
        public final ForgeConfigSpec.IntValue skyNecklaceSlotScansPerTick;
        public final ForgeConfigSpec.IntValue skyNecklaceTargetAttemptsPerWork;
        public final ForgeConfigSpec.IntValue skyRitualMinY;
        public final ForgeConfigSpec.IntValue eulogiaCrystalChargeSeconds;
        public final ForgeConfigSpec.LongValue skyContainerTransferLimit;
        public final ForgeConfigSpec.BooleanValue allowAe2ItemTransfer;
        public final ForgeConfigSpec.BooleanValue allowSophisticatedStorageStackUpgradeTransfer;
        public final ForgeConfigSpec.BooleanValue allowAe2FluidTransfer;
        public final ForgeConfigSpec.BooleanValue allowRefinedStorageItemTransfer;
        public final ForgeConfigSpec.BooleanValue allowRefinedStorageFluidTransfer;
        public final ForgeConfigSpec.BooleanValue allowFluidChemicalTransfer;
        public final ForgeConfigSpec.BooleanValue allowEnergyManaTransfer;
        public final ForgeConfigSpec.BooleanValue allowEnergySourceTransfer;
        public final ForgeConfigSpec.BooleanValue allowAe2AppFluxEnergyTransfer;
        public final ForgeConfigSpec.BooleanValue allowAe2AppliedMekanisticsChemicalTransfer;
        public final ForgeConfigSpec.BooleanValue allowBeyondDimensionsItemTransfer;
        public final ForgeConfigSpec.BooleanValue allowBeyondDimensionsFluidTransfer;
        public final ForgeConfigSpec.BooleanValue allowBeyondDimensionsEnergyTransfer;
        public final ForgeConfigSpec.BooleanValue allowBeyondDimensionsMekanismChemicalTransfer;
        public final ForgeConfigSpec.BooleanValue allowBeyondDimensionsManaTransfer;
        public final ForgeConfigSpec.BooleanValue allowBeyondDimensionsSourceTransfer;
        public final ForgeConfigSpec.BooleanValue allowAe2AppliedBotanicsManaTransfer;
        public final ForgeConfigSpec.BooleanValue allowAe2ArsEnergistiqueSourceTransfer;
        public final ForgeConfigSpec.BooleanValue enableSimpleItemPipe;
        public final ForgeConfigSpec.BooleanValue enableSimpleFluidPipe;
        public final ForgeConfigSpec.BooleanValue enableSimpleEnergyPipe;
        public final ForgeConfigSpec.BooleanValue enableDistributorItems;
        public final ForgeConfigSpec.BooleanValue enableDistributorFluids;
        public final ForgeConfigSpec.BooleanValue enableDistributorEnergy;
        public final ForgeConfigSpec.IntValue distributorMaxTargets;
        public final ForgeConfigSpec.IntValue distributorScanOpsPerTick;
        public final ForgeConfigSpec.IntValue distributorOpsPerTick;
        public final ForgeConfigSpec.IntValue simpleItemPipeTransferRate;
        public final ForgeConfigSpec.IntValue simpleFluidPipeTransferRate;
        public final ForgeConfigSpec.IntValue simpleEnergyPipeTransferRate;
        public final ForgeConfigSpec.IntValue simpleChemicalPipeTransferRate;
        public final ForgeConfigSpec.IntValue simpleManaPipeTransferRate;
        public final ForgeConfigSpec.IntValue simpleSourcePipeTransferRate;
        public final ForgeConfigSpec.BooleanValue enforceSimplePipeConnectionLimit;
        public final ForgeConfigSpec.IntValue simplePipeMaxConnectedBlocks;
        public final ForgeConfigSpec.IntValue maxSpeedUpgradesPerNode;

        private Server(ForgeConfigSpec.Builder builder) {
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
            builder.push("astages");
            enableAStagesTransferRates = builder
                    .comment("Whether AStages player stages limit and unlock per-operation transfer amounts. Requires AStages 2.x.")
                    .define("enabled", false);
            builder.push("initialRates");
            aStagesInitialItems = builder.defineInRange("items", 64L, 1L, Long.MAX_VALUE);
            aStagesInitialFluids = builder.defineInRange("fluids", 10_000L, 1L, Long.MAX_VALUE);
            aStagesInitialChemicals = builder.defineInRange("chemicals", 10_000L, 1L, Long.MAX_VALUE);
            aStagesInitialEnergy = builder.defineInRange("energy", 100_000L, 1L, Long.MAX_VALUE);
            aStagesInitialMana = builder.defineInRange("mana", 100_000L, 1L, Long.MAX_VALUE);
            aStagesInitialSource = builder.defineInRange("source", 100_000L, 1L, Long.MAX_VALUE);
            builder.pop();
            aStagesStageRates = builder
                    .comment("AStages unlock entries. Each entry requires stage and may define any of: items, fluids, chemicals, energy, mana, source.",
                            "Example: [{ stage = \"logistics_tier_1\", items = 128, fluids = 20000 }]")
                    .defineListAllowEmpty("stageRates", List.of(), SkyLogisticsConfig::validAStagesStageRateEntry);
            builder.pop();
            enableSimpleItemPipe = builder
                    .comment("Whether simple item pipes connect to inventories and transfer items.")
                    .define("enableSimpleItemPipe", true);
            enableSimpleFluidPipe = builder
                    .comment("Whether simple fluid pipes connect to tanks and transfer fluids.")
                    .define("enableSimpleFluidPipe", true);
            enableSimpleEnergyPipe = builder
                    .comment("Whether simple energy pipes connect to FE storages and transfer energy.")
                    .define("enableSimpleEnergyPipe", true);
            simpleItemPipeTransferRate = builder
                    .comment("Maximum items moved by each extracting simple item pipe per tick. A transfer still uses at most one source slot and one target slot.")
                    .defineInRange("simpleItemPipeTransferRate", 64, 1, Integer.MAX_VALUE);
            simpleFluidPipeTransferRate = builder
                    .comment("Maximum fluid amount in mB moved by each extracting simple fluid pipe per tick.")
                    .defineInRange("simpleFluidPipeTransferRate", 10_000, 1, Integer.MAX_VALUE);
            simpleEnergyPipeTransferRate = builder
                    .comment("Maximum FE moved by each extracting simple energy pipe per tick.")
                    .defineInRange("simpleEnergyPipeTransferRate", 100_000, 1, Integer.MAX_VALUE);
            simpleChemicalPipeTransferRate = builder
                    .comment("Maximum Mekanism chemical amount moved by each extracting simple fluid pipe per tick.")
                    .defineInRange("simpleChemicalPipeTransferRate", 10_000, 1, Integer.MAX_VALUE);
            simpleManaPipeTransferRate = builder
                    .comment("Maximum Botania mana moved by each extracting simple energy pipe per tick.")
                    .defineInRange("simpleManaPipeTransferRate", 50, 1, Integer.MAX_VALUE);
            simpleSourcePipeTransferRate = builder
                    .comment("Maximum Ars Nouveau source moved by each extracting simple energy pipe per tick.")
                    .defineInRange("simpleSourcePipeTransferRate", 50, 1, Integer.MAX_VALUE);
            enforceSimplePipeConnectionLimit = builder
                    .comment("Whether new simple pipe connections are rejected when they would exceed simplePipeMaxConnectedBlocks.")
                    .define("enforceSimplePipeConnectionLimit", true);
            simplePipeMaxConnectedBlocks = builder
                    .comment("Maximum connected blocks in one simple pipe line. New pipe edges that would exceed this limit stay disconnected.")
                    .defineInRange("simplePipeMaxConnectedBlocks", 1024, 16, 65_536);
            maxSpeedUpgradesPerNode = builder
                    .comment("Maximum speed upgrade cards that stack in one node upgrade slot. Each card adds one scanned slot per tick to the base rate of one.")
                    .defineInRange("maxSpeedUpgradesPerNode", 8, 1, 64);
            skyContainerTransferLimit = builder
                    .comment("Maximum amount moved per direct transfer operation between Sky Logistics vault containers.")
                    .defineInRange("skyContainerTransferLimit", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
            serverOpsPerTick = builder
                    .comment("Maximum endpoint, slot, tank, and energy transfer operations Sky Logistics may process per server tick.")
                    .defineInRange("serverOpsPerTick", 32_768, 1, 1_000_000);
            lineOpsPerTick = builder
                    .comment("Maximum endpoint, slot, tank, and energy transfer operations one logistics line may consume per server tick.")
                    .defineInRange("lineOpsPerTick", 256, 1, 1_000_000);
            endpointTargetAttempts = builder
                    .comment("Maximum receiving endpoints one source endpoint may try after failures for one transfer candidate. A successful transfer still stops immediately.")
                    .defineInRange("endpointTargetAttempts", 4, 1, 1_000_000);
            externalTankScansPerEndpoint = builder
                    .comment("Maximum external fluid tanks one source endpoint may scan per tick. Node operation rate still applies.")
                    .defineInRange("externalTankScansPerEndpoint", 8, 1, 1_000_000);
            sourceSearchAttemptsPerEndpoint = builder
                    .comment("Maximum slot/tank cursor positions one source endpoint may skip while searching for work in one transfer attempt.")
                    .defineInRange("sourceSearchAttemptsPerEndpoint", 64, 1, 1_000_000);
            maxItemSlotLimit = builder
                    .comment("Maximum item slot keep limit configurable on a logistics face. Face value 0 still means unlimited.")
                    .defineInRange("maxItemSlotLimit", 36, 1, 999);
            allowAe2ItemTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer items stored in AE2 networks.")
                    .define("allowAe2ItemTransfer", true);
            allowSophisticatedStorageStackUpgradeTransfer = builder
                    .comment("Whether transfers treat a Sophisticated Storage stack-upgraded slot as one transportable slot.")
                    .define("allowSophisticatedStorageStackUpgradeTransfer", true);
            allowAe2FluidTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer fluids stored in AE2 networks.")
                    .define("allowAe2FluidTransfer", true);
            allowRefinedStorageItemTransfer = builder
                    .comment("Whether Sky RS Interfaces may transfer items stored in Refined Storage networks.")
                    .define("allowRefinedStorageItemTransfer", true);
            allowRefinedStorageFluidTransfer = builder
                    .comment("Whether Sky RS Interfaces may transfer fluids stored in Refined Storage networks.")
                    .define("allowRefinedStorageFluidTransfer", true);
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
            allowBeyondDimensionsItemTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer items stored in Beyond Dimensions networks.")
                    .define("allowBeyondDimensionsItemTransfer", true);
            allowBeyondDimensionsFluidTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer fluids stored in Beyond Dimensions networks.")
                    .define("allowBeyondDimensionsFluidTransfer", true);
            allowBeyondDimensionsEnergyTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer FE stored in Beyond Dimensions networks.")
                    .define("allowBeyondDimensionsEnergyTransfer", true);
            allowBeyondDimensionsMekanismChemicalTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer Mekanism chemicals stored in Beyond Dimensions networks.")
                    .define("allowBeyondDimensionsMekanismChemicalTransfer", true);
            allowBeyondDimensionsManaTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer Botania mana stored in Beyond Dimensions networks.")
                    .define("allowBeyondDimensionsManaTransfer", true);
            allowBeyondDimensionsSourceTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer Ars Nouveau source stored in Beyond Dimensions networks.")
                    .define("allowBeyondDimensionsSourceTransfer", true);
            allowAe2AppliedBotanicsManaTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer Applied Botanics mana stored in AE2 networks.")
                    .define("allowAe2AppliedBotanicsManaTransfer", true);
            allowAe2ArsEnergistiqueSourceTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer Ars Energistique source stored in AE2 networks.")
                    .define("allowAe2ArsEnergistiqueSourceTransfer", true);
            preferredItemSlotCacheSize = builder
                    .comment("Number of successful item source slots remembered as hot slots per source endpoint.")
                    .defineInRange("preferredItemSlotCacheSize", 9, 1, 256);
            targetItemInsertionCursorCount = builder
                    .comment("Target item insertion cursor lanes per multi-slot endpoint. Single-slot targets bypass cursors; the active count never exceeds the target slot count; 0 disables cursor-based slot ordering.")
                    .defineInRange("targetItemInsertionCursorCount", 9, 0, 64);
            rejectedAcceptCacheSize = builder
                    .comment("Maximum recent item, fluid, or chemical accept-reject records remembered per receiving endpoint.")
                    .defineInRange("rejectedAcceptCacheSize", 9, 1, 64);
            transferRetryFirstTicks = builder
                    .comment("Ticks to wait after the first failed transfer attempt. Shared by sending endpoint failures and receiving endpoint accept-reject retries.")
                    .defineInRange("transferRetryFirstTicks", 5, 1, 1200);
            transferRetrySecondTicks = builder
                    .comment("Ticks to wait after the second consecutive failed transfer attempt.")
                    .defineInRange("transferRetrySecondTicks", 10, 1, 1200);
            transferRetryThirdTicks = builder
                    .comment("Ticks to wait after the third consecutive failed transfer attempt.")
                    .defineInRange("transferRetryThirdTicks", 20, 1, 1200);
            transferRetryMaxTicks = builder
                    .comment("Ticks to wait after the fourth and later consecutive failed transfer attempts.")
                    .defineInRange("transferRetryMaxTicks", 40, 1, 1200);
            builder.pop();

            builder.push("distributor");
            enableDistributorItems = builder
                    .comment("Whether Celestial Distributors proxy and distribute item storage.")
                    .define("enableItems", true);
            enableDistributorFluids = builder
                    .comment("Whether Celestial Distributors proxy and distribute fluid storage.")
                    .define("enableFluids", true);
            enableDistributorEnergy = builder
                    .comment("Whether Celestial Distributors proxy and distribute energy storage.")
                    .define("enableEnergy", true);
            distributorMaxTargets = builder
                    .comment("Maximum adjacent container targets discovered by one Celestial Distributor. Higher values increase scan and proxy costs.")
                    .defineInRange("maxTargets", 16, 1, 64);
            distributorScanOpsPerTick = builder
                    .comment("Maximum BFS positions one Celestial Distributor may inspect per server tick. This budget is independent from transfer operations.")
                    .defineInRange("scanOpsPerTick", 16, 1, 4096);
            distributorOpsPerTick = builder
                    .comment("Maximum transfer probes one Celestial Distributor may perform per server tick. Each directly accessed item slot, tank, or resource target costs one probe; item insertion combines a target and its first slot. BFS discovery uses scanOpsPerTick instead.")
                    .defineInRange("opsPerTick", 64, 1, 4096);
            builder.pop();

            builder.push("necklaces");
            skyNecklaceTickInterval = builder
                    .comment("Server ticks between Sky Necklace work scans. Higher values reduce player inventory and backpack scanning frequency.")
                    .defineInRange("skyNecklaceTickInterval", 10, 1, 1200);
            skyNecklaceSlotScansPerTick = builder
                    .comment("Maximum inventory, backpack, or network item slots one Sky Necklace may scan each work tick.")
                    .defineInRange("skyNecklaceSlotScansPerTick", 64, 1, 1_000_000);
            skyNecklaceTargetAttemptsPerWork = builder
                    .comment("Maximum logistics endpoints one Sky Necklace may visit during one work interval (outputs while extracting, inputs while inserting).")
                    .defineInRange("skyNecklaceTargetAttemptsPerWork", 1, 1, 1_000_000);
            builder.pop();

            builder.push("rituals");
            skyRitualMinY = builder
                    .comment("Minimum block Y for Eulogia Crystals to charge and sky offering altars to work.")
                    .defineInRange("skyRitualMinY", 128, -64, 320);
            eulogiaCrystalChargeSeconds = builder
                    .comment("Seconds an uncharged Eulogia Crystal must spend at or above skyRitualMinY before it becomes charged. One second is 20 ticks.")
                    .defineInRange("eulogiaCrystalChargeSeconds", 20, 1, 3600);
            builder.pop();
        }

        private int transferRetryDelayTicks(int failures) {
            if (failures <= 1) {
                return transferRetryFirstTicks.get();
            }
            if (failures == 2) {
                return transferRetrySecondTicks.get();
            }
            if (failures == 3) {
                return transferRetryThirdTicks.get();
            }
            return transferRetryMaxTicks.get();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue renderConfiguratorPlayerHeads;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("gui");
            renderConfiguratorPlayerHeads = builder
                    .comment("Whether the configurator line details render active Sky Necklaces as player heads.")
                    .define("renderConfiguratorPlayerHeads", true);
            builder.pop();
        }
    }

    private SkyLogisticsConfig() {
    }
}
