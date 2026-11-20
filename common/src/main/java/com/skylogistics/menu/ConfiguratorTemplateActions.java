package com.skylogistics.menu;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.PipeTemplate;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.network.SkyLineAccess;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ConfiguratorTemplateActions {
    private ConfiguratorTemplateActions() {}

    public static List<Item> cards() {
        return List.of(ModItems.SPEED_UPGRADE.get(), ModItems.DIMENSION_UPGRADE.get(),
                ModItems.FORCE_EXTRACTION_UPGRADE.get(), ModItems.ORDERED_MATCHING_UPGRADE.get());
    }

    public static boolean canEdit(Player player, ItemStack stack) {
        var config = ConfiguratorItem.read(stack);
        PipeTemplate pipe = ConfiguratorItem.readPipeTemplate(stack);
        return config != null && SkyLineAccess.canUse(player, config.lineId())
                && (pipe == null || pipe.owner() == null || SkyLineAccess.canUseOwner(player, pipe.owner()));
    }

    public static boolean handles(int action) {
        return action >= MenuAction.CONFIG_TEMPLATE_FILTER_BASE
                && action < MenuAction.CONFIG_FILTER_TOGGLE_MODE_BASE + Direction.values().length;
    }

    public static void apply(Player player, InteractionHand hand, ItemStack stack, ConfiguratorItem.ToolConfig config,
            int action) {
        if (!canEdit(player, stack)) return;
        PipeTemplate pipe = ConfiguratorItem.readPipeTemplate(stack);
        if (action == MenuAction.CONFIG_TEMPLATE_PASTE) {
            ConfiguratorItem.setPasteMode(stack, pipe == null
                    ? !ConfiguratorItem.isPasteMode(stack) : !ConfiguratorItem.isPipePasteMode(stack));
            return;
        }
        Direction direction = direction(action, MenuAction.CONFIG_TEMPLATE_FILTER_BASE);
        Direction clear = direction(action, MenuAction.CONFIG_TEMPLATE_CLEAR_FILTER_BASE);
        Direction toggle = direction(action, MenuAction.CONFIG_FILTER_TOGGLE_MODE_BASE);
        if (direction != null || clear != null || toggle != null) {
            ItemStack filter = direction == null ? ItemStack.EMPTY
                    : player.getItemInHand(hand == InteractionHand.MAIN_HAND
                            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (direction != null && !FilterListItem.isFilterItem(filter)) return;
            direction = direction == null ? clear : direction;
            if (toggle != null) {
                direction = toggle;
                filter = (pipe == null ? (config.hasCopiedFaces() ? config.face(toggle) : config.placement()).filter(0)
                        : pipe.face(toggle).filter()).copy();
                if (TagFilterListItem.isTagFilterList(filter)) {
                    TagFilterListItem.setWhitelist(filter, !TagFilterListItem.isWhitelist(filter));
                } else if (FilterListItem.isFilterItem(filter)) {
                    FilterListItem.setWhitelist(filter, !FilterListItem.isWhitelist(filter));
                } else {
                    return;
                }
            }
            if (pipe == null) {
                ConfiguratorItem.writeConfig(stack, config.withTemplateFilter(direction, filter),
                        ConfiguratorItem.assignedLineName(stack));
            } else {
                PipeTemplate.Face face = pipe.face(direction);
                if ((!face.connection().isContainer() && !filter.isEmpty())
                        || (pipe.type() == SimplePipeType.ENERGY && !filter.isEmpty()
                                && !TagFilterListItem.isTagFilterList(filter))) return;
                ConfiguratorItem.writePipeTemplate(stack, pipe.withFace(direction,
                        new PipeTemplate.Face(face.connection(), face.disconnected(), filter)));
            }
            return;
        }
        if (pipe != null) {
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
            return;
        }
        var cards = cards();
        int add = action - MenuAction.CONFIG_UPGRADE_ADD_BASE;
        int remove = action - MenuAction.CONFIG_UPGRADE_REMOVE_BASE;
        boolean adding = add >= 0 && add < cards.size();
        if (!adding && (remove < 0 || remove >= cards.size())) return;
        Item item = cards.get(adding ? add : remove);
        List<ItemStack> upgrades = new ArrayList<>(config.upgrades());
        for (int index = 0; index < upgrades.size(); index++) {
            ItemStack current = upgrades.get(index);
            if (!current.is(item)) continue;
            int count = current.getCount() + (adding ? 1 : -1);
            if (count > SkyNodeBlockEntity.maxUpgradeStackSize(current)) return;
            if (count == 0) upgrades.remove(index);
            else upgrades.set(index, current.copyWithCount(count));
            ConfiguratorItem.writeConfig(stack, config.withUpgrades(upgrades), ConfiguratorItem.assignedLineName(stack));
            return;
        }
        ItemStack card = new ItemStack(item);
        if (adding && upgrades.size() < SkyNodeBlockEntity.UPGRADE_SLOTS
                && SkyNodeBlockEntity.maxUpgradeStackSize(card) > 0) {
            upgrades.add(card);
            ConfiguratorItem.writeConfig(stack, config.withUpgrades(upgrades), ConfiguratorItem.assignedLineName(stack));
        }
    }

    private static Direction direction(int action, int base) {
        int index = action - base;
        Direction[] directions = Direction.values();
        return index >= 0 && index < directions.length ? directions[index] : null;
    }
}
