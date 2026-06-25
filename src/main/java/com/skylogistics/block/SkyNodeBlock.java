package com.skylogistics.block;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.menu.SkyNodeMenu;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public class SkyNodeBlock extends BaseEntityBlock {
    public static final DirectionProperty TARGET = BlockStateProperties.FACING;
    public static final EnumProperty<NodeMode> MODE = EnumProperty.create("mode", NodeMode.class);

    public SkyNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(TARGET, net.minecraft.core.Direction.NORTH)
                .setValue(MODE, NodeMode.OUTPUT));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        NodeMode mode = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? NodeMode.INPUT
                : NodeMode.OUTPUT;
        return defaultBlockState()
                .setValue(TARGET, context.getClickedFace().getOpposite())
                .setValue(MODE, mode);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(TARGET, MODE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkyNodeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof ConfiguratorItem
                && level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node) {
            return ConfiguratorItem.useOnNode(level, pos, node, player, hand, stack);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, ignored) -> new SkyNodeMenu(id, inventory, pos),
                            Component.translatable("menu.skylogistics.sky_node")),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeBoolean(false);
                        buffer.writeEnum(hand);
                    });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node)) {
            return;
        }
        NodeMode placementMode = state.getValue(MODE);
        node.setMode(placementMode);
        ItemStack offhand = placer.getOffhandItem();
        if (offhand.getItem() instanceof ConfiguratorItem) {
            node.applyPlacementToolConfig(ConfiguratorItem.readOrCreate(offhand), false);
        }
        node.setFaceMode(state.getValue(TARGET), placementMode == NodeMode.INPUT ? NodeFaceMode.INPUT : NodeFaceMode.OUTPUT);
    }

}
