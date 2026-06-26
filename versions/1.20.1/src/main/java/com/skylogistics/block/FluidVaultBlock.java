package com.skylogistics.block;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.menu.FluidVaultMenu;
import com.skylogistics.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.network.NetworkHooks;

public class FluidVaultBlock extends BaseEntityBlock {
    public FluidVaultBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidVaultBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (player.getItemInHand(hand).is(ModItems.CHORA_NECTAR.get())) {
            return InteractionResult.PASS;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FluidVaultBlockEntity
                && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, ignored) -> new FluidVaultMenu(id, inventory, pos),
                            Component.translatable("menu.skylogistics.fluid_vault")),
                    buffer -> buffer.writeBlockPos(pos));
            ((FluidVaultBlockEntity) level.getBlockEntity(pos)).syncTo(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
