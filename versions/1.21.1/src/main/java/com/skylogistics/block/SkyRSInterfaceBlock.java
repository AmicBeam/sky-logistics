package com.skylogistics.block;

import com.mojang.serialization.MapCodec;
import com.skylogistics.block.entity.SkyRSInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SkyRSInterfaceBlock extends ExternalNetworkInterfaceBlock {
    public static final MapCodec<SkyRSInterfaceBlock> CODEC = simpleCodec(SkyRSInterfaceBlock::new);

    public SkyRSInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkyRSInterfaceBlockEntity(pos, state);
    }
}
