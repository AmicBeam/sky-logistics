package com.skylogistics.block;

import com.mojang.serialization.MapCodec;
import com.skylogistics.block.entity.SimplePipeBlockEntity;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.InteractionResults;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeType;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SimplePipeBlock extends BaseEntityBlock {
    public static final Map<Direction, EnumProperty<SimplePipeConnection>> CONNECTION_BY_DIRECTION = Map.of(
            Direction.DOWN, EnumProperty.create("down", SimplePipeConnection.class),
            Direction.UP, EnumProperty.create("up", SimplePipeConnection.class),
            Direction.NORTH, EnumProperty.create("north", SimplePipeConnection.class),
            Direction.SOUTH, EnumProperty.create("south", SimplePipeConnection.class),
            Direction.WEST, EnumProperty.create("west", SimplePipeConnection.class),
            Direction.EAST, EnumProperty.create("east", SimplePipeConnection.class));
    private static final TagKey<Item> FORGE_WRENCHES = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("forge", "tools/wrench"));
    private static final TagKey<Item> COMMON_WRENCHES = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("c", "tools/wrench"));
    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape[] NORMAL_ARMS = makeArmShapes(false);
    private static final VoxelShape[] EXTRACT_ARMS = makeArmShapes(true);
    private final SimplePipeType pipeType;
    private final MapCodec<SimplePipeBlock> codec;

    public SimplePipeBlock(Properties properties, SimplePipeType pipeType) {
        super(properties);
        this.pipeType = pipeType;
        this.codec = simpleCodec(nextProperties -> new SimplePipeBlock(nextProperties, pipeType));
        BlockState state = stateDefinition.any();
        for (EnumProperty<SimplePipeConnection> property : CONNECTION_BY_DIRECTION.values()) {
            state = state.setValue(property, SimplePipeConnection.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    public SimplePipeType pipeType() {
        return pipeType;
    }

    public static EnumProperty<SimplePipeConnection> connectionProperty(Direction direction) {
        return CONNECTION_BY_DIRECTION.get(direction);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        Direction placementTarget = context.getClickedFace().getOpposite();
        boolean extractTarget = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        for (Direction direction : Direction.values()) {
            state = state.setValue(connectionProperty(direction),
                    connectionAt(context.getLevel(), context.getClickedPos(), direction,
                            direction == placementTarget && extractTarget
                                    ? SimplePipeConnection.EXTRACT
                                    : SimplePipeConnection.INSERT));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (!(level instanceof Level actualLevel)) {
            return state;
        }
        SimplePipeConnection existing = state.getValue(connectionProperty(direction));
        SimplePipeConnection containerDefault = existing.isContainer() ? existing : SimplePipeConnection.INSERT;
        return state.setValue(connectionProperty(direction),
                connectionAt(actualLevel, pos, direction, containerDefault));
    }

    private SimplePipeConnection connectionAt(Level level, BlockPos pos, Direction direction,
            SimplePipeConnection containerConnection) {
        BlockState neighbor = level.getBlockState(pos.relative(direction));
        if (neighbor.getBlock() instanceof SimplePipeBlock pipe && pipe.pipeType == pipeType) {
            return SimplePipeConnection.PIPE;
        }
        return SimplePipeBlockEntity.hasCapability(level, pos.relative(direction), direction.getOpposite(), pipeType)
                ? containerConnection
                : SimplePipeConnection.NONE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(FORGE_WRENCHES) && !stack.is(COMMON_WRENCHES)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        Direction direction = targetedDirection(state, pos, hit);
        if (direction == null) {
            return InteractionResult.PASS;
        }
        SimplePipeConnection current = state.getValue(connectionProperty(direction));
        if (!current.isContainer()) {
            return InteractionResult.PASS;
        }
        SimplePipeConnection next = current == SimplePipeConnection.EXTRACT
                ? SimplePipeConnection.INSERT
                : SimplePipeConnection.EXTRACT;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(connectionProperty(direction), next), Block.UPDATE_ALL);
            player.sendSystemMessage(Component.translatable("message.skylogistics.simple_pipe.mode",
                    Component.translatable("message.skylogistics.simple_pipe." + next.getSerializedName())));
        }
        return InteractionResults.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    private static Direction targetedDirection(BlockState state, BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        Direction preferred;
        double ax = Math.abs(local.x);
        double ay = Math.abs(local.y);
        double az = Math.abs(local.z);
        if (ax >= ay && ax >= az) {
            preferred = local.x >= 0.0D ? Direction.EAST : Direction.WEST;
        } else if (ay >= az) {
            preferred = local.y >= 0.0D ? Direction.UP : Direction.DOWN;
        } else {
            preferred = local.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
        }
        if (state.getValue(connectionProperty(preferred)).isContainer()) {
            return preferred;
        }
        Direction hitFace = hit.getDirection();
        return state.getValue(connectionProperty(hitFace)).isContainer() ? hitFace : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (EnumProperty<SimplePipeConnection> property : CONNECTION_BY_DIRECTION.values()) {
            builder.add(property);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SimplePipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.SIMPLE_PIPE.get(), SimplePipeBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            SimplePipeConnection connection = state.getValue(connectionProperty(direction));
            if (connection == SimplePipeConnection.EXTRACT) {
                shape = Shapes.or(shape, EXTRACT_ARMS[direction.ordinal()]);
            } else if (connection != SimplePipeConnection.NONE) {
                shape = Shapes.or(shape, NORMAL_ARMS[direction.ordinal()]);
            }
        }
        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    private static VoxelShape[] makeArmShapes(boolean extract) {
        VoxelShape[] result = new VoxelShape[Direction.values().length];
        for (Direction direction : Direction.values()) {
            VoxelShape shape = orientedBox(direction, 6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 5.0D);
            if (extract) {
                shape = Shapes.or(shape, orientedBox(direction, 3.0D, 3.0D, 0.0D, 13.0D, 13.0D, 2.0D));
            }
            result[direction.ordinal()] = shape;
        }
        return result;
    }

    private static VoxelShape orientedBox(Direction direction, double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        return switch (direction) {
            case NORTH -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
            case SOUTH -> Block.box(16.0D - maxX, minY, 16.0D - maxZ, 16.0D - minX, maxY, 16.0D - minZ);
            case WEST -> Block.box(minZ, minY, minX, maxZ, maxY, maxX);
            case EAST -> Block.box(16.0D - maxZ, minY, 16.0D - maxX, 16.0D - minZ, maxY, 16.0D - minX);
            case DOWN -> Block.box(minX, minZ, minY, maxX, maxZ, maxY);
            case UP -> Block.box(minX, 16.0D - maxZ, 16.0D - maxY, maxX, 16.0D - minZ, 16.0D - minY);
        };
    }

    public boolean enabled() {
        return switch (pipeType) {
            case ITEM -> SkyLogisticsConfig.enableSimpleItemPipe();
            case FLUID -> SkyLogisticsConfig.enableSimpleFluidPipe();
            case ENERGY -> SkyLogisticsConfig.enableSimpleEnergyPipe();
        };
    }
}
