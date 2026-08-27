package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.util.NodeFaceMode;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class SkyNodeJadeProvider extends BaseSkyLogisticsJadeProvider
        implements IBlockComponentProvider {
    public static final SkyNodeJadeProvider INSTANCE = new SkyNodeJadeProvider();
    private static final String DATA = "SkyLogisticsNode";

    private SkyNodeJadeProvider() {
        super("node");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData().getCompoundOrEmpty(DATA);
        if (data.isEmpty()) {
            if (accessor.getBlockEntity() instanceof SkyNodeBlockEntity node) {
                data = writeNodeData(node);
            } else {
                return;
            }
        }
        appendNodeTooltip(tooltip, data, accessor.getPlayer(), accessor.getLevel().registryAccess());
    }

    private static CompoundTag writeNodeData(SkyNodeBlockEntity node) {
        CompoundTag data = new CompoundTag();
        data.putString("LineName", node.getLineName());
        data.putBoolean("SpeedUpgrade", node.hasSpeedUpgrade());
        data.putBoolean("DimensionUpgrade", node.hasDimensionUpgrade());
        data.putBoolean("ForceExtractionUpgrade", node.hasForceExtractionUpgrade());
        data.putBoolean("Active", node.hasRecentTransfer());
        boolean items = false;
        boolean fluids = false;
        boolean energy = false;
        for (Direction direction : Direction.values()) {
            if (node.getFaceMode(direction) == NodeFaceMode.NONE) continue;
            items |= node.isItemsEnabled(direction);
            fluids |= node.isFluidsEnabled(direction);
            energy |= node.isEnergyEnabled(direction);
        }
        data.putBoolean("Items", items);
        data.putBoolean("Fluids", fluids);
        data.putBoolean("Energy", energy);
        if (node.getLevel() != null) data.put("Filters", JadeFilterTooltip.write(node, node.getLevel().registryAccess()));
        return data;
    }

    private static void appendNodeTooltip(ITooltip tooltip, CompoundTag data,
            net.minecraft.world.entity.player.Player player, net.minecraft.core.HolderLookup.Provider registries) {
        tooltip.add(Component.translatable("jade.skylogistics.line_name", data.getStringOr("LineName", "")));
        tooltip.add(Component.translatable("jade.skylogistics.resources",
                resourceSummary(data.getBooleanOr("Items", false), data.getBooleanOr("Fluids", false),
                        data.getBooleanOr("Energy", false))));
        tooltip.add(Component.translatable("jade.skylogistics.upgrades",
                upgradeSummary(data.getBooleanOr("SpeedUpgrade", false), data.getBooleanOr("DimensionUpgrade", false),
                        data.getBooleanOr("ForceExtractionUpgrade", false))));
        tooltip.add(Component.translatable("jade.skylogistics.status",
                Component.translatable(data.getBooleanOr("Active", false)
                        ? "jade.skylogistics.status_active" : "jade.skylogistics.status_idle")));
        CompoundTag filters = data.getCompoundOrEmpty("Filters");
        for (Direction direction : Direction.values()) JadeFilterTooltip.append(tooltip, filters, direction, player, registries);
    }

    private static Component resourceSummary(boolean items, boolean fluids, boolean energy) {
        return Component.empty()
                .append(items ? Component.translatable("screen.skylogistics.resource_short.items") : Component.literal("-"))
                .append("/")
                .append(fluids ? Component.translatable("screen.skylogistics.resource_short.fluids") : Component.literal("-"))
                .append("/")
                .append(energy ? Component.translatable("screen.skylogistics.resource_short.energy") : Component.literal("-"));
    }

    private static Component upgradeSummary(boolean speed, boolean dimension, boolean forceExtraction) {
        if (!speed && !dimension && !forceExtraction) {
            return Component.translatable("jade.skylogistics.upgrade_none");
        }
        var summary = Component.empty();
        if (speed) {
            summary.append(Component.translatable("jade.skylogistics.upgrade_speed_name"));
        }
        if (dimension) {
            if (speed) {
                summary.append(Component.literal(", "));
            }
            summary.append(Component.translatable("jade.skylogistics.upgrade_dimension_name"));
        }
        if (forceExtraction) {
            if (speed || dimension) summary.append(Component.literal(", "));
            summary.append(Component.translatable("jade.skylogistics.upgrade_force_extraction_name"));
        }
        return summary;
    }

    public static final class DataProvider extends BaseSkyLogisticsJadeProvider
            implements IServerDataProvider<BlockAccessor> {
        public static final DataProvider INSTANCE = new DataProvider();

        private DataProvider() {
            super("node");
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof SkyNodeBlockEntity node) {
                data.put(DATA, writeNodeData(node));
            }
        }
    }
}
