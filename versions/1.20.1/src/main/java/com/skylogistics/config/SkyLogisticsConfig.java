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
        public final ForgeConfigSpec.BooleanValue enableMaintainedItemHotSlotPolling;
        public final ForgeConfigSpec.IntValue maintainedItemHotSlotPollTicks;
        public final ForgeConfigSpec.IntValue skyNecklaceTickInterval;
        public final ForgeConfigSpec.IntValue skyNecklaceSlotScansPerTick;
        public final ForgeConfigSpec.IntValue skyNecklaceTargetAttemptsPerWork;
        public final ForgeConfigSpec.IntValue skyRitualMinY;
        public final ForgeConfigSpec.IntValue eulogiaCrystalChargeSeconds;
        public final ForgeConfigSpec.LongValue skyContainerTransferLimit;
        public final ForgeConfigSpec.BooleanValue allowAe2ItemTransfer;
        public final ForgeConfigSpec.BooleanValue allowSophisticatedStorageStackUpgradeTransfer;
        public final ForgeConfigSpec.ConfigValue<List<? extends Object>> forceExtractionDeviceModIdWhitelist;
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
        public final OrderedMatchingUpgrade orderedMatchingUpgrade;

        private Server(ForgeConfigSpec.Builder builder) {
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
            allowBeyondDimensionsManaTransfer = builder
                    .comment("Whether Sky Dimension Interfaces may transfer Botania mana stored in Beyond Dimensions networks.")
                    .define("allowBeyondDimensionsManaTransfer", true);
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
            enableDistributorItems = builder
                    .comment("Whether Celestial Distributors proxy and distribute item storage.",
                            "天穹分配器是否代理并分配物品存储。")
                    .define("enableItems", true);
            enableDistributorFluids = builder
                    .comment("Whether Celestial Distributors proxy and distribute fluid storage.",
                            "天穹分配器是否代理并分配流体存储。")
                    .define("enableFluids", true);
            enableDistributorEnergy = builder
                    .comment("Whether Celestial Distributors proxy and distribute energy storage.",
                            "天穹分配器是否代理并分配能量存储。")
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
                    .comment("Minimum block Y for Eulogia Crystals to charge and sky offering altars to work.",
                            "尤洛伽水晶充能及天穹供奉祭坛工作的最低方块 Y 坐标。")
                    .defineInRange("skyRitualMinY", 128, -64, 320);
            eulogiaCrystalChargeSeconds = builder
                    .comment("Seconds an uncharged Eulogia Crystal must spend at or above skyRitualMinY before it becomes charged. One second is 20 ticks.",
                            "未充能尤洛伽水晶在 skyRitualMinY 或更高处完成充能所需的秒数；1 秒为 20 tick。")
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

    public static final class OrderedMatchingUpgrade {
        public final ForgeConfigSpec.BooleanValue wrapTargets;
        public final ForgeConfigSpec.BooleanValue continueAfterTargetFailure;
        public final ForgeConfigSpec.IntValue perItemDetentionQueueLength;

        private OrderedMatchingUpgrade(ForgeConfigSpec.Builder builder) {
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
        public final ForgeConfigSpec.BooleanValue renderConfiguratorPlayerHeads;

        private Client(ForgeConfigSpec.Builder builder) {
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
