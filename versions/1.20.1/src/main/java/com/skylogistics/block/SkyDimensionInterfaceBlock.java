package com.skylogistics.block;

import com.skylogistics.block.entity.SkyDimensionInterfaceBlockEntity;
import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SkyDimensionInterfaceBlock extends ExternalNetworkInterfaceBlock {
    public SkyDimensionInterfaceBlock(Properties properties) {
        super(properties, SkyDimensionInterfaceBlockEntity::new);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        BeyondDimensionsCompat.bindOnPlaced(level, pos, placer);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (BeyondDimensionsCompat.handleBindingUse(level, pos, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
