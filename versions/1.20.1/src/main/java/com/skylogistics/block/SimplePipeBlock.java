package com.skylogistics.block;

import com.skylogistics.block.entity.SimplePipeBlockEntity;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeGeometry;
import com.skylogistics.util.SimplePipeEndpointTargeting;
import com.skylogistics.util.SimplePipeType;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SimplePipeBlock extends BaseEntityBlock {
    public static final Map<Direction, BooleanProperty> CONNECTION_BY_DIRECTION = Map.of(
            Direction.DOWN, BooleanProperty.create("connected_down"),
            Direction.UP, BooleanProperty.create("connected_up"),
            Direction.NORTH, BooleanProperty.create("connected_north"),
            Direction.SOUTH, BooleanProperty.create("connected_south"),
            Direction.WEST, BooleanProperty.create("connected_west"),
            Direction.EAST, BooleanProperty.create("connected_east"));
    private static final ThreadLocal<PlacementExtraction> PLACEMENT_EXTRACTION = new ThreadLocal<>();
    private static final TagKey<Item> FORGE_WRENCHES = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "tools/wrench"));
    private static final TagKey<Item> COMMON_WRENCHES = TagKey.create(Registries.ITEM,
            new ResourceLocation("c", "tools/wrench"));
    private final SimplePipeType pipeType;

    public SimplePipeBlock(Properties properties, SimplePipeType pipeType) {
        super(properties);
        this.pipeType = pipeType;
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : CONNECTION_BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    public SimplePipeType pipeType() {
        return pipeType;
    }

    public static BooleanProperty connectionProperty(Direction direction) {
        return CONNECTION_BY_DIRECTION.get(direction);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        PlacementConnections pipeConnections = placementPipeConnections(
                context.getLevel(), context.getClickedPos());
        Direction placementTarget = context.getClickedFace().getOpposite();
        boolean extractTarget = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        if (extractTarget) {
            PLACEMENT_EXTRACTION.set(new PlacementExtraction(context.getClickedPos(), placementTarget));
        } else {
            PLACEMENT_EXTRACTION.remove();
        }
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
            state = state.setValue(connectionProperty(direction), connection != SimplePipeConnection.NONE);
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        assignPlacementOwner(level, pos, state, placer);
        PlacementExtraction placementExtraction = PLACEMENT_EXTRACTION.get();
        PLACEMENT_EXTRACTION.remove();
        if (placementExtraction != null && placementExtraction.pos().equals(pos)
                && level.getBlockEntity(pos) instanceof SimplePipeBlockEntity placedPipe
                && state.getValue(connectionProperty(placementExtraction.direction()))) {
            placedPipe.setExtracting(placementExtraction.direction(), true);
        }
        if (level.isClientSide) {
            return;
        }
        Set<Direction> rejectedDirections = rejectedPlacementDirections(level, pos, state);
        if (rejectedDirections.isEmpty()
                || !(level.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity)) {
            return;
        }
        BlockState placedState = level.getBlockState(pos);
        for (Direction direction : rejectedDirections) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof SimplePipeBlock neighborPipe)
                    || neighborPipe.pipeType != pipeType
                    || !(level.getBlockEntity(neighborPos) instanceof SimplePipeBlockEntity neighborEntity)) {
                continue;
            }
            pipeEntity.setSideDisconnected(direction, true, SimplePipeConnection.PIPE);
            neighborEntity.setSideDisconnected(direction.getOpposite(), true, SimplePipeConnection.PIPE);
            placedState = withConnection(placedState, direction, SimplePipeConnection.NONE);
            level.setBlock(neighborPos, neighborState.setValue(
                    connectionProperty(direction.getOpposite()), false), Block.UPDATE_ALL);
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

    private void assignPlacementOwner(Level level, BlockPos pos, BlockState state, LivingEntity placer) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SimplePipeBlockEntity placedPipe)) return;
        UUID ownerId = null;
        for (Direction direction : Direction.values()) {
            if (!state.getValue(connectionProperty(direction))) continue;
            BlockPos neighborPos = pos.relative(direction);
            if (level.getBlockEntity(neighborPos) instanceof SimplePipeBlockEntity neighbor
                    && neighbor.pipeType() == pipeType && neighbor.ownerId() != null) {
                ownerId = neighbor.ownerId();
                break;
            }
        }
        if (ownerId == null && placer instanceof Player player) ownerId = player.getUUID();
        placedPipe.assignOwnerId(ownerId);
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!(level instanceof Level actualLevel)) {
            return state;
        }
        if (actualLevel.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity
                && pipeEntity.isSideDisconnected(direction)) {
            return withConnection(state, direction, SimplePipeConnection.NONE);
        }
        SimplePipeConnection existing = connectionFromState(actualLevel, pos, state, direction);
        SimplePipeConnection containerDefault = existing.isContainer() ? existing : SimplePipeConnection.INSERT;
        SimplePipeConnection next = connectionAt(actualLevel, pos, direction, containerDefault);
        if (!next.isContainer()
                && actualLevel.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity) {
            pipeEntity.setExtracting(direction, false);
        }
        BlockState updated = withConnection(state, direction, next);
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
        PlacementConnections indexed = indexedPlacementPipeConnections(level, pos);
        if (indexed != null) {
            return indexed;
        }
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

    private PlacementConnections indexedPlacementPipeConnections(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        int maxConnected = SkyLogisticsConfig.simplePipeMaxConnectedBlocks();
        int connected = 1;
        Set<java.util.UUID> acceptedLines = new HashSet<>();
        EnumSet<Direction> allowed = EnumSet.noneOf(Direction.class);
        EnumSet<Direction> rejected = EnumSet.noneOf(Direction.class);
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
            SkyNetworkRegistry.PipeLineInfo info =
                    SkyNetworkRegistry.simplePipeLineInfo(serverLevel, neighborPos, pipeType);
            if (info == null) {
                return null;
            }
            if (acceptedLines.contains(info.lineId())) {
                allowed.add(direction);
            } else if (connected + info.size() <= maxConnected) {
                acceptedLines.add(info.lineId());
                connected += info.size();
                allowed.add(direction);
            } else {
                rejected.add(direction);
            }
        }
        return new PlacementConnections(allowed, rejected);
    }

    private Set<Direction> rejectedPlacementDirections(Level level, BlockPos pos, BlockState placedState) {
        EnumSet<Direction> rejected = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof SimplePipeBlock pipe && pipe.pipeType == pipeType
                    && !placedState.getValue(connectionProperty(direction))
                    && (!(level.getBlockEntity(neighborPos) instanceof SimplePipeBlockEntity neighborEntity)
                            || !neighborEntity.isSideDisconnected(direction.getOpposite()))) {
                rejected.add(direction);
            }
        }
        return rejected;
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
                if (currentState.getValue(connectionProperty(direction))
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

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (FilterListItem.isFilterItem(held)) {
            Direction direction = targetedContainerEndpoint(state, level, pos, hit);
            if (direction == null) return InteractionResult.PASS;
            if (pipeType == SimplePipeType.ENERGY && !TagFilterListItem.isTagFilterList(held)) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable(
                        "message.skylogistics.simple_pipe.filter_unsupported"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity) {
                pipeEntity.setEndpointFilter(direction, held);
                player.displayClientMessage(Component.translatable(
                        "message.skylogistics.simple_pipe.filter_applied"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            Direction direction = targetedContainerEndpoint(state, level, pos, hit);
            if (direction != null && level.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity
                    && !pipeEntity.getFaceFilter(direction, 0).isEmpty()) {
                if (!level.isClientSide) {
                    pipeEntity.clearEndpointFilter(direction);
                    player.displayClientMessage(Component.translatable(
                            "message.skylogistics.simple_pipe.filter_cleared"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        boolean wrench = held.is(FORGE_WRENCHES) || held.is(COMMON_WRENCHES);
        if (wrench && player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (wrench) {
            Direction direction = targetedDirection(state, level, pos, hit, true);
            if (direction == null) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide && !toggleDisconnected(level, pos, state, direction, player)) {
                return InteractionResult.PASS;
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!held.is(ModItems.CONFIGURATOR.get())) {
            return InteractionResult.PASS;
        }
        Direction direction = targetedDirection(state, level, pos, hit, false);
        if (direction == null) {
            return InteractionResult.PASS;
        }
        SimplePipeConnection current = connectionFromState(level, pos, state, direction);
        if (!current.isContainer()) {
            return InteractionResult.PASS;
        }
        SimplePipeConnection next = current == SimplePipeConnection.EXTRACT
                ? SimplePipeConnection.INSERT
                : SimplePipeConnection.EXTRACT;
        if (!level.isClientSide) {
            if (!(level.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity)) {
                return InteractionResult.PASS;
            }
            pipeEntity.setExtracting(direction, next == SimplePipeConnection.EXTRACT);
            if (level instanceof ServerLevel serverLevel) {
                SkyNetworkRegistry.markPipeTopologyDirty(serverLevel, pos);
            }
            player.displayClientMessage(Component.translatable("message.skylogistics.simple_pipe.mode",
                    Component.translatable("message.skylogistics.simple_pipe." + next.getSerializedName())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static Direction targetedContainerEndpoint(BlockState state, Level level, BlockPos pos,
            BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        Direction direction = SimplePipeEndpointTargeting.endpointDirection(local.x, local.y, local.z);
        return direction != null && connectionFromState(level, pos, state, direction).isContainer()
                ? direction : null;
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
                        neighborState.setValue(connectionProperty(opposite), true),
                        Block.UPDATE_ALL);
            }
            level.setBlock(pos, withConnection(level.getBlockState(pos), direction, next),
                    Block.UPDATE_ALL);
        } else {
            SimplePipeConnection current = connectionFromState(level, pos, state, direction);
            if (current == SimplePipeConnection.NONE) {
                return false;
            }
            pipeEntity.setSideDisconnected(direction, true, current);
            if (pipeNeighbor) {
                neighborEntity.setSideDisconnected(opposite, true,
                        connectionFromState(level, neighborPos, neighborState, opposite));
                level.setBlock(neighborPos,
                        withConnection(neighborState, opposite, SimplePipeConnection.NONE),
                        Block.UPDATE_ALL);
            }
            level.setBlock(pos, withConnection(level.getBlockState(pos), direction, SimplePipeConnection.NONE),
                    Block.UPDATE_ALL);
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
        if (level instanceof ServerLevel serverLevel) {
            SkyNetworkRegistry.PipeLineInfo firstInfo =
                    SkyNetworkRegistry.simplePipeLineInfo(serverLevel, first, pipeType);
            SkyNetworkRegistry.PipeLineInfo secondInfo =
                    SkyNetworkRegistry.simplePipeLineInfo(serverLevel, second, pipeType);
            if (firstInfo != null && secondInfo != null) {
                return !firstInfo.lineId().equals(secondInfo.lineId())
                        && firstInfo.size() > maxConnected - secondInfo.size();
            }
        }
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
        SimplePipeConnection connection = connectionFromState(level, pos, state, direction);
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
        for (BooleanProperty property : CONNECTION_BY_DIRECTION.values()) {
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

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int connectionMask = 0;
        int extractMask = 0;
        SimplePipeBlockEntity pipeEntity =
                level.getBlockEntity(pos) instanceof SimplePipeBlockEntity entity ? entity : null;
        for (Direction direction : Direction.values()) {
            int side = SimplePipeGeometry.sideMask(direction);
            if (state.getValue(connectionProperty(direction))) {
                connectionMask |= side;
                if (pipeEntity != null && pipeEntity.isExtracting(direction)) {
                    extractMask |= side;
                }
            }
        }
        return SimplePipeGeometry.shape(connectionMask, extractMask);
    }

    private static BlockState withConnection(BlockState state, Direction direction,
            SimplePipeConnection connection) {
        return state.setValue(connectionProperty(direction), connection != SimplePipeConnection.NONE);
    }

    public static SimplePipeConnection connectionFromState(Level level, BlockPos pos, BlockState state,
            Direction direction) {
        if (!state.getValue(connectionProperty(direction))) {
            return SimplePipeConnection.NONE;
        }
        BlockState neighbor = level.getBlockState(pos.relative(direction));
        if (neighbor.getBlock() instanceof SimplePipeBlock pipe
                && state.getBlock() instanceof SimplePipeBlock self
                && pipe.pipeType == self.pipeType) {
            return SimplePipeConnection.PIPE;
        }
        return level.getBlockEntity(pos) instanceof SimplePipeBlockEntity pipeEntity
                && pipeEntity.isExtracting(direction)
                ? SimplePipeConnection.EXTRACT
                : SimplePipeConnection.INSERT;
    }

    private record PlacementExtraction(BlockPos pos, Direction direction) {
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    public boolean enabled() {
        return switch (pipeType) {
            case ITEM -> SkyLogisticsConfig.enableSimpleItemPipe();
            case FLUID -> SkyLogisticsConfig.enableSimpleFluidPipe();
            case ENERGY -> SkyLogisticsConfig.enableSimpleEnergyPipe();
        };
    }
}
