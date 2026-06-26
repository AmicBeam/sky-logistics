package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class SkyNodeJadeProvider extends BaseSkyLogisticsJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final SkyNodeJadeProvider INSTANCE = new SkyNodeJadeProvider();
    private static final String DATA = "SkyLogisticsNode";

    private SkyNodeJadeProvider() {
        super("node");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData().getCompound(DATA);
        if (data.isEmpty()) {
            if (accessor.getBlockEntity() instanceof SkyNodeBlockEntity node) {
                data = writeNodeData(node);
            } else {
                return;
            }
        }
        appendNodeTooltip(tooltip, data);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SkyNodeBlockEntity node) {
            data.put(DATA, writeNodeData(node));
        }
    }

    private static CompoundTag writeNodeData(SkyNodeBlockEntity node) {
        CompoundTag data = new CompoundTag();
        data.putString("LineName", node.getLineName());
        data.putString("LineCode", shortLine(node.getLineId()));
        data.putInt("LineIndex", node.getLineIndex() + 1);
        data.putInt("LineCount", node.getLineCount());
        data.putBoolean("SpeedUpgrade", node.hasSpeedUpgrade());
        data.putBoolean("DimensionUpgrade", node.hasDimensionUpgrade());
        return data;
    }

    private static void appendNodeTooltip(ITooltip tooltip, CompoundTag data) {
        tooltip.append(Component.translatable("jade.skylogistics.line_info", data.getString("LineName"),
                data.getString("LineCode"), data.getInt("LineIndex"), data.getInt("LineCount")));
        tooltip.append(Component.translatable("jade.skylogistics.upgrades",
                upgradeSummary(data.getBoolean("SpeedUpgrade"), data.getBoolean("DimensionUpgrade"))));
    }

    private static Component upgradeSummary(boolean speed, boolean dimension) {
        if (!speed && !dimension) {
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
        return summary;
    }
}
