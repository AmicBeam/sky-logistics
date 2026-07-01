package com.skylogistics.block;

import com.skylogistics.block.entity.SingleSlotDisplayBlockEntity;
import com.skylogistics.util.InteractionResults;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class SingleSlotDisplayBlock extends BaseEntityBlock {
    protected SingleSlotDisplayBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getDisplayShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getDisplayShape(state);
    }

    protected VoxelShape getDisplayShape(BlockState state) {
        return Shapes.block();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return toInteractionResult(handleDisplayInteraction(state, level, pos, player, hand,
                player.getItemInHand(hand)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return handleDisplayInteraction(state, level, pos, player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    protected InteractionResult handleDisplayInteraction(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, ItemStack held) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SingleSlotDisplayBlockEntity display)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        boolean changed = held.isEmpty() ? display.extractToPlayer(player, hand) : display.insertFromPlayer(player, held);
        return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    protected static InteractionResult toInteractionResult(InteractionResult result) {
        return InteractionResults.passToEmptyHand(result);
    }
}
