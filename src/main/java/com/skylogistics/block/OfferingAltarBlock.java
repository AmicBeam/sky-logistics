package com.skylogistics.block;

import com.mojang.serialization.MapCodec;
import com.skylogistics.block.entity.OfferingAltarBlockEntity;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class OfferingAltarBlock extends SingleSlotDisplayBlock {
    public static final MapCodec<OfferingAltarBlock> CODEC = simpleCodec(OfferingAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 1.0D, 15.0D, 2.0D, 15.0D),
            Block.box(3.0D, 2.0D, 3.0D, 13.0D, 5.0D, 13.0D),
            Block.box(4.0D, 5.0D, 4.0D, 12.0D, 8.0D, 12.0D),
            Block.box(2.0D, 8.0D, 2.0D, 14.0D, 10.0D, 14.0D),
            Block.box(5.0D, 9.0D, 0.0D, 11.0D, 12.0D, 4.0D),
            Block.box(5.0D, 9.0D, 12.0D, 11.0D, 12.0D, 16.0D),
            Block.box(0.0D, 9.0D, 5.0D, 4.0D, 12.0D, 11.0D),
            Block.box(12.0D, 9.0D, 5.0D, 16.0D, 12.0D, 11.0D),
            Block.box(4.0D, 10.0D, 4.0D, 12.0D, 11.0D, 12.0D),
            Block.box(6.0D, 11.0D, 6.0D, 10.0D, 15.0D, 10.0D),
            Block.box(5.0D, 15.0D, 5.0D, 11.0D, 16.0D, 11.0D));

    public OfferingAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getDisplayShape(BlockState state) {
        return SHAPE;
    }

    @Override
    protected InteractionResult handleDisplayInteraction(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, ItemStack held) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof OfferingAltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean inserting = !held.isEmpty();
        boolean changed = held.isEmpty() ? altar.extractToPlayer(player) : altar.insertFromPlayer(player, held);
        if (changed && inserting) {
            altar.sendRecipeStartFailureMessage(player);
        }
        return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OfferingAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.OFFERING_ALTAR.get(),
                OfferingAltarBlockEntity::tick);
    }
}
