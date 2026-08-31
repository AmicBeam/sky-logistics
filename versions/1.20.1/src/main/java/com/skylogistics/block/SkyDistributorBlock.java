package com.skylogistics.block;

import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.DistributorPushDirection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.tags.TagKey;

public class SkyDistributorBlock extends BaseEntityBlock {
    public static final EnumProperty<DistributorPushDirection> PUSH_DIRECTION =
            EnumProperty.create("push_direction", DistributorPushDirection.class);
    private static final TagKey<Item> FORGE_WRENCHES = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "tools/wrench"));
    private static final TagKey<Item> COMMON_WRENCHES = TagKey.create(Registries.ITEM,
            new ResourceLocation("c", "tools/wrench"));

    public SkyDistributorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PUSH_DIRECTION, DistributorPushDirection.ALL));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkyDistributorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SKY_DISTRIBUTOR.get(),
                SkyDistributorBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !isWrench(stack)) return InteractionResult.PASS;
        if (!level.isClientSide) setSide(level, pos, hit.getDirection());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean isWrench(ItemStack stack) {
        return stack.is(FORGE_WRENCHES) || stack.is(COMMON_WRENCHES);
    }

    public void setSide(Level level, BlockPos pos, Direction clickedFace) {
        BlockState currentState = level.getBlockState(pos);
        DistributorPushDirection current = currentState.getValue(PUSH_DIRECTION);
        DistributorPushDirection next = current.afterWrenchClick(clickedFace);
        level.setBlockAndUpdate(pos, currentState.setValue(PUSH_DIRECTION, next));
        if (level.getBlockEntity(pos) instanceof SkyDistributorBlockEntity distributor) {
            distributor.abandonTargets();
            distributor.refreshTargets();
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SkyDistributorBlockEntity distributor) {
            distributor.refreshTargets();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (level.getBlockEntity(pos) instanceof SkyDistributorBlockEntity distributor) {
            distributor.invalidateTargets();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PUSH_DIRECTION);
    }
}
