package com.skylogistics.item;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.util.StackData;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class EulogiaCrystalItem extends Item {
    private static final String CHARGE_SECONDS_TAG = "EulogiaChargeSeconds";
    private static final String DAMAGE_TAG = "Damage";
    private static final int TICKS_PER_SECOND = 20;
    private static final int CHARGED_DAMAGE_VALUE = 1;
    private static final int FULL_BAR_WIDTH = 13;

    public EulogiaCrystalItem(Properties properties) {
        super(properties);
    }

    public static boolean isCharged(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return stack.getItem() instanceof EulogiaCrystalItem
                && tag != null
                && tag.getInt(DAMAGE_TAG) >= CHARGED_DAMAGE_VALUE;
    }

    public static ItemStack chargedStack(Item item) {
        ItemStack stack = new ItemStack(item);
        StackData.update(stack, tag -> tag.putInt(DAMAGE_TAG, CHARGED_DAMAGE_VALUE));
        return stack;
    }

    public static boolean chargeOneSecond(ItemStack stack) {
        if (!(stack.getItem() instanceof EulogiaCrystalItem) || isCharged(stack)) {
            return false;
        }
        CompoundTag tag = StackData.getOrEmpty(stack);
        int chargeSeconds = storedChargeSeconds(tag) + 1;
        int requiredSeconds = SkyLogisticsConfig.eulogiaCrystalChargeSeconds();
        if (chargeSeconds >= requiredSeconds) {
            tag.remove(CHARGE_SECONDS_TAG);
            tag.putInt(DAMAGE_TAG, CHARGED_DAMAGE_VALUE);
            StackData.set(stack, tag);
            return true;
        }
        tag.putInt(CHARGE_SECONDS_TAG, chargeSeconds);
        StackData.set(stack, tag);
        return false;
    }

    private static int storedChargeSeconds(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag == null ? 0 : storedChargeSeconds(tag);
    }

    private static int storedChargeSeconds(CompoundTag tag) {
        if (tag.contains(CHARGE_SECONDS_TAG)) {
            return Math.max(0, tag.getInt(CHARGE_SECONDS_TAG));
        }
        return 0;
    }

    private static float chargeProgress(ItemStack stack) {
        if (isCharged(stack)) {
            return 1.0F;
        }
        int requiredSeconds = Math.max(1, SkyLogisticsConfig.eulogiaCrystalChargeSeconds());
        return Mth.clamp((float) storedChargeSeconds(stack) / (float) requiredSeconds, 0.0F, 1.0F);
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
        return stack.getItem() instanceof EulogiaCrystalItem && !isCharged(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(FULL_BAR_WIDTH * chargeProgress(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x55D6FF;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isCharged(stack)) {
            tooltip.add(Component.translatable("tooltip.skylogistics.eulogia_crystal.charged").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.skylogistics.eulogia_crystal.uncharged",
                    SkyLogisticsConfig.skyRitualMinY()).withStyle(ChatFormatting.GRAY));
        }
    }
}
