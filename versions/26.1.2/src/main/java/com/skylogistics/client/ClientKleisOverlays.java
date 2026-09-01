package com.skylogistics.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.network.KleisOverlayPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeFaceMode;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ClientKleisOverlays {
    private static List<KleisOverlayPacket.Entry> entries = List.of();
    private static UUID lineId;
    private static long lastRequest = Long.MIN_VALUE;
    private ClientKleisOverlays() {}

    public static void apply(KleisOverlayPacket packet) { lineId = packet.lineId(); entries = packet.entries(); }
    public static void clear() { lineId = null; entries = List.of(); lastRequest = Long.MIN_VALUE; }

    public static void render(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                || !mc.player.getOffhandItem().is(ModItems.CONFIGURATOR.get())) { clear(); return; }
        UUID selected = ConfiguratorItem.readLineId(mc.player.getOffhandItem());
        if (selected == null) { clear(); return; }
        long now = mc.level.getGameTime();
        if (!selected.equals(lineId)) { lineId = selected; entries = List.of(); lastRequest = Long.MIN_VALUE; }
        if (lastRequest == Long.MIN_VALUE || now - lastRequest >= 20) { lastRequest = now; ModNetworking.requestKleisOverlays(); }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderTypes.lines());
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
        mc.renderBuffers().bufferSource().endBatch(RenderTypes.lines());
    }

    private static void draw(RenderLevelStageEvent.AfterTranslucentParticles event, VertexConsumer consumer, AABB box, Vec3 camera,
            float r, float g, float b, float a) {
        lineBox(event.getPoseStack().last(), consumer, box.move(-camera.x, -camera.y, -camera.z), r, g, b, a);
    }

    private static void lineBox(PoseStack.Pose pose, VertexConsumer c, AABB b, float r, float g, float blue, float a) {
        double x0=b.minX,y0=b.minY,z0=b.minZ,x1=b.maxX,y1=b.maxY,z1=b.maxZ;
        line(c,pose,x0,y0,z0,x1,y0,z0,r,g,blue,a); line(c,pose,x1,y0,z0,x1,y0,z1,r,g,blue,a);
        line(c,pose,x1,y0,z1,x0,y0,z1,r,g,blue,a); line(c,pose,x0,y0,z1,x0,y0,z0,r,g,blue,a);
        line(c,pose,x0,y1,z0,x1,y1,z0,r,g,blue,a); line(c,pose,x1,y1,z0,x1,y1,z1,r,g,blue,a);
        line(c,pose,x1,y1,z1,x0,y1,z1,r,g,blue,a); line(c,pose,x0,y1,z1,x0,y1,z0,r,g,blue,a);
    }
    private static void line(VertexConsumer c, PoseStack.Pose pose, double x0,double y0,double z0,double x1,double y1,double z1,float r,float g,float b,float a) {
        float dx=(float)(x1-x0),dy=(float)(y1-y0),dz=(float)(z1-z0),len=net.minecraft.util.Mth.sqrt(dx*dx+dy*dy+dz*dz);
        if(len<=0)return; dx/=len;dy/=len;dz/=len;
        c.addVertex(pose,(float)x0,(float)y0,(float)z0).setColor(r,g,b,a).setNormal(pose,dx,dy,dz);
        c.addVertex(pose,(float)x1,(float)y1,(float)z1).setColor(r,g,b,a).setNormal(pose,dx,dy,dz);
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
