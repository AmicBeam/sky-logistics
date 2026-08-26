package com.skylogistics.item;

import com.skylogistics.util.InteractionResults;
import com.skylogistics.util.OrderedMatchingMode;
import com.skylogistics.util.StackData;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!orderedMatchingModeSwitch) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            OrderedMatchingMode mode = orderedMatchingMode(stack).next();
            setOrderedMatchingMode(stack, mode);
            player.sendOverlayMessage(Component.translatable("message.skylogistics.ordered_matching_upgrade.mode",
                    Component.translatable(mode.translationKey())));
        }
        return InteractionResults.sidedSuccess(level.isClientSide());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        if (orderedMatchingModeSwitch) {
            tooltip.accept(Component.translatable("tooltip.skylogistics.ordered_matching_upgrade.mode",
                    Component.translatable(orderedMatchingMode(stack).translationKey()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (orderedMatchingMode(stack) == OrderedMatchingMode.PER_SLOT) {
                tooltip.accept(Component.translatable("tooltip.skylogistics.ordered_matching_upgrade.offset",
                        orderedMatchingOffset(stack)).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    public static OrderedMatchingMode orderedMatchingMode(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag == null ? OrderedMatchingMode.PER_SLOT
                : OrderedMatchingMode.byName(tag.getStringOr(ORDERED_MATCHING_MODE, ""));
    }

    public static int orderedMatchingOffset(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag == null ? 0 : tag.getIntOr(ORDERED_MATCHING_OFFSET, 0);
    }

    public static void setOrderedMatchingOffset(ItemStack stack, int offset) {
        if (offset == 0) StackData.remove(stack, ORDERED_MATCHING_OFFSET);
        else StackData.update(stack, tag -> tag.putInt(ORDERED_MATCHING_OFFSET, offset));
    }

    private static void setOrderedMatchingMode(ItemStack stack, OrderedMatchingMode mode) {
        if (mode == OrderedMatchingMode.PER_SLOT) {
            StackData.remove(stack, ORDERED_MATCHING_MODE);
        } else {
            setOrderedMatchingOffset(stack, 0);
            StackData.update(stack, tag -> tag.putString(ORDERED_MATCHING_MODE, mode.serializedName()));
        }
    }
}
