package com.skylogistics.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ConfiguratorPreview {
    private ConfiguratorPreview() {}

    public static Direction firstCopiedFace(ConfiguratorItem.ToolConfig config, PipeTemplate pipe) {
        if (pipe != null) {
            Direction firstEndpoint = null;
            for (Direction direction : Direction.values()) {
                var face = pipe.face(direction);
                if (!face.filter().isEmpty()) return direction;
                if (firstEndpoint == null && face.connection().isContainer()) firstEndpoint = direction;
            }
            return firstEndpoint;
        }
        if (config == null || !config.hasCopiedFaces()) return null;
        for (Direction direction : Direction.values()) {
            if (!config.face(direction).filter(0).isEmpty()) return direction;
        }
        return null;
    }

    public static ItemStack filter(ConfiguratorItem.ToolConfig config, PipeTemplate pipe, Direction face) {
        if (pipe != null) return face == null ? ItemStack.EMPTY : pipe.face(face).filter();
        if (config == null) return ItemStack.EMPTY;
        return (face == null ? config.placement() : config.face(face)).filter(0);
    }

    public static void appendUpgrades(ConfiguratorItem.ToolConfig config, Consumer<Component> tooltip) {
        for (ItemStack upgrade : config.upgrades()) {
            if (upgrade.isEmpty()) continue;
            tooltip.accept(Component.translatable("tooltip.skylogistics.configurator.upgrade",
                    upgrade.getHoverName(), upgrade.getCount()).withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean appendFilterMode(ItemStack filter, Consumer<Component> tooltip) {
        if (filter.isEmpty() || !FilterListItem.isFilterItem(filter)) return false;
        boolean whitelist = TagFilterListItem.isTagFilterList(filter)
                ? TagFilterListItem.isWhitelist(filter) : FilterListItem.isWhitelist(filter);
        tooltip.accept(Component.translatable("tooltip.skylogistics.configurator.filter",
                Component.translatable(whitelist ? "tooltip.skylogistics.filter_list.whitelist_button"
                        : "tooltip.skylogistics.filter_list.blacklist_button")).withStyle(ChatFormatting.GRAY));
        return true;
    }
}
