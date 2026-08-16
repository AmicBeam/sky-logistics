package com.skylogistics.compat.jade;

import com.skylogistics.block.entity.NetworkEndpointBlockEntity;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.item.ModFilterListItem;
import com.skylogistics.util.StackData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.ITooltip;

final class JadeFilterTooltip {
    private JadeFilterTooltip() {}

    static CompoundTag write(NetworkEndpointBlockEntity endpoint, HolderLookup.Provider registries) {
        CompoundTag result = new CompoundTag();
        for (Direction direction : Direction.values()) {
            ItemStack filter = endpoint.getFaceFilter(direction, 0);
            if (!filter.isEmpty()) result.put(direction.getSerializedName(), StackData.saveItem(filter, registries));
        }
        return result;
    }

    static void append(ITooltip tooltip, CompoundTag filters, Direction direction, Player player,
            HolderLookup.Provider registries) {
        if (direction == null || !filters.contains(direction.getSerializedName(), Tag.TAG_COMPOUND)) return;
        ItemStack filter = StackData.loadItem(filters.getCompound(direction.getSerializedName()), registries);
        if (filter.isEmpty()) return;
        tooltip.add(Component.translatable("jade.skylogistics.filter_summary", filter.getHoverName(),
                Component.translatable(FilterListItem.isWhitelist(filter)
                        ? "screen.skylogistics.filter_whitelist" : "screen.skylogistics.filter_blacklist"))
                .withStyle(ChatFormatting.GRAY));
        if (player == null || !player.isShiftKeyDown()) {
            tooltip.add(Component.translatable("jade.skylogistics.filter_hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        List<Component> details = new ArrayList<>();
        if (ModFilterListItem.isModFilterList(filter)) ModFilterListItem.appendFilterContents(filter, details, true);
        else if (TagFilterListItem.isTagFilterList(filter)) TagFilterListItem.appendFilterContents(filter, details, true);
        else FilterListItem.appendFilterContents(filter, details, true);
        for (Component detail : details) tooltip.add(detail);
    }
}
