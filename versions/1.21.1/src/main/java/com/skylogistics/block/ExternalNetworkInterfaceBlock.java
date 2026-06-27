package com.skylogistics.block;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.menu.SkyNodeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public abstract class ExternalNetworkInterfaceBlock extends BaseEntityBlock {
    protected ExternalNetworkInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        openInterface(state, level, pos, player, hand);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        openInterface(state, level, pos, player, InteractionHand.MAIN_HAND);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected void openInterface(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node) {
            node.claimDefaultLineName(player);
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, ignored) -> new SkyNodeMenu(id, inventory, pos),
                            Component.translatable(state.getBlock().getDescriptionId())),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeBoolean(false);
                        buffer.writeEnum(hand);
                    });
        }
    }
}
