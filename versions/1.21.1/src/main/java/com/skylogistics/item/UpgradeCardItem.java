package com.skylogistics.item;

import com.skylogistics.util.OrderedMatchingMode;
import com.skylogistics.util.StackData;
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
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        if (orderedMatchingModeSwitch) {
            tooltip.add(Component.translatable("tooltip.skylogistics.ordered_matching_upgrade.mode",
                    Component.translatable(orderedMatchingMode(stack).translationKey()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static OrderedMatchingMode orderedMatchingMode(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag == null ? OrderedMatchingMode.PER_SLOT
                : OrderedMatchingMode.byName(tag.getString(ORDERED_MATCHING_MODE));
    }

    private static void setOrderedMatchingMode(ItemStack stack, OrderedMatchingMode mode) {
        if (mode == OrderedMatchingMode.PER_SLOT) {
            StackData.remove(stack, ORDERED_MATCHING_MODE);
        } else {
            StackData.update(stack, tag -> tag.putString(ORDERED_MATCHING_MODE, mode.serializedName()));
        }
    }
}
