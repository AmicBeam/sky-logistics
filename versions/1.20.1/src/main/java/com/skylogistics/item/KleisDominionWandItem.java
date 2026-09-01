package com.skylogistics.item;

import com.skylogistics.network.KleisEndpointSavedData;
import com.skylogistics.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class KleisDominionWandItem extends Item {
    public KleisDominionWandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack configurator = player.getOffhandItem();
        if (!configurator.is(ModItems.CONFIGURATOR.get())) {
            if (!context.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable(
                        "message.skylogistics.kleis_dominion_wand.configurator_required"), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;
        KleisEndpointSavedData.ToggleResult result = KleisEndpointSavedData.get(serverPlayer.getServer()).toggle(
                serverPlayer, context.getClickedPos(), context.getClickedFace(), player.isShiftKeyDown(), configurator);
        KleisEndpointSavedData.get(serverPlayer.getServer()).syncVisibleOverlays(serverPlayer.getServer(),
                serverPlayer.level().dimension(), context.getClickedPos());
        player.displayClientMessage(Component.translatable("message.skylogistics.kleis_dominion_wand."
                + result.name().toLowerCase(java.util.Locale.ROOT)), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            double x = entity.getX();
            double z = entity.getZ();
            entity.teleportTo(x, 256.0D, z);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skylogistics.kleis_dominion_wand")
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.skylogistics.kleis_dominion_wand.cross_dimension")
                .withStyle(ChatFormatting.BLUE));
    }
}
