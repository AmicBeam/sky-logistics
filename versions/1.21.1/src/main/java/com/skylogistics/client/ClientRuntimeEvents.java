package com.skylogistics.client;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.registry.ModBlocks;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = SkyLogistics.MOD_ID, value = Dist.CLIENT)
public final class ClientRuntimeEvents {
    private ClientRuntimeEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientLineNames.clear();
        ClientDistributorHighlights.clear();
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
}
