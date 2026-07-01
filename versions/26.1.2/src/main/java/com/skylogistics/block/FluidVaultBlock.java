package com.skylogistics.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

public class FluidVaultBlock extends BaseEntityBlock {
    public static final MapCodec<FluidVaultBlock> CODEC = simpleCodec(FluidVaultBlock::new);

    public FluidVaultBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.CHORA_NECTAR.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return com.skylogistics.util.InteractionResults.sidedSuccess(level.isClientSide());
        }
        openVault(level, pos, player);
        return com.skylogistics.util.InteractionResults.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        openVault(level, pos, player);
        return com.skylogistics.util.InteractionResults.sidedSuccess(level.isClientSide());
    }

    private void openVault(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FluidVaultBlockEntity
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, ignored) -> new FluidVaultMenu(id, inventory, pos),
                            Component.translatable("menu.skylogistics.fluid_vault")),
                    buffer -> buffer.writeBlockPos(pos));
            ((FluidVaultBlockEntity) level.getBlockEntity(pos)).syncTo(serverPlayer);
        }
    }
}
