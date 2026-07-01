package com.skylogistics.block;

import com.mojang.serialization.MapCodec;
import com.skylogistics.block.entity.OfferingTableBlockEntity;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class OfferingTableBlock extends SingleSlotDisplayBlock {
    public static final MapCodec<OfferingTableBlock> CODEC = simpleCodec(OfferingTableBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0D, 0.0D, 2.0D, 14.0D, 2.0D, 14.0D),
            Block.box(4.0D, 2.0D, 4.0D, 12.0D, 4.0D, 12.0D),
            Block.box(5.0D, 4.0D, 5.0D, 11.0D, 11.0D, 11.0D),
            Block.box(2.0D, 11.0D, 2.0D, 14.0D, 13.0D, 14.0D),
            Block.box(3.0D, 13.0D, 3.0D, 13.0D, 14.0D, 13.0D),
            Block.box(5.0D, 14.0D, 5.0D, 11.0D, 15.0D, 11.0D),
            Block.box(2.0D, 13.0D, 2.0D, 4.0D, 15.0D, 4.0D),
            Block.box(12.0D, 13.0D, 2.0D, 14.0D, 15.0D, 4.0D),
            Block.box(2.0D, 13.0D, 12.0D, 4.0D, 15.0D, 14.0D),
            Block.box(12.0D, 13.0D, 12.0D, 14.0D, 15.0D, 14.0D));

    public OfferingTableBlock(Properties properties) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OfferingTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.OFFERING_TABLE.get(),
                OfferingTableBlockEntity::tick);
    }
}
