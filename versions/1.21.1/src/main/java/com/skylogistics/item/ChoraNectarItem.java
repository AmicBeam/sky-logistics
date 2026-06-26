package com.skylogistics.item;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ChoraNectarItem extends Item {
    public ChoraNectarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ItemVaultBlockEntity) && !(blockEntity instanceof FluidVaultBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        ItemStack held = context.getItemInHand();
        boolean bulk = player != null && player.isShiftKeyDown();
        boolean creative = player != null && player.getAbilities().instabuild;
        int requested = bulk ? (creative ? Integer.MAX_VALUE : held.getCount()) : 1;
        int applied = 0;
        int newLimit;
        int maxLimit;
        if (blockEntity instanceof ItemVaultBlockEntity vault) {
            int possible = Math.min(requested, Math.max(0, vault.getConfiguredMaxTypes() - vault.getTypeLimit()));
            for (int i = 0; i < possible && vault.increaseTypeLimit(); i++) {
                applied++;
            }
            newLimit = vault.getTypeLimit();
            maxLimit = vault.getConfiguredMaxTypes();
            vault.syncToPlayerIfPresent(player);
        } else if (blockEntity instanceof FluidVaultBlockEntity vault) {
            int possible = Math.min(requested, Math.max(0, vault.getConfiguredMaxTypes() - vault.getTypeLimit()));
            for (int i = 0; i < possible && vault.increaseTypeLimit(); i++) {
                applied++;
            }
            newLimit = vault.getTypeLimit();
            maxLimit = vault.getConfiguredMaxTypes();
            vault.syncToPlayerIfPresent(player);
        } else {
            return InteractionResult.PASS;
        }

        if (applied <= 0) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.skylogistics.chora_nectar.full", maxLimit), true);
            }
            return InteractionResult.CONSUME;
        }

        if (!creative) {
            held.shrink(applied);
        }
        playEffects(level, pos);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.skylogistics.chora_nectar.applied",
                    newLimit, maxLimit), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skylogistics.chora_nectar").withStyle(ChatFormatting.GRAY));
    }

    private static void playEffects(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7F, 1.15F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.55F, 1.35F);
        if (level instanceof ServerLevel serverLevel) {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.9D;
            double z = pos.getZ() + 0.5D;
            serverLevel.sendParticles(ParticleTypes.END_ROD, x, y, z, 20, 0.35D, 0.35D, 0.35D, 0.025D);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.1D, z, 10, 0.3D, 0.18D, 0.3D,
                    0.025D);
        }
    }
}
