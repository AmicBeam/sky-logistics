package com.skylogistics.item;

import com.skylogistics.util.OrderedMatchingMode;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class UpgradeCardItem extends Item {
    private static final String ORDERED_MATCHING_MODE = "OrderedMatchingMode";
    private static final String ORDERED_MATCHING_OFFSET = "OrderedMatchingOffset";
    private final String tooltipKey;
    private final boolean orderedMatchingModeSwitch;

    public UpgradeCardItem(Properties properties, String tooltipKey) {
        this(properties, tooltipKey, false);
    }

    public UpgradeCardItem(Properties properties, String tooltipKey, boolean orderedMatchingModeSwitch) {
        super(properties);
        this.tooltipKey = tooltipKey;
        this.orderedMatchingModeSwitch = orderedMatchingModeSwitch;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!orderedMatchingModeSwitch) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            OrderedMatchingMode mode = orderedMatchingMode(stack).next();
            setOrderedMatchingMode(stack, mode);
            player.displayClientMessage(Component.translatable("message.skylogistics.ordered_matching_upgrade.mode",
                    Component.translatable(mode.translationKey())), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        if (orderedMatchingModeSwitch) {
            tooltip.add(Component.translatable("tooltip.skylogistics.ordered_matching_upgrade.mode",
                    Component.translatable(orderedMatchingMode(stack).translationKey()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (orderedMatchingMode(stack) == OrderedMatchingMode.PER_SLOT) {
                tooltip.add(Component.translatable("tooltip.skylogistics.ordered_matching_upgrade.offset",
                        orderedMatchingOffset(stack)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    public static OrderedMatchingMode orderedMatchingMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? OrderedMatchingMode.PER_SLOT
                : OrderedMatchingMode.byName(tag.getString(ORDERED_MATCHING_MODE));
    }

    public static int orderedMatchingOffset(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(ORDERED_MATCHING_OFFSET);
    }

    public static void setOrderedMatchingOffset(ItemStack stack, int offset) {
        if (offset == 0) {
            if (stack.hasTag()) {
                stack.getTag().remove(ORDERED_MATCHING_OFFSET);
                if (stack.getTag().isEmpty()) stack.setTag(null);
            }
        } else {
            stack.getOrCreateTag().putInt(ORDERED_MATCHING_OFFSET, offset);
        }
    }

    private static void setOrderedMatchingMode(ItemStack stack, OrderedMatchingMode mode) {
        if (mode == OrderedMatchingMode.PER_SLOT) {
            if (stack.hasTag()) {
                stack.getTag().remove(ORDERED_MATCHING_MODE);
                if (stack.getTag().isEmpty()) stack.setTag(null);
            }
        } else {
            stack.getOrCreateTag().putString(ORDERED_MATCHING_MODE, mode.serializedName());
        }
    }
}
