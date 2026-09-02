package com.skylogistics.block.entity;

import com.skylogistics.registry.ModBlocks;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.util.RedstoneControl;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/** Runtime-only node backed by KleisEndpointSavedData rather than a placed block. */
public final class KleisVirtualNodeBlockEntity extends SkyNodeBlockEntity {
    public static final Direction ENDPOINT_DIRECTION = Direction.NORTH;

    private final Direction targetFace;
    private Runnable changeListener = () -> {};
    private boolean suppressChanges;

    public KleisVirtualNodeBlockEntity(BlockPos targetPos, Direction targetFace) {
        super(targetPos, virtualState());
        this.targetFace = Objects.requireNonNull(targetFace);
    }

    private static BlockState virtualState() {
        return ModBlocks.SKY_NODE.get().defaultBlockState()
                .setValue(com.skylogistics.block.SkyNodeBlock.TARGET, ENDPOINT_DIRECTION);
    }

    public Direction targetFace() {
        return targetFace;
    }

    public void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {} : listener;
    }

    public void setSuppressChanges(boolean suppress) {
        suppressChanges = suppress;
    }

    @Override
    protected boolean isVirtualEndpoint() {
        return true;
    }

    @Override
    public boolean usesSingleEndpoint() {
        return true;
    }

    @Override
    public Direction getSingleEndpointDirection() {
        return ENDPOINT_DIRECTION;
    }

    @Override
    public Direction getTargetDirection() {
        return ENDPOINT_DIRECTION;
    }

    @Override
    public boolean canConfigureFace(Direction direction) {
        return direction == ENDPOINT_DIRECTION;
    }

    @Override
    public BlockPos getTargetPos() {
        return getBlockPos();
    }

    @Override
    public BlockPos getTargetPos(Direction direction) {
        return getBlockPos();
    }

    @Override
    public Direction getAccessSide() {
        return targetFace;
    }

    @Override
    public Direction getAccessSide(Direction direction) {
        return targetFace;
    }

    @Override
    public boolean hasConfigurableTarget(Direction direction) {
        return direction == ENDPOINT_DIRECTION && getLevel() != null
                && !getLevel().getBlockState(getBlockPos()).isAir();
    }

    @Override
    public Component getTargetName(Direction direction) {
        if (getLevel() == null || getLevel().getBlockState(getBlockPos()).isAir()) {
            return Component.translatable("screen.skylogistics.no_target");
        }
        return getLevel().getBlockState(getBlockPos()).getBlock().getName();
    }

    @Override
    public boolean hasDimensionUpgrade() {
        return true;
    }

    @Override
    public void applyPlacementToolConfig(ConfiguratorItem.ToolConfig config, boolean includeMode) {
        super.applyPlacementToolConfig(config.withPlacement(
                config.placement().withRedstoneControl(RedstoneControl.IGNORE)), includeMode);
    }

    @Override
    public RedstoneControl getRedstoneControl(Direction direction) {
        return RedstoneControl.IGNORE;
    }

    @Override
    public boolean isFaceRedstoneAllowed(Direction direction) {
        return true;
    }

    @Override
    public boolean consumeRedstonePulse(Direction direction) {
        return false;
    }

    @Override
    public void onRedstoneNeighborChanged() {
    }

    @Override
    public void cycleRedstoneControl(Direction direction) {
    }

    @Override
    public void setChanged() {
        if (!suppressChanges) {
            changeListener.run();
        }
    }
}
