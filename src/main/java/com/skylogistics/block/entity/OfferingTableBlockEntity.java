package com.skylogistics.block.entity;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.item.EulogiaCrystalItem;
import com.skylogistics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class OfferingTableBlockEntity extends SingleSlotDisplayBlockEntity {
    private static final int CHARGE_INTERVAL_TICKS = 20;

    public OfferingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OFFERING_TABLE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OfferingTableBlockEntity table) {
        if (!(level instanceof ServerLevel serverLevel) || pos.getY() < SkyLogisticsConfig.skyRitualMinY()) {
            return;
        }
        if (serverLevel.getGameTime() % CHARGE_INTERVAL_TICKS != 0L) {
            return;
        }
        ItemStack stack = table.getDisplayedItem();
        if (stack.isEmpty() || !(stack.getItem() instanceof EulogiaCrystalItem) || EulogiaCrystalItem.isCharged(stack)) {
            return;
        }
        boolean completed = EulogiaCrystalItem.chargeOneSecond(stack);
        if (completed) {
            serverLevel.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.45F);
            serverLevel.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 1.05D,
                    pos.getZ() + 0.5D, 18, 0.25D, 0.15D, 0.25D, 0.02D);
            table.markSlotChanged();
        } else {
            table.setChanged();
        }
    }

    @Override
    protected void onStoredItemChanged() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockEntity blockEntity = serverLevel.getBlockEntity(worldPosition.relative(direction));
            if (blockEntity instanceof OfferingAltarBlockEntity altar) {
                altar.wakeForRecipeCheck();
            }
        }
    }
}
