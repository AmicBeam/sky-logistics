package com.skylogistics.item;

import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeMode;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
        if (!level.isClientSide() && context.getPlayer() != null) {
            NodeMode mode = context.getPlayer().isShiftKeyDown() ? NodeMode.INPUT : NodeMode.OUTPUT;
            context.getPlayer().sendOverlayMessage(Component.translatable("message.skylogistics.sky_node.placed",
                    Component.translatable(mode.translationKey())));
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        List<StoredFilter> filters = storedFilters(stack, context);
        if (filters.isEmpty()) {
            return;
        }
        tooltip.accept(Component.translatable("tooltip.skylogistics.sky_node.filters", filters.size())
                .withStyle(ChatFormatting.GRAY));
        if (!FilterListItem.showFilterContents()) {
            tooltip.accept(Component.translatable("tooltip.skylogistics.filter_list.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (StoredFilter stored : filters) {
            ItemStack filter = stored.filter();
            tooltip.accept(Component.translatable("tooltip.skylogistics.sky_node.filter_face",
                    Component.translatable("screen.skylogistics.face." + stored.direction().getSerializedName()),
                    Component.translatable(FilterListItem.isWhitelist(filter)
                            ? "screen.skylogistics.filter_whitelist"
                            : "screen.skylogistics.filter_blacklist"),
                    FilterListItem.countFilters(filter), FilterListItem.countFluidFilters(filter))
                    .withStyle(ChatFormatting.GRAY));
            FilterListItem.appendFilterContents(filter, tooltip, true);
        }
    }

    private static List<StoredFilter> storedFilters(ItemStack stack, Item.TooltipContext context) {
        TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) {
            return List.of();
        }
        CompoundTag tag = data.copyTagWithoutId();
        if (tag.isEmpty()) {
            return List.of();
        }
        if (!tag.contains("FaceSettings")) {
            return List.of();
        }
        HolderLookup.Provider registries = context.registries() == null
                ? StackData.builtinRegistries()
                : context.registries();
        List<StoredFilter> filters = new ArrayList<>();
        CompoundTag faceSettings = tag.getCompoundOrEmpty("FaceSettings");
        for (Direction direction : Direction.values()) {
            String key = direction.getSerializedName();
            if (!faceSettings.contains(key)) {
                continue;
            }
            CompoundTag settings = faceSettings.getCompoundOrEmpty(key);
            if (!settings.contains("Filters")) {
                continue;
            }
            ListTag filterTags = settings.getListOrEmpty("Filters");
            for (int i = 0; i < filterTags.size(); i++) {
                ItemStack filter = StackData.loadItem(filterTags.getCompoundOrEmpty(i).getCompoundOrEmpty("Stack"), registries);
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
