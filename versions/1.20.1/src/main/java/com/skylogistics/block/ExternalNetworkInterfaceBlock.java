package com.skylogistics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExternalNetworkInterfaceBlock extends BaseEntityBlock {
    private final BlockEntityFactory factory;

    public ExternalNetworkInterfaceBlock(Properties properties, BlockEntityFactory factory) {
        super(properties);
        this.factory = factory;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.create(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @FunctionalInterface
    public interface BlockEntityFactory {
        BlockEntity create(BlockPos pos, BlockState state);
    }
}
