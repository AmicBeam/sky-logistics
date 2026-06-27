package com.skylogistics.block;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.menu.SkyNodeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node) {
            node.claimDefaultLineName(player);
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, ignored) -> new SkyNodeMenu(id, inventory, pos),
                            Component.translatable(state.getBlock().getDescriptionId())),
                    buffer -> {
                        buffer.writeBlockPos(pos);
                        buffer.writeBoolean(false);
                        buffer.writeEnum(hand);
                    });
        }
        return InteractionResult.CONSUME;
    }

    @FunctionalInterface
    public interface BlockEntityFactory {
        BlockEntity create(BlockPos pos, BlockState state);
    }
}
