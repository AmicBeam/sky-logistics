package com.skylogistics.item;

import com.skylogistics.SkyLogistics;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.registry.ModDataComponents;
import com.skylogistics.util.StackData;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class EulogiaCrystalItem extends Item {
    private static final String SKYLOGISTICS_TAG = "skylogistics";
    private static final String CHARGE_SECONDS_TAG = "eulogia_charge_seconds";
    private static final String LEGACY_CHARGED_TAG = "eulogia_charged";
    private static final int TICKS_PER_SECOND = 20;
    private static final int FULL_BAR_WIDTH = 13;
    private static final Identifier CHARGED_ITEM_MODEL = SkyLogistics.id("eulogia_crystal_charged");

    public EulogiaCrystalItem(Properties properties) {
        super(properties);
    }

    public static boolean isCharged(ItemStack stack) {
        return stack.getItem() instanceof EulogiaCrystalItem
                && Boolean.TRUE.equals(stack.get(ModDataComponents.EULOGIA_CHARGED.get()));
    }

    public static ItemStack chargedStack(Item item) {
        ItemStack stack = new ItemStack(item);
        setCharged(stack, StackData.getOrEmpty(stack));
        return stack;
    }

    public static boolean chargeOneSecond(ItemStack stack) {
        if (!(stack.getItem() instanceof EulogiaCrystalItem) || isCharged(stack)) {
            return false;
        }
        clearUnchargedComponents(stack);
        CompoundTag tag = StackData.getOrEmpty(stack);
        clearLegacyChargedData(tag);
        int chargeSeconds = storedChargeSeconds(tag) + 1;
        int requiredSeconds = SkyLogisticsConfig.eulogiaCrystalChargeSeconds();
        if (chargeSeconds >= requiredSeconds) {
            setCharged(stack, tag);
            return true;
        }
        skyDataForWrite(tag).putInt(CHARGE_SECONDS_TAG, chargeSeconds);
        StackData.set(stack, tag);
        return false;
    }

    private static int storedChargeSeconds(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
        return tag == null ? 0 : storedChargeSeconds(tag);
    }

    private static int storedChargeSeconds(CompoundTag tag) {
        CompoundTag data = skyData(tag);
        if (data.contains(CHARGE_SECONDS_TAG)) {
            return Math.max(0, data.getIntOr(CHARGE_SECONDS_TAG, 0));
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
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (isCharged(stack)) {
            ensureChargedComponents(stack);
            return;
        }
        clearUnchargedComponents(stack);
        if (player.blockPosition().getY() < SkyLogisticsConfig.skyRitualMinY()) {
            return;
        }
        if (level.getGameTime() % TICKS_PER_SECOND != 0L) {
            return;
        }
        if (chargeOneSecond(stack) && player instanceof ServerPlayer) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.8F, 1.4F);
            player.sendOverlayMessage(Component.translatable("message.skylogistics.eulogia_crystal.charged"));
        }
    }

    private static void setCharged(ItemStack stack, CompoundTag tag) {
        stack.remove(DataComponents.DAMAGE);
        tag.remove("Damage");
        CompoundTag data = skyData(tag);
        data.remove(CHARGE_SECONDS_TAG);
        data.remove(LEGACY_CHARGED_TAG);
        if (data.isEmpty()) {
            tag.remove(SKYLOGISTICS_TAG);
        }
        StackData.set(stack, tag);
        ensureChargedComponents(stack);
    }

    private static void ensureChargedComponents(ItemStack stack) {
        stack.remove(DataComponents.DAMAGE);
        stack.set(ModDataComponents.EULOGIA_CHARGED.get(), true);
        ensureChargedModel(stack);
    }

    private static void ensureChargedModel(ItemStack stack) {
        if (!CHARGED_ITEM_MODEL.equals(stack.get(DataComponents.ITEM_MODEL))) {
            stack.set(DataComponents.ITEM_MODEL, CHARGED_ITEM_MODEL);
        }
    }

    private static void clearUnchargedComponents(ItemStack stack) {
        stack.remove(DataComponents.DAMAGE);
        stack.remove(ModDataComponents.EULOGIA_CHARGED.get());
        if (CHARGED_ITEM_MODEL.equals(stack.get(DataComponents.ITEM_MODEL))) {
            stack.remove(DataComponents.ITEM_MODEL);
        }
    }

    private static CompoundTag skyData(CompoundTag tag) {
        return tag.getCompoundOrEmpty(SKYLOGISTICS_TAG);
    }

    private static CompoundTag skyDataForWrite(CompoundTag tag) {
        CompoundTag data = tag.getCompoundOrEmpty(SKYLOGISTICS_TAG);
        tag.put(SKYLOGISTICS_TAG, data);
        return data;
    }

    private static void clearLegacyChargedData(CompoundTag tag) {
        CompoundTag data = skyData(tag);
        data.remove(LEGACY_CHARGED_TAG);
        if (data.isEmpty()) {
            tag.remove(SKYLOGISTICS_TAG);
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

    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (isCharged(stack)) {
            tooltip.accept(Component.translatable("tooltip.skylogistics.eulogia_crystal.charged").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.accept(Component.translatable("tooltip.skylogistics.eulogia_crystal.uncharged",
                    SkyLogisticsConfig.skyRitualMinY()).withStyle(ChatFormatting.GRAY));
        }
    }
}
