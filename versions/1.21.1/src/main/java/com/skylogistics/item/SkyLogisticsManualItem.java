package com.skylogistics.item;

import com.skylogistics.compat.PatchouliCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SkyLogisticsManualItem extends Item {
    public SkyLogisticsManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (PatchouliCompat.openManual(player)) {
            return InteractionResultHolder.consume(stack);
        }
        player.displayClientMessage(Component.translatable("message.skylogistics.manual.missing_provider"), true);
        return InteractionResultHolder.fail(stack);
    }
}
