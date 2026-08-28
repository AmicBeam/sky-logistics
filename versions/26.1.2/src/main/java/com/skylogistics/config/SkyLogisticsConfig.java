package com.skylogistics.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.Config;
import com.skylogistics.compat.astages.StageRateRules;
import com.skylogistics.compat.astages.StageTransferRates;
import com.skylogistics.compat.astages.TransferRates;
import com.skylogistics.compat.astages.TransferResource;
import com.skylogistics.compat.advancements.AdvancementDisplayEntry;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class SkyLogisticsConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    private static List<? extends Object> cachedAStagesEntries;
    private static TransferRates cachedAStagesInitial;
    private static StageRateRules cachedAStagesRules;
    private static List<? extends Object> cachedAdvancementEntries;
    private static TransferRates cachedAdvancementInitial;
    private static StageRateRules cachedAdvancementRules;

    static {
        ModConfigSpec.Builder serverBuilder = new ModConfigSpec.Builder();
        SERVER = new Server(serverBuilder);
        SERVER_SPEC = serverBuilder.build();

        ModConfigSpec.Builder clientBuilder = new ModConfigSpec.Builder();
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

    public static boolean orderedMatchingWrapTargets() {
        return SERVER.orderedMatchingUpgrade.wrapTargets.get();
    }

    public static boolean orderedMatchingContinueAfterTargetFailure() {
        return SERVER.orderedMatchingUpgrade.continueAfterTargetFailure.get();
    }

    public static int orderedMatchingPerItemDetentionQueueLength() {
        return SERVER.orderedMatchingUpgrade.perItemDetentionQueueLength.get();
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
                    Long rate = configuredAdvancementRate(entry.get(resource.configKey()));
                    if (rate != null) rates.put(resource, rate);
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
            if (configuredAdvancementRate(entry.get(key)) == null) return false;
        }
        return true;
    }

    private static Long configuredAdvancementRate(Object value) {
        if (value instanceof Number number && number.longValue() >= 1L) return number.longValue();
        if (value instanceof String text && "unlimited".equalsIgnoreCase(text.trim())) return Long.MAX_VALUE;
        return null;
    }

    private static List<? extends Object> defaultAdvancementRates() {
        return List.of(
                advancementRate("minecraft:story/smelt_iron", "minecraft:iron_ingot",
                        "advancements.story.smelt_iron.title", "task", 16L, 2_500L, 2_500L, 25_000L, 13L, 13L),
                advancementRate("minecraft:story/mine_diamond", "minecraft:diamond",
                        "advancements.story.mine_diamond.title", "task", 64L, 10_000L, 10_000L, 100_000L, 50L, 50L),
                advancementRate("minecraft:story/enchant_item", "minecraft:enchanting_table",
                        "advancements.story.enchant_item.title", "task", 1_024L, 160_000L, 160_000L,
                        1_600_000L, 800L, 800L),
                advancementRate("minecraft:adventure/trade_at_world_height", "minecraft:emerald",
                        "advancements.adventure.trade_at_world_height.title", "task", 32_768L, 5_120_000L,
                        5_120_000L, 51_200_000L, 25_600L, 25_600L),
                advancementRate("minecraft:nether/create_beacon", "minecraft:beacon",
                        "advancements.nether.create_beacon.title", "task", 1_048_576L, 163_840_000L,
                        163_840_000L, 1_638_400_000L, 819_200L, 819_200L),
                advancementRate("minecraft:nether/create_full_beacon", "minecraft:beacon",
                        "advancements.nether.create_full_beacon.title", "goal", 2_147_483_647L,
                        2_147_483_647L, 2_147_483_647L, 2_147_483_647L, 2_147_483_647L,
                        2_147_483_647L),
                advancementRate("minecraft:end/elytra", "minecraft:elytra", "advancements.end.elytra.title",
                        "goal", "unlimited", "unlimited", "unlimited", "unlimited", "unlimited", "unlimited"));
    }

    private static Config advancementRate(String advancement, String icon, String title, String frame,
            Object items, Object fluids,
            Object chemicals, Object energy, Object mana, Object source) {
        Config entry = Config.inMemory();
        entry.set("advancement", advancement);
        entry.set("icon", icon);
        entry.set("title", title);
        entry.set("frame", frame);
        entry.set("items", items);
        entry.set("fluids", fluids);
        entry.set("chemicals", chemicals);
        entry.set("energy", energy);
        entry.set("mana", mana);
        entry.set("source", source);
        return entry;
    }

    public static boolean enableAdvancementTransferRates() {
        return SERVER.enableAdvancementTransferRates.get();
    }

    public static synchronized StageRateRules advancementTransferRateRules() {
        TransferRates initial = new TransferRates(SERVER.advancementInitialItems.get(),
                SERVER.advancementInitialFluids.get(), SERVER.advancementInitialChemicals.get(),
                SERVER.advancementInitialEnergy.get(), SERVER.advancementInitialMana.get(),
                SERVER.advancementInitialSource.get());
        List<? extends Object> entries = SERVER.advancementRates.get();
        if (cachedAdvancementRules == null || cachedAdvancementEntries != entries
                || !initial.equals(cachedAdvancementInitial)) {
            Map<String, StageTransferRates> advancements = new HashMap<>();
            for (Object value : entries) {
                Map<String, ?> entry = advancementEntry(value);
                String advancement = (String) entry.get("advancement");
                EnumMap<TransferResource, Long> rates = new EnumMap<>(TransferResource.class);
                for (TransferResource resource : TransferResource.values()) {
                    Long rate = configuredAdvancementRate(entry.get(resource.configKey()));
                    if (rate != null) rates.put(resource, rate);
                }
                StageTransferRates parsed = new StageTransferRates(rates);
                advancements.merge(advancement, parsed, StageTransferRates::mergeMax);
            }
            cachedAdvancementEntries = entries;
            cachedAdvancementInitial = initial;
            cachedAdvancementRules = new StageRateRules(initial, advancements);
        }
        return cachedAdvancementRules;
    }

    public static List<AdvancementDisplayEntry> advancementDisplayEntries() {
        TransferRates current = advancementTransferRateRules().initial();
        List<AdvancementDisplayEntry> result = new java.util.ArrayList<>();
        for (Object value : SERVER.advancementRates.get()) {
            Map<String, ?> entry = advancementEntry(value);
            if (entry == null || !(entry.get("advancement") instanceof String advancement)) continue;
            EnumMap<TransferResource, Long> rates = new EnumMap<>(TransferResource.class);
            for (TransferResource resource : TransferResource.values()) {
                Long rate = configuredAdvancementRate(entry.get(resource.configKey()));
                if (rate != null) rates.put(resource, rate);
            }
            current = current.max(new StageTransferRates(rates));
            result.add(new AdvancementDisplayEntry(advancement,
                    stringValue(entry.get("icon"), "minecraft:knowledge_book"),
                    stringValue(entry.get("title"), advancement),
                    stringValue(entry.get("frame"), "task"), current));
        }
        return List.copyOf(result);
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static boolean validAdvancementRateEntry(Object value) {
        Map<String, ?> entry = advancementEntry(value);
        if (entry == null) return false;
        Object advancement = entry.get("advancement");
        return advancement instanceof String text && !text.isBlank();
    }

    private static boolean validAdvancementRateValue(Object value) {
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> advancementEntry(Object value) {
        if (value instanceof UnmodifiableConfig config) return config.valueMap();
        if (value instanceof Map<?, ?> map && map.keySet().stream().allMatch(String.class::isInstance)) {
            return (Map<String, ?>) map;
        }
        return null;
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
    public static boolean enableDistributorAdaptiveItemTargetProbes() { return SERVER.enableDistributorAdaptiveItemTargetProbes.get(); }
    public static boolean enableDistributorAdaptiveFluidTargetProbes() { return SERVER.enableDistributorAdaptiveFluidTargetProbes.get(); }
    public static boolean enableDistributorAdaptiveChemicalTargetProbes() { return SERVER.enableDistributorAdaptiveChemicalTargetProbes.get(); }
    public static int distributorItemRouteCacheSize() { return SERVER.distributorItemRouteCacheSize.get(); }
    public static int distributorItemTargetHotProbeTicks() { return SERVER.distributorItemTargetHotProbeTicks.get(); }
    public static int distributorItemTargetWarmProbeTicks() { return SERVER.distributorItemTargetWarmProbeTicks.get(); }
    public static int distributorItemTargetCoolProbeTicks() { return SERVER.distributorItemTargetCoolProbeTicks.get(); }
    public static int distributorItemTargetFallbackProbeTicks() { return SERVER.distributorItemTargetFallbackProbeTicks.get(); }
    public static int distributorItemTargetMissesPerDemotion() { return SERVER.distributorItemTargetMissesPerDemotion.get(); }

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

    public static boolean forceExtractionDeviceModAllowed(String modId) {
        return SERVER.forceExtractionDeviceModIdWhitelist.get().contains(modId);
    }

    private static boolean validModId(Object value) {
        return value instanceof String text && text.matches("[a-z0-9_.-]+");
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

    public static boolean enableMaintainedItemHotSlotPolling() {
        return SERVER.enableMaintainedItemHotSlotPolling.get();
    }

    public static int maintainedItemHotSlotPollTicks() {
        return SERVER.maintainedItemHotSlotPollTicks.get();
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

    public static int eulogiaCompanionStoneChargeSeconds() {
        return SERVER.eulogiaCompanionStoneChargeSeconds.get();
    }

    public static boolean renderConfiguratorPlayerHeads() {
        return CLIENT.renderConfiguratorPlayerHeads.get();
    }

    public static final class Server {
        public final ModConfigSpec.IntValue maxVaultTypes;
        public final ModConfigSpec.IntValue maxVaultItemEntryNbtBytes;
        public final ModConfigSpec.IntValue maxVaultFluidEntryNbtBytes;
        public final ModConfigSpec.IntValue nodeItemTransferLimit;
        public final ModConfigSpec.IntValue nodeEnergyTransferLimit;
        public final ModConfigSpec.BooleanValue enableAStagesTransferRates;
        public final ModConfigSpec.LongValue aStagesInitialItems;
        public final ModConfigSpec.LongValue aStagesInitialFluids;
        public final ModConfigSpec.LongValue aStagesInitialChemicals;
        public final ModConfigSpec.LongValue aStagesInitialEnergy;
        public final ModConfigSpec.LongValue aStagesInitialMana;
        public final ModConfigSpec.LongValue aStagesInitialSource;
        public final ModConfigSpec.ConfigValue<List<? extends Object>> aStagesStageRates;
        public final ModConfigSpec.BooleanValue enableAdvancementTransferRates;
        public final ModConfigSpec.LongValue advancementInitialItems;
        public final ModConfigSpec.LongValue advancementInitialFluids;
        public final ModConfigSpec.LongValue advancementInitialChemicals;
        public final ModConfigSpec.LongValue advancementInitialEnergy;
        public final ModConfigSpec.LongValue advancementInitialMana;
        public final ModConfigSpec.LongValue advancementInitialSource;
        public final ModConfigSpec.ConfigValue<List<? extends Object>> advancementRates;
        public final ModConfigSpec.IntValue serverOpsPerTick;
        public final ModConfigSpec.IntValue lineOpsPerTick;
        public final ModConfigSpec.IntValue endpointTargetAttempts;
        public final ModConfigSpec.IntValue externalTankScansPerEndpoint;
        public final ModConfigSpec.IntValue sourceSearchAttemptsPerEndpoint;
        public final ModConfigSpec.IntValue maxItemSlotLimit;
        public final ModConfigSpec.IntValue preferredItemSlotCacheSize;
        public final ModConfigSpec.IntValue targetItemInsertionCursorCount;
        public final ModConfigSpec.IntValue rejectedAcceptCacheSize;
        public final ModConfigSpec.IntValue transferRetryFirstTicks;
        public final ModConfigSpec.IntValue transferRetrySecondTicks;
        public final ModConfigSpec.IntValue transferRetryThirdTicks;
        public final ModConfigSpec.IntValue transferRetryMaxTicks;
        public final ModConfigSpec.BooleanValue enableMaintainedItemHotSlotPolling;
        public final ModConfigSpec.IntValue maintainedItemHotSlotPollTicks;
        public final ModConfigSpec.IntValue skyNecklaceTickInterval;
        public final ModConfigSpec.IntValue skyNecklaceSlotScansPerTick;
        public final ModConfigSpec.IntValue skyNecklaceTargetAttemptsPerWork;
        public final ModConfigSpec.IntValue skyRitualMinY;
        public final ModConfigSpec.IntValue eulogiaCrystalChargeSeconds;
        public final ModConfigSpec.IntValue eulogiaCompanionStoneChargeSeconds;
        public final ModConfigSpec.LongValue skyContainerTransferLimit;
        public final ModConfigSpec.BooleanValue allowAe2ItemTransfer;
        public final ModConfigSpec.BooleanValue allowSophisticatedStorageStackUpgradeTransfer;
        public final ModConfigSpec.ConfigValue<List<? extends Object>> forceExtractionDeviceModIdWhitelist;
        public final ModConfigSpec.BooleanValue allowAe2FluidTransfer;
        public final ModConfigSpec.BooleanValue allowRefinedStorageItemTransfer;
        public final ModConfigSpec.BooleanValue allowRefinedStorageFluidTransfer;
        public final ModConfigSpec.BooleanValue allowFluidChemicalTransfer;
        public final ModConfigSpec.BooleanValue allowEnergyManaTransfer;
        public final ModConfigSpec.BooleanValue allowEnergySourceTransfer;
        public final ModConfigSpec.BooleanValue allowAe2AppFluxEnergyTransfer;
        public final ModConfigSpec.BooleanValue allowAe2AppliedMekanisticsChemicalTransfer;
        public final ModConfigSpec.BooleanValue allowBeyondDimensionsItemTransfer;
        public final ModConfigSpec.BooleanValue allowBeyondDimensionsFluidTransfer;
        public final ModConfigSpec.BooleanValue allowBeyondDimensionsEnergyTransfer;
        public final ModConfigSpec.BooleanValue allowBeyondDimensionsMekanismChemicalTransfer;
        public final ModConfigSpec.BooleanValue allowBeyondDimensionsSourceTransfer;
        public final ModConfigSpec.BooleanValue allowAe2AppliedBotanicsManaTransfer;
        public final ModConfigSpec.BooleanValue allowAe2ArsEnergistiqueSourceTransfer;
        public final ModConfigSpec.BooleanValue enableSimpleItemPipe;
        public final ModConfigSpec.BooleanValue enableSimpleFluidPipe;
        public final ModConfigSpec.BooleanValue enableSimpleEnergyPipe;
        public final ModConfigSpec.BooleanValue enableDistributorItems;
        public final ModConfigSpec.BooleanValue enableDistributorFluids;
        public final ModConfigSpec.BooleanValue enableDistributorEnergy;
        public final ModConfigSpec.IntValue distributorMaxTargets;
        public final ModConfigSpec.IntValue distributorScanOpsPerTick;
        public final ModConfigSpec.IntValue distributorOpsPerTick;
        public final ModConfigSpec.BooleanValue enableDistributorAdaptiveItemTargetProbes;
        public final ModConfigSpec.BooleanValue enableDistributorAdaptiveFluidTargetProbes;
        public final ModConfigSpec.BooleanValue enableDistributorAdaptiveChemicalTargetProbes;
        public final ModConfigSpec.IntValue distributorItemRouteCacheSize;
        public final ModConfigSpec.IntValue distributorItemTargetHotProbeTicks;
        public final ModConfigSpec.IntValue distributorItemTargetWarmProbeTicks;
        public final ModConfigSpec.IntValue distributorItemTargetCoolProbeTicks;
        public final ModConfigSpec.IntValue distributorItemTargetFallbackProbeTicks;
        public final ModConfigSpec.IntValue distributorItemTargetMissesPerDemotion;
        public final ModConfigSpec.IntValue simpleItemPipeTransferRate;
        public final ModConfigSpec.IntValue simpleFluidPipeTransferRate;
        public final ModConfigSpec.IntValue simpleEnergyPipeTransferRate;
        public final ModConfigSpec.IntValue simpleChemicalPipeTransferRate;
        public final ModConfigSpec.IntValue simpleManaPipeTransferRate;
        public final ModConfigSpec.IntValue simpleSourcePipeTransferRate;
        public final ModConfigSpec.BooleanValue enforceSimplePipeConnectionLimit;
        public final ModConfigSpec.IntValue simplePipeMaxConnectedBlocks;
        public final ModConfigSpec.IntValue maxSpeedUpgradesPerNode;
        public final OrderedMatchingUpgrade orderedMatchingUpgrade;

        private Server(ModConfigSpec.Builder builder) {
            builder.push("vaults");
            maxVaultTypes = builder
                    .comment("Maximum item/fluid type slots a Celestial Vault can be expanded to with capacity nectar.",
                            "使用容量甘露扩容后，天穹仓库可拥有的最大物品/流体类型槽位数。")
                    .defineInRange("maxVaultTypes", 36, 1, 63);
            maxVaultItemEntryNbtBytes = builder
                    .comment("Maximum serialized NBT bytes allowed for one distinct item stored in an item vault.",
                            "物品库中单种物品允许的最大序列化 NBT 字节数。")
                    .defineInRange("maxVaultItemEntryNbtBytes", 8192, 128, 1_048_576);
            maxVaultFluidEntryNbtBytes = builder
                    .comment("Maximum serialized NBT bytes allowed for one distinct fluid stored in a fluid vault.",
                            "流体库中单种流体允许的最大序列化 NBT 字节数。")
                    .defineInRange("maxVaultFluidEntryNbtBytes", 4096, 128, 1_048_576);
            builder.pop();

            builder.push("transfers");
            nodeItemTransferLimit = builder
                    .comment("Maximum item count moved by a logistics node per normal item transfer operation.",
                            "物流节点每次普通物品传输操作可搬运的最大物品数量。")
                    .defineInRange("nodeItemTransferLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
            nodeEnergyTransferLimit = builder
                    .comment("Maximum energy moved by a logistics node per energy transfer operation.",
                            "物流节点每次能量传输操作可搬运的最大能量。")
                    .defineInRange("nodeEnergyTransferLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
            builder.push("astages");
            enableAStagesTransferRates = builder
                    .comment("Whether AStages player stages limit and unlock per-operation transfer amounts. Requires AStages 2.x.",
                            "是否由 AStages 玩家阶段限制并解锁单次操作传输量。需要 AStages 2.x。")
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
                            "AStages 解锁条目。每项必须包含 stage，并可定义 items、fluids、chemicals、energy、mana、source 中的任意字段。",
                            "Example / 示例: [{ stage = \"logistics_tier_1\", items = 128, fluids = 20000 }]")
                    .defineListAllowEmpty("stageRates", List.of(), SkyLogisticsConfig::validAStagesStageRateEntry);
            builder.pop();
            builder.push("advancements");
            enableAdvancementTransferRates = builder
                    .comment("Whether vanilla advancements completed by the line owner limit and unlock per-operation transfer amounts.",
                            "是否由线路持有者完成的原版进度限制并解锁单次操作传输量。")
                    .define("enabled", true);
            builder.push("initialRates");
            advancementInitialItems = builder.defineInRange("items", 4L, 1L, Long.MAX_VALUE);
            advancementInitialFluids = builder.defineInRange("fluids", 625L, 1L, Long.MAX_VALUE);
            advancementInitialChemicals = builder.defineInRange("chemicals", 625L, 1L, Long.MAX_VALUE);
            advancementInitialEnergy = builder.defineInRange("energy", 6_250L, 1L, Long.MAX_VALUE);
            advancementInitialMana = builder.defineInRange("mana", 3L, 1L, Long.MAX_VALUE);
            advancementInitialSource = builder.defineInRange("source", 3L, 1L, Long.MAX_VALUE);
            builder.pop();
            advancementRates = builder
                    .comment("Vanilla advancement unlock entries. Resource rates accept positive integers or the string \"unlimited\".",
                            "原版进度解锁条目。资源速率可填写正整数或字符串 \"unlimited\"（无限制）。")
                    .defineListAllowEmpty("advancementRates", defaultAdvancementRates(),
                            SkyLogisticsConfig::validAdvancementRateValue);
            builder.pop();
            builder.comment("Simple pipe enable switches, transfer rates, and connection limits.",
                            "简易管道的启用开关、传输速率与连接上限。")
                    .push("simplePipes");
            enableSimpleItemPipe = builder
                    .comment("Whether simple item pipes connect to inventories and transfer items.",
                            "简易物品管道是否连接物品容器并传输物品。")
                    .define("enableSimpleItemPipe", true);
            enableSimpleFluidPipe = builder
                    .comment("Whether simple fluid pipes connect to tanks and transfer fluids.",
                            "简易流体管道是否连接储罐并传输流体。")
                    .define("enableSimpleFluidPipe", true);
            enableSimpleEnergyPipe = builder
                    .comment("Whether simple energy pipes connect to FE storages and transfer energy.",
                            "简易能量管道是否连接 FE 储能设备并传输能量。")
                    .define("enableSimpleEnergyPipe", true);
            simpleItemPipeTransferRate = builder
                    .comment("Maximum items moved by each extracting simple item pipe per tick. A transfer still uses at most one source slot and one target slot.",
                            "每个抽取型简易物品管道每 tick 最多搬运的物品数；一次传输仍最多使用一个来源槽和一个目标槽。")
                    .defineInRange("simpleItemPipeTransferRate", 64, 1, Integer.MAX_VALUE);
            simpleFluidPipeTransferRate = builder
                    .comment("Maximum fluid amount in mB moved by each extracting simple fluid pipe per tick.",
                            "每个抽取型简易流体管道每 tick 最多搬运的流体量（mB）。")
                    .defineInRange("simpleFluidPipeTransferRate", 10_000, 1, Integer.MAX_VALUE);
            simpleEnergyPipeTransferRate = builder
                    .comment("Maximum FE moved by each extracting simple energy pipe per tick.",
                            "每个抽取型简易能量管道每 tick 最多搬运的 FE。")
                    .defineInRange("simpleEnergyPipeTransferRate", 100_000, 1, Integer.MAX_VALUE);
            simpleChemicalPipeTransferRate = builder
                    .comment("Maximum Mekanism chemical amount moved by each extracting simple fluid pipe per tick.",
                            "每个抽取型简易流体管道每 tick 最多搬运的 Mekanism 化学品数量。")
                    .defineInRange("simpleChemicalPipeTransferRate", 10_000, 1, Integer.MAX_VALUE);
            simpleManaPipeTransferRate = builder
                    .comment("Maximum Botania mana moved by each extracting simple energy pipe per tick.",
                            "每个抽取型简易能量管道每 tick 最多搬运的 Botania 魔力。")
                    .defineInRange("simpleManaPipeTransferRate", 50, 1, Integer.MAX_VALUE);
            simpleSourcePipeTransferRate = builder
                    .comment("Maximum Ars Nouveau source moved by each extracting simple energy pipe per tick.",
                            "每个抽取型简易能量管道每 tick 最多搬运的 Ars Nouveau 源质。")
                    .defineInRange("simpleSourcePipeTransferRate", 50, 1, Integer.MAX_VALUE);
            enforceSimplePipeConnectionLimit = builder
                    .comment("Whether new simple pipe connections are rejected when they would exceed simplePipeMaxConnectedBlocks.",
                            "新建简易管道连接超过 simplePipeMaxConnectedBlocks 时，是否拒绝该连接。")
                    .define("enforceSimplePipeConnectionLimit", true);
            simplePipeMaxConnectedBlocks = builder
                    .comment("Maximum connected blocks in one simple pipe line. New pipe edges that would exceed this limit stay disconnected.",
                            "单条简易管道线路允许连接的最大方块数；超过上限的新连接会保持断开。")
                    .defineInRange("simplePipeMaxConnectedBlocks", 1024, 16, 65_536);
            builder.pop();
            maxSpeedUpgradesPerNode = builder
                    .comment("Maximum speed upgrade cards that stack in one node upgrade slot. Each card adds one scanned slot per tick to the base rate of one. It is recommended to set preferredItemSlotCacheSize to at least this value plus one.",
                            "单个节点升级槽内可堆叠的速度升级卡上限；基础速率为每 tick 1 槽，每张卡额外增加 1 槽。建议 preferredItemSlotCacheSize 的配置值至少为此升级数加 1。")
                    .defineInRange("maxSpeedUpgradesPerNode", 8, 1, 64);
            skyContainerTransferLimit = builder
                    .comment("Maximum amount moved per direct transfer operation between Sky Logistics vault containers.",
                            "天穹物流仓库容器之间每次直接传输操作可搬运的最大数量。")
                    .defineInRange("skyContainerTransferLimit", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
            builder.comment("Transfer operation budgets and scan attempt limits.",
                            "传输操作预算与扫描尝试上限。")
                    .push("performance");
            serverOpsPerTick = builder
                    .comment("Maximum endpoint, slot, tank, and energy transfer operations Sky Logistics may process per server tick.",
                            "天穹物流每个服务器 tick 可处理的端点、槽位、储罐和能量传输操作总上限。")
                    .defineInRange("serverOpsPerTick", 32_768, 1, 1_000_000);
            lineOpsPerTick = builder
                    .comment("Maximum endpoint, slot, tank, and energy transfer operations one logistics line may consume per server tick.",
                            "单条物流线路每个服务器 tick 可消耗的端点、槽位、储罐和能量传输操作上限。")
                    .defineInRange("lineOpsPerTick", 1_024, 1, 1_000_000);
            endpointTargetAttempts = builder
                    .comment("Maximum receiving endpoints one source endpoint may try after failures for one transfer candidate. A successful transfer still stops immediately.",
                            "一个来源端点针对单个传输候选在失败后最多尝试的接收端点数；成功传输后仍会立即停止。")
                    .defineInRange("endpointTargetAttempts", 4, 1, 1_000_000);
            externalTankScansPerEndpoint = builder
                    .comment("Maximum external fluid tanks one source endpoint may scan per tick. Node operation rate still applies.",
                            "一个来源端点每 tick 最多扫描的外部流体储罐数；仍受节点操作速率限制。")
                    .defineInRange("externalTankScansPerEndpoint", 8, 1, 1_000_000);
            sourceSearchAttemptsPerEndpoint = builder
                    .comment("Maximum slot/tank cursor positions one source endpoint may skip while searching for work in one transfer attempt.",
                            "一个来源端点在单次传输尝试中寻找可执行工作时最多跳过的槽位/储罐游标位置数。")
                    .defineInRange("sourceSearchAttemptsPerEndpoint", 64, 1, 1_000_000);
            builder.pop();
            maxItemSlotLimit = builder
                    .comment("Maximum item slot keep limit configurable on a logistics face. Face value 0 still means unlimited.",
                            "物流面的物品留槽限制可配置的最大值；面配置值 0 仍表示无限制。")
                    .defineInRange("maxItemSlotLimit", 36, 1, 999);
            builder.comment("Third-party storage and resource transfer integrations.",
                            "第三方存储与资源传输联动。")
                    .push("integrations");
            allowAe2ItemTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer items stored in AE2 networks.",
                            "天穹 ME 接口是否可传输 AE2 网络中存储的物品。")
                    .define("allowAe2ItemTransfer", true);
            allowSophisticatedStorageStackUpgradeTransfer = builder
                    .comment("Whether transfers treat a Sophisticated Storage stack-upgraded slot as one transportable slot.",
                            "传输时是否将 Sophisticated Storage 堆叠升级后的槽位视为一个可搬运槽位。")
                    .define("allowSophisticatedStorageStackUpgradeTransfer", true);
            builder.push("forceExtractionUpgrade");
            forceExtractionDeviceModIdWhitelist = builder
                    .comment("Device block mod IDs allowed for force extraction. An empty list disables the upgrade; only matching mod devices enable its behavior.",
                            "允许强制抽取的设备方块 modID 列表。列表为空时禁用该升级；仅匹配列表中模组的设备会启用其功能。")
                    .defineListAllowEmpty("deviceModIdWhitelist", List.of("mekanism_extras"),
                            SkyLogisticsConfig::validModId);
            builder.pop();
            allowAe2FluidTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer fluids stored in AE2 networks.",
                            "天穹 ME 接口是否可传输 AE2 网络中存储的流体。")
                    .define("allowAe2FluidTransfer", true);
            allowRefinedStorageItemTransfer = builder
                    .comment("Whether Sky RS Interfaces may transfer items stored in Refined Storage networks.",
                            "天穹 RS 接口是否可传输 Refined Storage 网络中存储的物品。")
                    .define("allowRefinedStorageItemTransfer", true);
            allowRefinedStorageFluidTransfer = builder
                    .comment("Whether Sky RS Interfaces may transfer fluids stored in Refined Storage networks.",
                            "天穹 RS 接口是否可传输 Refined Storage 网络中存储的流体。")
                    .define("allowRefinedStorageFluidTransfer", true);
            allowFluidChemicalTransfer = builder
                    .comment("Whether fluid-enabled logistics faces may also transfer Mekanism chemicals.",
                            "启用流体的物流面是否也可传输 Mekanism 化学品。")
                    .define("allowFluidChemicalTransfer", true);
            allowEnergyManaTransfer = builder
                    .comment("Whether energy-enabled logistics faces may also transfer Botania mana.",
                            "启用能量的物流面是否也可传输 Botania 魔力。")
                    .define("allowEnergyManaTransfer", true);
            allowEnergySourceTransfer = builder
                    .comment("Whether energy-enabled logistics faces may also transfer Ars Nouveau source.",
                            "启用能量的物流面是否也可传输 Ars Nouveau 源质。")
                    .define("allowEnergySourceTransfer", true);
            allowAe2AppFluxEnergyTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer AppFlux FE stored in AE2 networks.",
                            "天穹 ME 接口是否可传输 AE2 网络中由 AppFlux 存储的 FE。")
                    .define("allowAe2AppFluxEnergyTransfer", true);
            allowAe2AppliedMekanisticsChemicalTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer Applied Mekanistics chemicals stored in AE2 networks.",
                            "天穹 ME 接口是否可传输 AE2 网络中由 Applied Mekanistics 存储的化学品。")
                    .define("allowAe2AppliedMekanisticsChemicalTransfer", true);
            allowBeyondDimensionsItemTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer items stored in Beyond Dimensions networks.",
                            "天穹维度接口是否可传输 Beyond Dimensions 网络中存储的物品。")
                    .define("allowBeyondDimensionsItemTransfer", true);
            allowBeyondDimensionsFluidTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer fluids stored in Beyond Dimensions networks.",
                            "天穹维度接口是否可传输 Beyond Dimensions 网络中存储的流体。")
                    .define("allowBeyondDimensionsFluidTransfer", true);
            allowBeyondDimensionsEnergyTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer FE stored in Beyond Dimensions networks.",
                            "天穹维度接口是否可传输 Beyond Dimensions 网络中存储的 FE。")
                    .define("allowBeyondDimensionsEnergyTransfer", true);
            allowBeyondDimensionsMekanismChemicalTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer Mekanism chemicals stored in Beyond Dimensions networks.",
                            "天穹维度接口是否可传输 Beyond Dimensions 网络中存储的 Mekanism 化学品。")
                    .define("allowBeyondDimensionsMekanismChemicalTransfer", true);
            allowBeyondDimensionsSourceTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer Ars Nouveau source stored in Beyond Dimensions networks.",
                            "天穹维度接口是否可传输 Beyond Dimensions 网络中存储的 Ars Nouveau 源质。")
                    .define("allowBeyondDimensionsSourceTransfer", true);
            allowAe2AppliedBotanicsManaTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer Applied Botanics mana stored in AE2 networks.",
                            "天穹 ME 接口是否可传输 AE2 网络中由 Applied Botanics 存储的魔力。")
                    .define("allowAe2AppliedBotanicsManaTransfer", true);
            allowAe2ArsEnergistiqueSourceTransfer = builder
                    .comment("Whether Sky ME Interfaces may transfer Ars Energistique source stored in AE2 networks.",
                            "天穹 ME 接口是否可传输 AE2 网络中由 Ars Energistique 存储的源质。")
                    .define("allowAe2ArsEnergistiqueSourceTransfer", true);
            builder.pop();
            builder.comment("Transfer retry delays, cursors, and endpoint caches.",
                            "传输重试延迟、游标与端点缓存。")
                    .push("retryAndCaching");
            preferredItemSlotCacheSize = builder
                    .comment("Number of successful item source slots remembered as hot slots per source endpoint.",
                            "每个来源端点记忆为热槽的成功物品来源槽数量。")
                    .defineInRange("preferredItemSlotCacheSize", 9, 1, 256);
            targetItemInsertionCursorCount = builder
                    .comment("Target item insertion cursor lanes per multi-slot endpoint. Single-slot targets bypass cursors; the active count never exceeds the target slot count; 0 disables cursor-based slot ordering.",
                            "每个多槽端点的目标物品插入游标通道数。单槽目标绕过游标；实际数量不超过目标槽数；0 表示禁用基于游标的槽位排序。")
                    .defineInRange("targetItemInsertionCursorCount", 9, 0, 64);
            rejectedAcceptCacheSize = builder
                    .comment("Maximum recent item, fluid, or chemical accept-reject records remembered per receiving endpoint.",
                            "每个接收端点记忆的近期物品、流体或化学品接受/拒绝记录上限。")
                    .defineInRange("rejectedAcceptCacheSize", 9, 1, 64);
            transferRetryFirstTicks = builder
                    .comment("Ticks to wait after the first failed transfer attempt. Shared by sending endpoint failures and receiving endpoint accept-reject retries.",
                            "首次传输尝试失败后的等待 tick 数；发送端点失败与接收端点接受/拒绝重试共用。")
                    .defineInRange("transferRetryFirstTicks", 5, 1, 1200);
            transferRetrySecondTicks = builder
                    .comment("Ticks to wait after the second consecutive failed transfer attempt.",
                            "连续第二次传输尝试失败后的等待 tick 数。")
                    .defineInRange("transferRetrySecondTicks", 10, 1, 1200);
            transferRetryThirdTicks = builder
                    .comment("Ticks to wait after the third consecutive failed transfer attempt.",
                            "连续第三次传输尝试失败后的等待 tick 数。")
                    .defineInRange("transferRetryThirdTicks", 20, 1, 1200);
            transferRetryMaxTicks = builder
                    .comment("Ticks to wait after the fourth and later consecutive failed transfer attempts.",
                            "连续第四次及后续传输尝试失败后的等待 tick 数。")
                    .defineInRange("transferRetryMaxTicks", 40, 1, 1200);
            enableMaintainedItemHotSlotPolling = builder
                    .comment("Whether item faces with a non-zero maintain limit probe their last successful transfer slot between full retry scans.",
                            "物品面维持值非 0 时，是否在完整退避扫描之间探测上次成功传输的槽位。")
                    .define("enableMaintainedItemHotSlotPolling", true);
            maintainedItemHotSlotPollTicks = builder
                    .comment("Ticks between low-cost probes of the last successful slot for maintained item input and output faces. The normal transfer retry remains the full-scan fallback.",
                            "物品输入与输出维持面低成本探测上次成功槽位的间隔 tick；普通传输退避仍作为完整扫描兜底。")
                    .defineInRange("maintainedItemHotSlotPollTicks", 5, 1, 1200);
            builder.pop();
            builder.pop();
            orderedMatchingUpgrade = new OrderedMatchingUpgrade(builder);

            builder.push("distributor");
            enableDistributorItems = builder.comment("Whether Celestial Distributors proxy item storage.",
                    "天穹分配器是否代理物品存储。")
                    .define("enableItems", true);
            enableDistributorFluids = builder.comment("Whether Celestial Distributors proxy fluid storage.",
                    "天穹分配器是否代理流体存储。")
                    .define("enableFluids", true);
            enableDistributorEnergy = builder.comment("Whether Celestial Distributors proxy energy storage.",
                    "天穹分配器是否代理能量存储。")
                    .define("enableEnergy", true);
            distributorMaxTargets = builder
                    .comment("Maximum adjacent container targets discovered by one Celestial Distributor. Higher values increase scan and proxy costs.",
                            "单个天穹分配器最多发现的相邻容器目标数；数值越高，扫描和代理开销越大。")
                    .defineInRange("maxTargets", 32, 1, 64);
            distributorScanOpsPerTick = builder
                    .comment("Maximum BFS positions one Celestial Distributor may inspect per server tick. This budget is independent from transfer operations.",
                            "单个天穹分配器每个服务器 tick 最多检查的 BFS 位置数；该预算独立于传输操作。")
                    .defineInRange("scanOpsPerTick", 16, 1, 4096);
            distributorOpsPerTick = builder
                    .comment("Maximum transfer probes one Celestial Distributor may perform per server tick. Each directly accessed item slot, tank, or resource target costs one probe; item insertion combines a target and its first slot. BFS discovery uses scanOpsPerTick instead.",
                            "单个天穹分配器每个服务器 tick 最多执行的传输探测数。每个直接访问的物品槽、储罐或资源目标消耗一次；物品插入将目标及其首槽合并计数。BFS 发现改用 scanOpsPerTick。")
                    .defineInRange("opsPerTick", 64, 1, 4096);
            enableDistributorAdaptiveItemTargetProbes = builder
                    .comment("Whether distributor item extraction and insertion use independent adaptive per-machine routing tiers.",
                            "分配器物品抽取与插入是否使用独立的按机器自适应路由等级。")
                    .define("enableAdaptiveItemTargetProbes", true);
            enableDistributorAdaptiveFluidTargetProbes = builder
                    .comment("Whether distributor fluid extraction and insertion use adaptive per-machine routing tiers.",
                            "分配器流体抽取与插入是否使用按机器自适应路由等级。")
                    .define("enableAdaptiveFluidTargetProbes", true);
            enableDistributorAdaptiveChemicalTargetProbes = builder
                    .comment("Whether distributor chemical extraction and insertion use adaptive per-machine routing tiers.",
                            "分配器化学品抽取与插入是否使用按机器自适应路由等级。")
                    .define("enableAdaptiveChemicalTargetProbes", true);
            distributorItemRouteCacheSize = builder
                    .comment("Maximum exact resource keys retained per item, fluid, or chemical route bank on each distributor face. The legacy key name is retained for config compatibility.",
                            "分配器每个面、每种物品/流体/化学品路由库保留的精确资源 key 数量上限；为兼容旧配置保留原字段名。")
                    .defineInRange("itemRouteCacheSize", 64, 1, 256);
            distributorItemTargetHotProbeTicks = builder
                    .comment("Retry interval for hot distributor resource machines after successful extraction or keyed insertion.",
                            "物品、流体或化学品成功抽取或按 key 插入后的热机器重试间隔 tick。")
                    .defineInRange("itemTargetHotProbeTicks", 1, 1, 1200);
            distributorItemTargetWarmProbeTicks = builder
                    .comment("Retry interval for warm distributor machine routes after repeated misses.",
                            "分配器机器路由连续失败后进入温热等级时的重试间隔 tick。")
                    .defineInRange("itemTargetWarmProbeTicks", 5, 1, 1200);
            distributorItemTargetCoolProbeTicks = builder
                    .comment("Retry interval for cool distributor machine routes after further misses.",
                            "分配器机器路由进一步连续失败后进入低频等级时的重试间隔 tick。")
                    .defineInRange("itemTargetCoolProbeTicks", 20, 1, 1200);
            distributorItemTargetFallbackProbeTicks = builder
                    .comment("Fallback interval for cold distributor machine routes. Extraction probes are staggered across this window.",
                            "冷分配器机器路由的兜底间隔 tick；抽取首次探测会在该窗口内错峰安排。")
                    .defineInRange("itemTargetFallbackProbeTicks", 40, 1, 1200);
            distributorItemTargetMissesPerDemotion = builder
                    .comment("Consecutive empty extraction or rejected insertion probes required to demote an item, fluid, or chemical target by one tier.",
                            "物品、流体或化学品目标每次降低一个抽取或插入探测等级所需的连续失败次数。")
                    .defineInRange("itemTargetMissesPerDemotion", 3, 1, 64);
            builder.pop();

            builder.push("necklaces");
            skyNecklaceTickInterval = builder
                    .comment("Server ticks between Sky Necklace work scans. Higher values reduce player inventory and backpack scanning frequency.",
                            "天穹项链两次工作扫描之间的服务器 tick 数；数值越高，扫描玩家背包和附属背包的频率越低。")
                    .defineInRange("skyNecklaceTickInterval", 10, 1, 1200);
            skyNecklaceSlotScansPerTick = builder
                    .comment("Maximum inventory, backpack, or network item slots one Sky Necklace may scan each work tick.",
                            "一条天穹项链每次工作最多扫描的物品栏、背包或网络物品槽数量。")
                    .defineInRange("skyNecklaceSlotScansPerTick", 64, 1, 1_000_000);
            skyNecklaceTargetAttemptsPerWork = builder
                    .comment("Maximum logistics endpoints one Sky Necklace may visit during one work interval (outputs while extracting, inputs while inserting).",
                            "一条天穹项链每次工作最多访问的物流端点数（抽取时访问输出端，插入时访问输入端）。")
                    .defineInRange("skyNecklaceTargetAttemptsPerWork", 1, 1, 1_000_000);
            builder.pop();

            builder.push("rituals");
            skyRitualMinY = builder
                    .comment("Minimum block Y for Eulogia chargeable items to charge and sky offering altars to work.",
                            "尤洛伽水晶与配石充能及天穹供奉祭坛工作的最低方块 Y 坐标。")
                    .defineInRange("skyRitualMinY", 96, -64, 320);
            eulogiaCrystalChargeSeconds = builder
                    .comment("Seconds an uncharged Eulogia Crystal must spend at or above skyRitualMinY before it becomes charged. One second is 20 ticks.",
                            "未充能尤洛伽水晶在 skyRitualMinY 或更高处完成充能所需的秒数；1 秒为 20 tick。")
                    .defineInRange("eulogiaCrystalChargeSeconds", 20, 1, 3600);
            eulogiaCompanionStoneChargeSeconds = builder
                    .comment("Seconds an uncharged Eulogia Companion Stone must spend at or above skyRitualMinY before it becomes charged. One second is 20 ticks.",
                            "未充能尤洛伽配石在 skyRitualMinY 或更高处完成充能所需的秒数；1 秒为 20 tick。")
                    .defineInRange("eulogiaCompanionStoneChargeSeconds", 10, 1, 3600);
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

    public static final class OrderedMatchingUpgrade {
        public final ModConfigSpec.BooleanValue wrapTargets;
        public final ModConfigSpec.BooleanValue continueAfterTargetFailure;
        public final ModConfigSpec.IntValue perItemDetentionQueueLength;

        private OrderedMatchingUpgrade(ModConfigSpec.Builder builder) {
            builder.comment("Ordered Matching Upgrade behavior.",
                            "顺序匹配升级行为。")
                    .push("orderedMatchingUpgrade");
            wrapTargets = builder
                    .comment("Whether Per Slot maps source slots cyclically with slotIndex % targetCount. When disabled, source slots beyond the receiving endpoint count are not dispatched.",
                            "逐槽模式是否按 槽位号 % 接收端数量 循环映射。关闭后，超出接收端数量的来源槽位不再发配。")
                    .define("wrapTargets", true);
            continueAfterTargetFailure = builder
                    .comment("Whether a failed or temporarily unavailable target may be passed. Per Item detains the skipped assignment when queue capacity is available.",
                            "目标拒收或暂时不可用时是否允许越过；逐个模式会在队列有容量时扣押被跳过的分配。")
                    .define("continueAfterTargetFailure", true);
            perItemDetentionQueueLength = builder
                    .comment("Maximum number of one-item failed assignments retained per Per Item extraction face. Zero disables detention and prevents passing a failed target.",
                            "逐个模式每个抽取面最多保留的单物品失败分配数。0 表示禁用扣押，并阻止越过失败目标。")
                    .defineInRange("perItemDetentionQueueLength", 1, 0, 1024);
            builder.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue renderConfiguratorPlayerHeads;

        private Client(ModConfigSpec.Builder builder) {
            builder.push("gui");
            renderConfiguratorPlayerHeads = builder
                    .comment("Whether the configurator line details render active Sky Necklaces as player heads.",
                            "配置器线路详情中是否将活动的天穹项链显示为玩家头颅。")
                    .define("renderConfiguratorPlayerHeads", true);
            builder.pop();
        }
    }

    private SkyLogisticsConfig() {
    }
}
