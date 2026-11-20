package com.skylogistics.menu;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.PipeTemplate;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.network.SkyLineAccess;
import com.skylogistics.util.SimplePipeConnection;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class ConfiguratorTemplateActions {
    private ConfiguratorTemplateActions() {}

    public static boolean canEdit(Player player, ItemStack stack) {
        var config = ConfiguratorItem.read(stack);
        PipeTemplate pipe = ConfiguratorItem.readPipeTemplate(stack);
        return config != null && SkyLineAccess.canUse(player, config.lineId())
                && (pipe == null || pipe.owner() == null || SkyLineAccess.canUseOwner(player, pipe.owner()));
    }

    static boolean handles(int action) {
        return action == MenuAction.CONFIG_TEMPLATE_PASTE
                || direction(action, MenuAction.CONFIG_FILTER_TOGGLE_MODE_BASE) != null
                || direction(action, MenuAction.CONFIG_PIPE_MODE_BASE) != null
                || direction(action, MenuAction.CONFIG_PIPE_DISCONNECT_BASE) != null;
    }

    static void apply(Player player, ItemStack stack, ConfiguratorItem.ToolConfig config, int action) {
        if (!canEdit(player, stack)) return;
        PipeTemplate pipe = ConfiguratorItem.readPipeTemplate(stack);
        if (action == MenuAction.CONFIG_TEMPLATE_PASTE) {
            ConfiguratorItem.setPasteMode(stack, pipe == null
                    ? !ConfiguratorItem.isPasteMode(stack) : !ConfiguratorItem.isPipePasteMode(stack));
            return;
        }
        Direction toggle = direction(action, MenuAction.CONFIG_FILTER_TOGGLE_MODE_BASE);
        if (toggle != null) {
            ItemStack filter = (pipe != null ? pipe.face(toggle).filter()
                    : (config.hasCopiedFaces() ? config.face(toggle) : config.placement()).filter(0)).copy();
            if (TagFilterListItem.isTagFilterList(filter)) {
                TagFilterListItem.setWhitelist(filter, !TagFilterListItem.isWhitelist(filter));
            } else if (FilterListItem.isFilterItem(filter)) {
                FilterListItem.setWhitelist(filter, !FilterListItem.isWhitelist(filter));
            } else {
                return;
            }
            ConfiguratorTemplateSlots.edit(stack, toggle, ConfiguratorTemplateSlots.FILTER, filter, false);
            return;
        }
        if (pipe == null) return;
        Direction mode = direction(action, MenuAction.CONFIG_PIPE_MODE_BASE);
        Direction disconnect = direction(action, MenuAction.CONFIG_PIPE_DISCONNECT_BASE);
        Direction selected = mode == null ? disconnect : mode;
        if (selected == null || !pipe.face(selected).connection().isContainer()) return;
        PipeTemplate.Face face = pipe.face(selected);
        var connection = mode == null ? face.connection()
                : face.connection() == SimplePipeConnection.INSERT
                        ? SimplePipeConnection.EXTRACT : SimplePipeConnection.INSERT;
        ConfiguratorItem.writePipeTemplate(stack, pipe.withFace(selected, new PipeTemplate.Face(
                connection, disconnect == null ? face.disconnected() : !face.disconnected(), face.filter())));
    }

    private static Direction direction(int action, int base) {
        int index = action - base;
        Direction[] directions = Direction.values();
        return index >= 0 && index < directions.length ? directions[index] : null;
    }
}
