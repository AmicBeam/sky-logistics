package com.skylogistics.item;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.network.KleisEndpointSavedData;
import com.skylogistics.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public final class KleisDominionWandItem extends Item {
    public KleisDominionWandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** An offhand configurator overrides the wand's own saved settings. */
    public static InteractionHand configurationHand(Player player) {
        return player.getOffhandItem().is(ModItems.CONFIGURATOR.get())
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    public static ItemStack configurationStack(Player player) {
        return player.getItemInHand(configurationHand(player));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(player.getItemInHand(hand));
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            InteractionHand configHand = configurationHand(player);
            ConfiguratorItem.setPasteMode(configurationStack(player), false);
            ConfiguratorItem.readOrCreate(configurationStack(player), player);
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inventory, ignored) -> new ConfiguratorMenu(id, inventory, configHand),
                            Component.translatable(configHand == InteractionHand.OFF_HAND
                                    ? "menu.skylogistics.configurator" : "item.skylogistics.kleis_dominion_wand")),
                    buffer -> buffer.writeEnum(configHand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack configurator = configurationStack(player);
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;
        KleisEndpointSavedData.ToggleResult result = KleisEndpointSavedData.get(serverPlayer.getServer()).toggle(
                serverPlayer, context.getClickedPos(), context.getClickedFace(), player.isShiftKeyDown(), configurator);
        KleisEndpointSavedData.get(serverPlayer.getServer()).syncVisibleOverlays(serverPlayer.getServer(),
                serverPlayer.level().dimension(), context.getClickedPos());
        if (result == KleisEndpointSavedData.ToggleResult.INVALID_TARGET
                || result == KleisEndpointSavedData.ToggleResult.LINE_CONFLICT
                || result == KleisEndpointSavedData.ToggleResult.EDIT_DENIED) {
            player.displayClientMessage(Component.translatable("message.skylogistics.kleis_dominion_wand."
                    + result.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (SkyLogisticsConfig.enableKleisDominionWandEntityTeleport()
                && !player.level().isClientSide && player instanceof ServerPlayer
                && entity.level() instanceof ServerLevel level) {
            teleportWithEffects(level, entity);
        }
        return true;
    }

    private static void teleportWithEffects(ServerLevel level, Entity entity) {
        Vec3 origin = entity.position();
        double targetY = SkyLogisticsConfig.kleisDominionWandTeleportY();
        entity.teleportTo(origin.x, targetY, origin.z);
        if (entity.level() != level || entity.distanceToSqr(origin.x, targetY, origin.z) > 0.01D) return;
        level.gameEvent(GameEvent.TELEPORT, origin, GameEvent.Context.of(entity));
        if (!entity.isSilent()) {
            level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT,
                    entity.getSoundSource(), 1.0F, 1.0F);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENDERMAN_TELEPORT,
                    entity.getSoundSource(), 1.0F, 1.0F);
        }
        sendTeleportParticles(level, origin.x, origin.y, origin.z, entity);
        sendTeleportParticles(level, entity.getX(), entity.getY(), entity.getZ(), entity);
    }

    private static void sendTeleportParticles(ServerLevel level, double x, double y, double z, Entity entity) {
        level.sendParticles(ParticleTypes.PORTAL, x, y + entity.getBbHeight() * 0.5D, z, 32,
                Math.max(0.2D, entity.getBbWidth() * 0.5D), Math.max(0.3D, entity.getBbHeight() * 0.5D),
                Math.max(0.2D, entity.getBbWidth() * 0.5D), 0.15D);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skylogistics.kleis_dominion_wand")
                .withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.skylogistics.kleis_dominion_wand.cross_dimension")
                .withStyle(ChatFormatting.BLUE));
    }
}
