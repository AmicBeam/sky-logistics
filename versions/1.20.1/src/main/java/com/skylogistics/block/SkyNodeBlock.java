package com.skylogistics.block;

import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.menu.SkyNodeMenu;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class SkyNodeBlock extends BaseEntityBlock {
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;
    public static final Map<Direction, EnumProperty<NodeFaceMode>> MODE_PROPERTY_BY_DIRECTION = Map.of(
            Direction.DOWN, EnumProperty.create("down_mode", NodeFaceMode.class),
            Direction.UP, EnumProperty.create("up_mode", NodeFaceMode.class),
            Direction.NORTH, EnumProperty.create("north_mode", NodeFaceMode.class),
            Direction.SOUTH, EnumProperty.create("south_mode", NodeFaceMode.class),
            Direction.WEST, EnumProperty.create("west_mode", NodeFaceMode.class),
            Direction.EAST, EnumProperty.create("east_mode", NodeFaceMode.class));
    public static final DirectionProperty TARGET = BlockStateProperties.FACING;
    public static final EnumProperty<NodeMode> MODE = EnumProperty.create("mode", NodeMode.class);
    private static final int FACE_MODE_COUNT = NodeFaceMode.values().length;
    private static final VoxelShape CORE_SHAPE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape[] OUTPUT_ARM_SHAPES = makeArmShapes(false);
    private static final VoxelShape[] INPUT_ARM_SHAPES = makeArmShapes(true);
    private static final VoxelShape[] SHAPES = makeShapes();

    public SkyNodeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any()
                .setValue(TARGET, net.minecraft.core.Direction.NORTH)
                .setValue(MODE, NodeMode.OUTPUT);
        for (BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        for (EnumProperty<NodeFaceMode> property : MODE_PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, NodeFaceMode.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        NodeMode mode = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? NodeMode.INPUT
                : NodeMode.OUTPUT;
        Direction target = context.getClickedFace().getOpposite();
        return defaultBlockState()
                .setValue(TARGET, target)
                .setValue(connectedProperty(target), true)
                .setValue(faceModeProperty(target), mode == NodeMode.INPUT ? NodeFaceMode.INPUT : NodeFaceMode.OUTPUT)
                .setValue(MODE, mode);
    }

    public static BooleanProperty connectedProperty(Direction direction) {
        return PROPERTY_BY_DIRECTION.get(direction);
    }

    public static EnumProperty<NodeFaceMode> faceModeProperty(Direction direction) {
        return MODE_PROPERTY_BY_DIRECTION.get(direction);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(TARGET, MODE);
        for (BooleanProperty property : PROPERTY_BY_DIRECTION.values()) {
            builder.add(property);
        }
        for (EnumProperty<NodeFaceMode> property : MODE_PROPERTY_BY_DIRECTION.values()) {
            builder.add(property);
        }
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForState(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForState(state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof ConfiguratorItem
                && level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node) {
            return ConfiguratorItem.useOnNode(level, pos, node, player, hand, stack);
        }
        if (tryApplyHeldItem(stack, level, pos, player, hit.getDirection())) {
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node) {
                node.claimDefaultLineName(player);
            }
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

    private boolean tryApplyHeldItem(ItemStack stack, Level level, BlockPos pos, Player player, Direction clickedFace) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node)) {
            return false;
        }
        if (SkyNodeBlockEntity.isUpgradeItem(stack)) {
            int slot = firstAcceptingUpgradeSlot(node, stack);
            if (slot < 0) {
                return false;
            }
            if (!level.isClientSide) {
                node.setUpgrade(slot, stack);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return true;
        }
        if (SkyNodeBlockEntity.isFaceFilterItem(stack)) {
            Direction direction = configurableFace(node, clickedFace);
            if (direction == null) {
                return false;
            }
            if (!level.isClientSide) {
                node.setFaceFilter(direction, 0, stack);
            }
            return true;
        }
        return false;
    }

    private int firstAcceptingUpgradeSlot(SkyNodeBlockEntity node, ItemStack stack) {
        for (int slot = 0; slot < SkyNodeBlockEntity.UPGRADE_SLOTS; slot++) {
            if (node.canAcceptUpgrade(slot, stack)) {
                return slot;
            }
        }
        return -1;
    }

    private Direction configurableFace(SkyNodeBlockEntity node, Direction clickedFace) {
        Direction direction = node.usesSingleEndpoint() ? node.getSingleEndpointDirection() : clickedFace;
        if (node.canConfigureFace(direction)) {
            return direction;
        }
        Direction target = node.getTargetDirection();
        return node.canConfigureFace(target) ? target : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node)) {
            return;
        }
        NodeMode placementMode = state.getValue(MODE);
        Direction targetDirection = state.getValue(TARGET);
        node.setMode(placementMode);
        ItemStack offhand = placer.getOffhandItem();
        if (offhand.getItem() instanceof ConfiguratorItem) {
            node.applyPlacementToolConfig(ConfiguratorItem.readOrCreate(offhand,
                    placer instanceof Player player ? player : null), false);
        } else {
            if (placer instanceof Player player) {
                node.claimDefaultLineName(player);
            }
            node.configureTargetResourcesFromCapabilities();
        }
        node.setFaceMode(targetDirection, placementMode == NodeMode.INPUT ? NodeFaceMode.INPUT : NodeFaceMode.OUTPUT);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SkyNodeBlockEntity node) {
            node.dropUpgrades();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private static VoxelShape shapeForState(BlockState state) {
        return SHAPES[shapeIndex(state)];
    }

    private static int shapeIndex(BlockState state) {
        int index = 0;
        int factor = 1;
        for (Direction direction : Direction.values()) {
            NodeFaceMode faceMode = state.hasProperty(faceModeProperty(direction))
                    ? state.getValue(faceModeProperty(direction))
                    : NodeFaceMode.NONE;
            index += faceMode.ordinal() * factor;
            factor *= FACE_MODE_COUNT;
        }
        return index;
    }

    private static VoxelShape[] makeShapes() {
        int shapeCount = 1;
        for (int i = 0; i < Direction.values().length; i++) {
            shapeCount *= FACE_MODE_COUNT;
        }
        VoxelShape[] shapes = new VoxelShape[shapeCount];
        NodeFaceMode[] modes = NodeFaceMode.values();
        for (int index = 0; index < shapes.length; index++) {
            VoxelShape shape = CORE_SHAPE;
            int remaining = index;
            for (Direction direction : Direction.values()) {
                NodeFaceMode faceMode = modes[remaining % FACE_MODE_COUNT];
                remaining /= FACE_MODE_COUNT;
                if (faceMode == NodeFaceMode.INPUT) {
                    shape = Shapes.or(shape, INPUT_ARM_SHAPES[direction.ordinal()]);
                } else if (faceMode == NodeFaceMode.OUTPUT) {
                    shape = Shapes.or(shape, OUTPUT_ARM_SHAPES[direction.ordinal()]);
                }
            }
            shapes[index] = shape;
        }
        return shapes;
    }

    private static VoxelShape[] makeArmShapes(boolean input) {
        VoxelShape[] shapes = new VoxelShape[Direction.values().length];
        for (Direction direction : Direction.values()) {
            VoxelShape arm = orientedBox(direction, 6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 5.0D);
            if (input) {
                arm = Shapes.or(arm,
                        orientedBox(direction, 3.0D, 3.0D, 0.0D, 13.0D, 13.0D, 2.0D),
                        orientedBox(direction, 2.0D, 2.0D, 2.0D, 14.0D, 4.0D, 4.0D),
                        orientedBox(direction, 2.0D, 12.0D, 2.0D, 14.0D, 14.0D, 4.0D),
                        orientedBox(direction, 2.0D, 4.0D, 2.0D, 4.0D, 12.0D, 4.0D),
                        orientedBox(direction, 12.0D, 4.0D, 2.0D, 14.0D, 12.0D, 4.0D));
            } else {
                arm = Shapes.or(arm,
                        orientedBox(direction, 4.0D, 4.0D, 0.0D, 12.0D, 12.0D, 2.0D));
            }
            shapes[direction.ordinal()] = arm;
        }
        return shapes;
    }

    private static VoxelShape orientedBox(Direction direction, double minU, double minV, double minDepth,
            double maxU, double maxV, double maxDepth) {
        return switch (direction) {
            case NORTH -> Block.box(minU, minV, minDepth, maxU, maxV, maxDepth);
            case SOUTH -> Block.box(minU, minV, 16.0D - maxDepth, maxU, maxV, 16.0D - minDepth);
            case WEST -> Block.box(minDepth, minV, minU, maxDepth, maxV, maxU);
            case EAST -> Block.box(16.0D - maxDepth, minV, minU, 16.0D - minDepth, maxV, maxU);
            case DOWN -> Block.box(minU, minDepth, minV, maxU, maxDepth, maxV);
            case UP -> Block.box(minU, 16.0D - maxDepth, minV, maxU, 16.0D - minDepth, maxV);
        };
    }

}
