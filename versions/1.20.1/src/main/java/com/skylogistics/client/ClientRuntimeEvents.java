package com.skylogistics.client;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.UpgradeCardItem;
import com.skylogistics.network.KleisEndpointPolicy;
import com.skylogistics.network.KleisOverlayPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.OrderedMatchingMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SkyLogistics.MOD_ID, value = Dist.CLIENT)
public final class ClientRuntimeEvents {
    private ClientRuntimeEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLineNames.clear();
        ClientDistributorHighlights.clear();
        ClientKleisOverlays.clear();
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isShiftKeyDown() || event.getScrollDelta() == 0.0D) return;
        var stack = minecraft.player.getMainHandItem();
        if (!stack.is(ModItems.ORDERED_MATCHING_UPGRADE.get())
                || UpgradeCardItem.orderedMatchingMode(stack) != OrderedMatchingMode.PER_SLOT) return;
        ModNetworking.sendOrderedMatchingOffset(event.getScrollDelta() > 0.0D);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isAttack() || minecraft.player == null
                || !(minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) return;
        boolean mainHandWand = minecraft.player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get());
        boolean offhandConfigurator = minecraft.player.getOffhandItem().is(ModItems.CONFIGURATOR.get());
        boolean mainHandConfigurator = minecraft.player.getMainHandItem().is(ModItems.CONFIGURATOR.get());
        boolean offhandWand = minecraft.player.getOffhandItem().is(ModItems.KLEIS_DOMINION_WAND.get());
        boolean canOpen = KleisEndpointPolicy.canOpenEndpointFromHands(mainHandWand, offhandConfigurator,
                mainHandConfigurator, offhandWand);
        if (!mainHandWand && !canOpen) return;
        if (mainHandConfigurator && ClientKleisOverlays.entryAt(hit.getBlockPos(), hit.getDirection()) == null) return;
        event.setCanceled(true);
        if (canOpen) {
            ModNetworking.openKleisEndpoint(hit.getBlockPos(), hit.getDirection());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKleisEndpointEdit(PlayerInteractEvent.RightClickBlock event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                || minecraft.player == null || minecraft.level == null
                || !minecraft.player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                || !minecraft.player.getOffhandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                || minecraft.level.getBlockEntity(event.getPos()) instanceof com.skylogistics.block.entity.SkyNodeBlockEntity) {
            return;
        }
        KleisOverlayPacket.Entry endpoint = ClientKleisOverlays.entryAt(event.getPos(), event.getFace());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        int revision = endpoint == null ? -1 : endpoint.revision();
        if (minecraft.player.isShiftKeyDown()) {
            ModNetworking.editKleisEndpoint(event.getPos(), event.getFace(), revision, true);
        } else if (ConfiguratorItem.isPasteMode(minecraft.player.getMainHandItem())) {
            ModNetworking.editKleisEndpoint(event.getPos(), event.getFace(), revision, false);
        } else {
            ModNetworking.openKleisEndpoint(event.getPos(), event.getFace());
        }
    }

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !(minecraft.player.getMainHandItem().getItem() instanceof ConfiguratorItem)
                        && !(minecraft.player.getOffhandItem().getItem() instanceof ConfiguratorItem)) {
            return;
        }
        BlockPos distributorPos = event.getTarget().getBlockPos();
        if (!minecraft.level.getBlockState(distributorPos).is(ModBlocks.SKY_DISTRIBUTOR.get())) return;

        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        for (var target : ClientDistributorHighlights.targets(distributorPos)) {
            BlockPos pos = target.pos();
            LevelRenderer.renderLineBox(event.getPoseStack(), lines,
                    pos.getX() - camera.x - 0.002D, pos.getY() - camera.y - 0.002D,
                    pos.getZ() - camera.z - 0.002D, pos.getX() - camera.x + 1.002D,
                    pos.getY() - camera.y + 1.002D, pos.getZ() - camera.z + 1.002D,
                    0.25F, 0.95F, 0.90F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) { ClientKleisOverlays.render(event); }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientKleisOverlays.renderHud(event.getGuiGraphics());
    }
}
