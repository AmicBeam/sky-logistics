package com.skylogistics.block;

import com.skylogistics.block.entity.SimplePipeBlockEntity;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeType;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.server.level.ServerLevel;
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
            new ResourceLocation("forge", "tools/wrench"));
    private static final TagKey<Item> COMMON_WRENCHES = TagKey.create(Registries.ITEM,
            new ResourceLocation("c", "tools/wrench"));
    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape[] NORMAL_ARMS = makeArmShapes(false);
    private static final VoxelShape[] EXTRACT_ARMS = makeArmShapes(true);
    private final SimplePipeType pipeType;

    public SimplePipeBlock(Properties properties, SimplePipeType pipeType) {
        super(properties);
        this.pipeType = pipeType;
        BlockState state = stateDefinition.any();
        for (EnumProperty<SimplePipeConnection> property : CONNECTION_BY_DIRECTION.values()) {
            state = state.setValue(property, SimplePipeConnection.NONE);
        }
        registerDefaultState(state);
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
        PlacementConnections pipeConnections = placementPipeConnections(
                context.getLevel(), context.getClickedPos());
        Direction placementTarget = context.getClickedFace().getOpposite();
        boolean extractTarget = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        for (Direction direction : Direction.values()) {
            BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            SimplePipeConnection connection;
            if (neighbor.getBlock() instanceof SimplePipeBlock pipe && pipe.pipeType == pipeType) {
                connection = pipeConnections.allowed().contains(direction)
                        ? SimplePipeConnection.PIPE
                        : SimplePipeConnection.NONE;
            } else {
                connection = connectionAt(context.getLevel(), context.getClickedPos(), direction,
                        direction == placementTarget && extractTarget
                                ? SimplePipeConnection.EXTRACT
                                : SimplePipeConnection.INSERT);
            }
            state = state.setValue(connectionProperty(direction), connection);
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        PlacementConnections pipeConnections = placementPipeConnections(level, pos);
        if (pipeConnections.rejected().isEmpty()
                || !(level.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity)) {
            return;
        }
        BlockState placedState = level.getBlockState(pos);
        for (Direction direction : pipeConnections.rejected()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof SimplePipeBlock neighborPipe)
                    || neighborPipe.pipeType != pipeType
                    || !(level.getBlockEntity(neighborPos) instanceof SimplePipeBlockEntity neighborEntity)) {
                continue;
            }
            pipeEntity.setSideDisconnected(direction, true, SimplePipeConnection.PIPE);
            neighborEntity.setSideDisconnected(direction.getOpposite(), true, SimplePipeConnection.PIPE);
            placedState = placedState.setValue(connectionProperty(direction), SimplePipeConnection.NONE);
            level.setBlock(neighborPos, neighborState.setValue(
                    connectionProperty(direction.getOpposite()), SimplePipeConnection.NONE), Block.UPDATE_ALL);
        }
        level.setBlock(pos, placedState, Block.UPDATE_ALL);
        if (level instanceof ServerLevel serverLevel) {
            SkyNetworkRegistry.markPipeTopologyDirty(serverLevel, pos);
        }
        if (placer instanceof Player player) {
            player.displayClientMessage(Component.translatable(
                    "message.skylogistics.simple_pipe.connection_limit",
                    SkyLogisticsConfig.simplePipeMaxConnectedBlocks()), true);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!(level instanceof Level actualLevel)) {
            return state;
        }
        if (actualLevel.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity
                && pipeEntity.isSideDisconnected(direction)) {
            return state.setValue(connectionProperty(direction), SimplePipeConnection.NONE);
        }
        SimplePipeConnection existing = state.getValue(connectionProperty(direction));
        SimplePipeConnection containerDefault = existing.isContainer() ? existing : SimplePipeConnection.INSERT;
        BlockState updated = state.setValue(connectionProperty(direction),
                connectionAt(actualLevel, pos, direction, containerDefault));
        if (!updated.equals(state) && actualLevel instanceof ServerLevel serverLevel) {
            SkyNetworkRegistry.markPipeTopologyDirty(serverLevel, pos);
        }
        return updated;
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

    private PlacementConnections placementPipeConnections(Level level, BlockPos pos) {
        int maxConnected = SkyLogisticsConfig.simplePipeMaxConnectedBlocks();
        EnumSet<Direction> allowed = EnumSet.noneOf(Direction.class);
        EnumSet<Direction> rejected = EnumSet.noneOf(Direction.class);
        Set<BlockPos> accepted = new HashSet<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof SimplePipeBlock pipe) || pipe.pipeType != pipeType) {
                continue;
            }
            if (level.getBlockEntity(neighborPos) instanceof SimplePipeBlockEntity neighborEntity
                    && neighborEntity.isSideDisconnected(direction.getOpposite())) {
                continue;
            }
            if (accepted.contains(neighborPos)) {
                allowed.add(direction);
                continue;
            }
            Set<BlockPos> component = collectConnectedPipeComponent(level, neighborPos, pos, maxConnected);
            int additional = 0;
            for (BlockPos member : component) {
                if (!accepted.contains(member)) {
                    additional++;
                }
            }
            if (component.size() > maxConnected || accepted.size() + additional + 1 > maxConnected) {
                rejected.add(direction);
                continue;
            }
            accepted.addAll(component);
            allowed.add(direction);
        }
        return new PlacementConnections(allowed, rejected);
    }

    private Set<BlockPos> collectConnectedPipeComponent(Level level, BlockPos start, BlockPos excluded,
            int maxConnected) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty() && visited.size() <= maxConnected) {
            BlockPos currentPos = pending.removeFirst();
            if (currentPos.equals(excluded) || !visited.add(currentPos)) {
                continue;
            }
            BlockState currentState = level.getBlockState(currentPos);
            if (!(currentState.getBlock() instanceof SimplePipeBlock pipe) || pipe.pipeType != pipeType) {
                visited.remove(currentPos);
                continue;
            }
            SimplePipeBlockEntity pipeEntity =
                    level.getBlockEntity(currentPos) instanceof SimplePipeBlockEntity entity ? entity : null;
            for (Direction direction : Direction.values()) {
                if (currentState.getValue(connectionProperty(direction)) == SimplePipeConnection.PIPE
                        && (pipeEntity == null || !pipeEntity.isSideDisconnected(direction))) {
                    BlockPos neighborPos = currentPos.relative(direction);
                    if (!neighborPos.equals(excluded) && !visited.contains(neighborPos)) {
                        pending.addLast(neighborPos);
                    }
                }
            }
        }
        return visited;
    }

    private record PlacementConnections(Set<Direction> allowed, Set<Direction> rejected) {
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(FORGE_WRENCHES) && !held.is(COMMON_WRENCHES)) {
            return InteractionResult.PASS;
        }
        boolean toggleConnection = player.isShiftKeyDown();
        Direction direction = targetedDirection(state, level, pos, hit, toggleConnection);
        if (direction == null) {
            return InteractionResult.PASS;
        }
        if (toggleConnection) {
            if (!level.isClientSide && !toggleDisconnected(level, pos, state, direction, player)) {
                return InteractionResult.PASS;
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        SimplePipeConnection current = state.getValue(connectionProperty(direction));
        if (!current.isContainer()) {
            return InteractionResult.PASS;
        }
        SimplePipeConnection next = current == SimplePipeConnection.EXTRACT
                ? SimplePipeConnection.INSERT
                : SimplePipeConnection.EXTRACT;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(connectionProperty(direction), next), Block.UPDATE_ALL);
            if (level instanceof ServerLevel serverLevel) {
                SkyNetworkRegistry.markPipeTopologyDirty(serverLevel, pos);
            }
            player.displayClientMessage(Component.translatable("message.skylogistics.simple_pipe.mode",
                    Component.translatable("message.skylogistics.simple_pipe." + next.getSerializedName())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private boolean toggleDisconnected(Level level, BlockPos pos, BlockState state, Direction direction,
            Player player) {
        if (!(level.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity)) {
            return false;
        }
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        Direction opposite = direction.getOpposite();
        boolean pipeNeighbor = neighborState.getBlock() instanceof SimplePipeBlock neighborPipe
                && neighborPipe.pipeType == pipeType;
        SimplePipeBlockEntity neighborEntity = pipeNeighbor
                && level.getBlockEntity(neighborPos) instanceof SimplePipeBlockEntity otherEntity
                        ? otherEntity
                        : null;
        boolean reconnecting = pipeEntity.isSideDisconnected(direction);
        if (pipeNeighbor && neighborEntity == null) {
            return false;
        }

        if (reconnecting) {
            if (pipeNeighbor && pipeConnectionWouldExceedLimit(level, pos, neighborPos)) {
                player.displayClientMessage(Component.translatable(
                        "message.skylogistics.simple_pipe.connection_limit",
                        SkyLogisticsConfig.simplePipeMaxConnectedBlocks()), true);
                return true;
            }
            SimplePipeConnection next = connectionAt(level, pos, direction,
                    pipeEntity.rememberedContainerConnection(direction));
            if (next == SimplePipeConnection.NONE) {
                return false;
            }
            pipeEntity.setSideDisconnected(direction, false, next);
            if (pipeNeighbor) {
                neighborEntity.setSideDisconnected(opposite, false, SimplePipeConnection.PIPE);
                level.setBlock(neighborPos,
                        neighborState.setValue(connectionProperty(opposite), SimplePipeConnection.PIPE),
                        Block.UPDATE_ALL);
            }
            level.setBlock(pos, level.getBlockState(pos).setValue(connectionProperty(direction), next),
                    Block.UPDATE_ALL);
        } else {
            SimplePipeConnection current = state.getValue(connectionProperty(direction));
            if (current == SimplePipeConnection.NONE) {
                return false;
            }
            pipeEntity.setSideDisconnected(direction, true, current);
            if (pipeNeighbor) {
                neighborEntity.setSideDisconnected(opposite, true,
                        neighborState.getValue(connectionProperty(opposite)));
                level.setBlock(neighborPos,
                        neighborState.setValue(connectionProperty(opposite), SimplePipeConnection.NONE),
                        Block.UPDATE_ALL);
            }
            level.setBlock(pos, level.getBlockState(pos)
                    .setValue(connectionProperty(direction), SimplePipeConnection.NONE), Block.UPDATE_ALL);
        }
        if (level instanceof ServerLevel serverLevel) {
            SkyNetworkRegistry.markPipeTopologyDirty(serverLevel, pos);
            if (pipeNeighbor) {
                SkyNetworkRegistry.markPipeTopologyDirty(serverLevel, neighborPos);
            }
        }
        player.displayClientMessage(Component.translatable(
                reconnecting
                        ? "message.skylogistics.simple_pipe.reconnected"
                        : "message.skylogistics.simple_pipe.disconnected"), true);
        return true;
    }

    private boolean pipeConnectionWouldExceedLimit(Level level, BlockPos first, BlockPos second) {
        int maxConnected = SkyLogisticsConfig.simplePipeMaxConnectedBlocks();
        Set<BlockPos> connected = collectConnectedPipeComponent(level, first, null, maxConnected);
        if (connected.size() > maxConnected) {
            return true;
        }
        Set<BlockPos> other = collectConnectedPipeComponent(level, second, null, maxConnected);
        connected.addAll(other);
        return connected.size() > maxConnected;
    }

    private Direction targetedDirection(BlockState state, Level level, BlockPos pos, BlockHitResult hit,
            boolean includeAllConnections) {
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
        if (isTargetable(state, level, pos, preferred, includeAllConnections)) {
            return preferred;
        }
        Direction hitFace = hit.getDirection();
        return isTargetable(state, level, pos, hitFace, includeAllConnections) ? hitFace : null;
    }

    private boolean isTargetable(BlockState state, Level level, BlockPos pos, Direction direction,
            boolean includeAllConnections) {
        SimplePipeConnection connection = state.getValue(connectionProperty(direction));
        if (!includeAllConnections) {
            return connection.isContainer();
        }
        if (connection != SimplePipeConnection.NONE) {
            return true;
        }
        SimplePipeConnection containerDefault =
                level.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity
                        ? pipeEntity.rememberedContainerConnection(direction)
                        : SimplePipeConnection.INSERT;
        return connectionAt(level, pos, direction, containerDefault) != SimplePipeConnection.NONE;
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
        return null;
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
