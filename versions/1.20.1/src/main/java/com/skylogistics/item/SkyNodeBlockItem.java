package com.skylogistics.item;

import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class SkyNodeBlockItem extends BlockItem {
    public SkyNodeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(UseOnContext context) {
        net.minecraft.world.InteractionResult result = super.useOn(context);
        if (!result.consumesAction()) {
            return result;
        }

        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() != null) {
            NodeMode mode = context.getPlayer().isShiftKeyDown() ? NodeMode.INPUT : NodeMode.OUTPUT;
            context.getPlayer().displayClientMessage(Component.translatable("message.skylogistics.sky_node.placed",
                    Component.translatable(mode.translationKey())), true);
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        List<StoredFilter> filters = storedFilters(stack);
        if (filters.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.skylogistics.sky_node.filters", filters.size())
                .withStyle(ChatFormatting.GRAY));
        if (!FilterListItem.showFilterContents()) {
            tooltip.add(Component.translatable("tooltip.skylogistics.filter_list.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (StoredFilter stored : filters) {
            ItemStack filter = stored.filter();
            tooltip.add(Component.translatable("tooltip.skylogistics.sky_node.filter_face",
                    Component.translatable("screen.skylogistics.face." + stored.direction().getSerializedName()),
                    Component.translatable(FilterListItem.isWhitelist(filter)
                            ? "screen.skylogistics.filter_whitelist"
                            : "screen.skylogistics.filter_blacklist"),
                    FilterListItem.countFilters(filter), FilterListItem.countFluidFilters(filter))
                    .withStyle(ChatFormatting.GRAY));
            FilterListItem.appendFilterContents(filter, tooltip, true);
        }
    }

    private static List<StoredFilter> storedFilters(ItemStack stack) {
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag == null || !tag.contains("FaceSettings", Tag.TAG_COMPOUND)) {
            return List.of();
        }
        List<StoredFilter> filters = new ArrayList<>();
        CompoundTag faceSettings = tag.getCompound("FaceSettings");
        for (Direction direction : Direction.values()) {
            String key = direction.getSerializedName();
            if (!faceSettings.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag settings = faceSettings.getCompound(key);
            if (!settings.contains("Filters", Tag.TAG_LIST)) {
                continue;
            }
            ListTag filterTags = settings.getList("Filters", Tag.TAG_COMPOUND);
            for (int i = 0; i < filterTags.size(); i++) {
                ItemStack filter = ItemStack.of(filterTags.getCompound(i).getCompound("Stack"));
                if (filter.is(ModItems.FILTER_LIST.get())) {
                    filters.add(new StoredFilter(direction, filter));
                }
            }
        }
        return filters;
    }

    private record StoredFilter(Direction direction, ItemStack filter) {
    }
}
