package com.skylogistics.item;

import com.skylogistics.config.SkyLogisticsConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class EulogiaCrystalItem extends Item {
    private static final String CHARGE_SECONDS_TAG = "EulogiaChargeSeconds";
    private static final int TICKS_PER_SECOND = 20;
    private static final int CHARGED_DAMAGE_VALUE = 1;

    public EulogiaCrystalItem(Properties properties) {
        super(properties);
    }

    public static boolean isCharged(ItemStack stack) {
        return stack.getItem() instanceof EulogiaCrystalItem && stack.getDamageValue() >= CHARGED_DAMAGE_VALUE;
    }

    public static boolean chargeOneSecond(ItemStack stack) {
        if (!(stack.getItem() instanceof EulogiaCrystalItem) || isCharged(stack)) {
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        int chargeSeconds = storedChargeSeconds(tag) + 1;
        int requiredSeconds = SkyLogisticsConfig.eulogiaCrystalChargeSeconds();
        if (chargeSeconds >= requiredSeconds) {
            tag.remove(CHARGE_SECONDS_TAG);
            stack.setDamageValue(CHARGED_DAMAGE_VALUE);
            return true;
        }
        tag.putInt(CHARGE_SECONDS_TAG, chargeSeconds);
        return false;
    }

    private static int storedChargeSeconds(CompoundTag tag) {
        if (tag.contains(CHARGE_SECONDS_TAG)) {
            return Math.max(0, tag.getInt(CHARGE_SECONDS_TAG));
        }
        return 0;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player) || player.blockPosition().getY() < SkyLogisticsConfig.skyRitualMinY()) {
            return;
        }
        if (level.getGameTime() % TICKS_PER_SECOND != 0L) {
            return;
        }
        if (chargeOneSecond(stack) && player instanceof ServerPlayer) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.8F, 1.4F);
            player.displayClientMessage(Component.translatable("message.skylogistics.eulogia_crystal.charged"), true);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (isCharged(stack)) {
            tooltip.add(Component.translatable("tooltip.skylogistics.eulogia_crystal.charged").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.eulogia_crystal.uncharged",
                    SkyLogisticsConfig.skyRitualMinY()).withStyle(ChatFormatting.GRAY));
        }
    }
}
