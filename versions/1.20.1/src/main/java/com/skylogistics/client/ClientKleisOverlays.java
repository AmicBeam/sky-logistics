package com.skylogistics.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.network.KleisOverlayPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeFaceMode;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class ClientKleisOverlays {
    private static List<KleisOverlayPacket.Entry> entries = List.of();
    private static UUID lineId;
    private static long lastRequest = Long.MIN_VALUE;
    private ClientKleisOverlays() {}

    public static void apply(KleisOverlayPacket packet) { lineId = packet.lineId(); entries = packet.entries(); }
    public static void clear() { lineId = null; entries = List.of(); lastRequest = Long.MIN_VALUE; }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                || !mc.player.getOffhandItem().is(ModItems.CONFIGURATOR.get())) { clear(); return; }
        UUID selected = ConfiguratorItem.readLineId(mc.player.getOffhandItem());
        if (selected == null) { clear(); return; }
        long now = mc.level.getGameTime();
        if (!selected.equals(lineId)) { lineId = selected; entries = List.of(); lastRequest = Long.MIN_VALUE; }
        if (lastRequest == Long.MIN_VALUE || now - lastRequest >= 20) { lastRequest = now; ModNetworking.requestKleisOverlays(); }
        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        int phase = (int)(now % 50L) / 5;
        for (KleisOverlayPacket.Entry entry : entries) {
            if (!mc.level.isLoaded(entry.pos()) || mc.level.getBlockState(entry.pos()).isAir()) continue;
            boolean extract = entry.mode() == NodeFaceMode.INPUT;
            float r = extract ? 1.0F : 0.25F, g = extract ? 0.62F : 0.86F, b = extract ? 0.22F : 0.94F;
            draw(event, lines, faceBox(entry.pos(), entry.face(), 0), camera, r, g, b, 0.85F);
            if (phase < 8) {
                int size = extract ? 2 + phase * 2 : 16 - phase * 2;
                draw(event, lines, faceBox(entry.pos(), entry.face(), (16 - size) / 32.0D), camera, r, g, b, 0.50F);
            }
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void draw(RenderLevelStageEvent event, VertexConsumer consumer, AABB box, Vec3 camera,
            float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(event.getPoseStack(), consumer, box.minX-camera.x, box.minY-camera.y, box.minZ-camera.z,
                box.maxX-camera.x, box.maxY-camera.y, box.maxZ-camera.z, r,g,b,a);
    }

    private static AABB faceBox(BlockPos pos, Direction face, double inset) {
        double x=pos.getX(), y=pos.getY(), z=pos.getZ(), lo=inset, hi=1-inset, o=.002, d=.001;
        return switch(face) {
            case UP -> new AABB(x+lo,y+1+o,z+lo,x+hi,y+1+o+d,z+hi);
            case DOWN -> new AABB(x+lo,y-o-d,z+lo,x+hi,y-o,z+hi);
            case NORTH -> new AABB(x+lo,y+lo,z-o-d,x+hi,y+hi,z-o);
            case SOUTH -> new AABB(x+lo,y+lo,z+1+o,x+hi,y+hi,z+1+o+d);
            case WEST -> new AABB(x-o-d,y+lo,z+lo,x-o,y+hi,z+hi);
            case EAST -> new AABB(x+1+o,y+lo,z+lo,x+1+o+d,y+hi,z+hi);
        };
    }
}
