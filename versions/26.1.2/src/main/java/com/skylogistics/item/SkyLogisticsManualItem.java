package com.skylogistics.item;

import com.skylogistics.compat.ManualCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SkyLogisticsManualItem extends Item {
    public SkyLogisticsManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (ManualCompat.openManual(player)) {
            return InteractionResult.CONSUME;
        }
        player.sendOverlayMessage(Component.translatable("message.skylogistics.manual.missing_provider"));
        return InteractionResult.FAIL;
    }
}
