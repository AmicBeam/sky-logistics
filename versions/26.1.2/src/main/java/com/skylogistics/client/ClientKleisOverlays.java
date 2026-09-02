package com.skylogistics.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.network.KleisOverlayPacket;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.util.NodeFaceMode;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ClientKleisOverlays {
    private static final RenderPipeline XRAY_MASK_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation("pipeline/skylogistics_kleis_xray_mask")
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN, false))
            .withCull(false)
            .build();
    private static final RenderType XRAY_MASK = RenderType.create("skylogistics_kleis_xray_mask",
            RenderSetup.builder(XRAY_MASK_PIPELINE).createRenderSetup());
    private static List<KleisOverlayPacket.Entry> entries = List.of();
    private static UUID lineId;
    private static boolean editNearby;
    private static boolean active;
    private static Object lastRequestLevel;
    private static Object snapshotLevel;
    private static int lastRequestChunkX = Integer.MIN_VALUE;
    private static int lastRequestChunkZ = Integer.MIN_VALUE;
    private static final Map<KleisOverlayPacket.Entry, Long> animationStarts = new HashMap<>();
    private ClientKleisOverlays() {}

    public static void apply(KleisOverlayPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        Map<KleisOverlayPacket.Entry, Long> nextStarts = new HashMap<>();
        for (KleisOverlayPacket.Entry entry : packet.entries()) {
            nextStarts.put(entry, animationStarts.getOrDefault(entry, now));
        }
        animationStarts.clear();
        animationStarts.putAll(nextStarts);
        lineId = packet.selectedLineId();
        editNearby = packet.editNearby();
        entries = packet.entries();
    }
    public static void clear() {
        lineId = null; editNearby = false; active = false; entries = List.of(); animationStarts.clear();
        snapshotLevel = null; resetRequestLocation();
    }

    public static KleisOverlayPacket.Entry entryAt(BlockPos pos, Direction face) {
        for (KleisOverlayPacket.Entry entry : entries) {
            if (entry.pos().equals(pos) && entry.face() == face) return entry;
        }
        return null;
    }

    public static void render(RenderLevelStageEvent.AfterTranslucentParticles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        if (snapshotLevel != mc.level) {
            snapshotLevel = mc.level;
            entries = List.of();
            animationStarts.clear();
            ModNetworking.requestKleisOverlays(true);
        }
        boolean edit = mc.player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                && mc.player.getOffhandItem().is(ModItems.KLEIS_DOMINION_WAND.get());
        boolean currentLine = mc.player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                && mc.player.getOffhandItem().is(ModItems.CONFIGURATOR.get());
        if (!edit && !currentLine) { active = false; resetRequestLocation(); return; }
        UUID selected = ConfiguratorItem.readLineId(edit
                ? mc.player.getMainHandItem() : mc.player.getOffhandItem());
        if (!edit && selected == null) { active = false; resetRequestLocation(); return; }
        if (!active) { active = true; resetRequestLocation(); }
        long now = mc.level.getGameTime();
        if (edit != editNearby || !edit && !java.util.Objects.equals(selected, lineId)) {
            editNearby = edit; lineId = selected; entries = List.of(); animationStarts.clear(); resetRequestLocation();
        } else if (edit) {
            lineId = selected;
        }
        int chunkX = mc.player.blockPosition().getX() >> 4;
        int chunkZ = mc.player.blockPosition().getZ() >> 4;
        if (lastRequestLevel != mc.level || chunkX != lastRequestChunkX || chunkZ != lastRequestChunkZ) {
            lastRequestLevel = mc.level; lastRequestChunkX = chunkX; lastRequestChunkZ = chunkZ;
            ModNetworking.requestKleisOverlays(edit);
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        net.minecraft.world.phys.BlockHitResult hit = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit
                ? blockHit : null;
        renderMasks(event, mc, camera, hit, edit, false);
        renderMasks(event, mc, camera, hit, edit, true);
        renderAnimation(event, mc, camera, hit, edit, now);
    }

    private static void renderMasks(RenderLevelStageEvent.AfterTranslucentParticles event, Minecraft mc, Vec3 camera,
            net.minecraft.world.phys.BlockHitResult hit, boolean edit, boolean xray) {
        RenderType type = xray ? XRAY_MASK : RenderTypes.debugQuads();
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(type);
        for (KleisOverlayPacket.Entry entry : entries) {
            if (!mc.level.isLoaded(entry.pos()) || mc.level.getBlockState(entry.pos()).isAir()) continue;
            if (xray && !mc.level.getBlockState(entry.pos()).is(ModBlocks.SKY_DISTRIBUTOR.get())) continue;
            boolean extract = entry.mode() == NodeFaceMode.INPUT;
            float r = extract ? 1.0F : 0.25F, g = extract ? 0.62F : 0.86F, b = extract ? 0.22F : 0.94F;
            boolean focused = hit != null && hit.getBlockPos().equals(entry.pos()) && hit.getDirection() == entry.face();
            float alphaScale = edit && !focused && lineId != null && !lineId.equals(entry.lineId()) ? 0.45F : 1.0F;
            drawMask(event.getPoseStack().last(), consumer, entry.pos(), entry.face(), camera,
                    r, g, b, 0.22F * alphaScale);
        }
        mc.renderBuffers().bufferSource().endBatch(type);
    }

    private static void renderAnimation(RenderLevelStageEvent.AfterTranslucentParticles event, Minecraft mc, Vec3 camera,
            net.minecraft.world.phys.BlockHitResult hit, boolean edit, long now) {
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderTypes.lines());
        for (KleisOverlayPacket.Entry entry : entries) {
            if (!mc.level.isLoaded(entry.pos()) || mc.level.getBlockState(entry.pos()).isAir()) continue;
            boolean extract = entry.mode() == NodeFaceMode.INPUT;
            float r = extract ? 1.0F : 0.25F, g = extract ? 0.62F : 0.86F, b = extract ? 0.22F : 0.94F;
            boolean focused = hit != null && hit.getBlockPos().equals(entry.pos()) && hit.getDirection() == entry.face();
            float alphaScale = edit && !focused && lineId != null && !lineId.equals(entry.lineId()) ? 0.45F : 1.0F;
            int phase = (int)(Math.floorMod(now - animationStarts.getOrDefault(entry, now), 50L)) / 5;
            if (phase < 8) {
                int size = extract ? 2 + phase * 2 : 16 - phase * 2;
                draw(event, lines, faceBox(entry.pos(), entry.face(), (16 - size) / 32.0D), camera, r, g, b, 0.50F * alphaScale);
            }
        }
        mc.renderBuffers().bufferSource().endBatch(RenderTypes.lines());
    }

    private static void resetRequestLocation() {
        lastRequestLevel = null;
        lastRequestChunkX = Integer.MIN_VALUE;
        lastRequestChunkZ = Integer.MIN_VALUE;
    }

    public static void renderHud(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || mc.screen != null
                || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) return;
        KleisOverlayPacket.Entry entry = entryAt(hit.getBlockPos(), hit.getDirection());
        if (entry == null) return;
        String name = ClientLineNames.displayName(entry.lineId(), entry.lineName());
        Component resources = resourceLabel(entry.resourceMask());
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int centerX = graphics.guiWidth() / 2;
        int firstLineY = graphics.guiHeight() - 69;
        graphics.centeredText(mc.font, name, centerX, firstLineY, 0xFFFFFFFF);
        graphics.centeredText(mc.font, resources, centerX, firstLineY + 10, 0xFFFFFFFF);
    }

    private static Component resourceLabel(int mask) {
        MutableComponent label = Component.empty();
        if ((mask & 1) != 0) label.append(Component.translatable("button.skylogistics.items"));
        if ((mask & 2) != 0) {
            if (!label.getString().isEmpty()) label.append(" · ");
            label.append(Component.translatable("button.skylogistics.fluids"));
        }
        if ((mask & 4) != 0) {
            if (!label.getString().isEmpty()) label.append(" · ");
            label.append(Component.translatable("button.skylogistics.energy"));
        }
        return label.getString().isEmpty() ? Component.literal("-") : label;
    }

    private static void draw(RenderLevelStageEvent.AfterTranslucentParticles event, VertexConsumer consumer, AABB box, Vec3 camera,
            float r, float g, float b, float a) {
        lineBox(event.getPoseStack().last(), consumer, box.move(-camera.x, -camera.y, -camera.z), r, g, b, a);
    }
    private static void drawMask(PoseStack.Pose pose, VertexConsumer c, BlockPos pos, Direction face, Vec3 camera,
            float r, float g, float b, float a) {
        float x0=(float)(pos.getX()-camera.x), y0=(float)(pos.getY()-camera.y), z0=(float)(pos.getZ()-camera.z);
        float x1=x0+1, y1=y0+1, z1=z0+1, o=.002F;
        switch (face) {
            case UP -> quad(c,pose,x0,y1+o,z0,x0,y1+o,z1,x1,y1+o,z1,x1,y1+o,z0,r,g,b,a);
            case DOWN -> quad(c,pose,x0,y0-o,z1,x0,y0-o,z0,x1,y0-o,z0,x1,y0-o,z1,r,g,b,a);
            case NORTH -> quad(c,pose,x1,y0,z0-o,x0,y0,z0-o,x0,y1,z0-o,x1,y1,z0-o,r,g,b,a);
            case SOUTH -> quad(c,pose,x0,y0,z1+o,x1,y0,z1+o,x1,y1,z1+o,x0,y1,z1+o,r,g,b,a);
            case WEST -> quad(c,pose,x0-o,y0,z0,x0-o,y0,z1,x0-o,y1,z1,x0-o,y1,z0,r,g,b,a);
            case EAST -> quad(c,pose,x1+o,y0,z1,x1+o,y0,z0,x1+o,y1,z0,x1+o,y1,z1,r,g,b,a);
        }
    }
    private static void quad(VertexConsumer c, PoseStack.Pose pose,
            float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,
            float r,float g,float blue,float alpha) {
        c.addVertex(pose,ax,ay,az).setColor(r,g,blue,alpha);
        c.addVertex(pose,bx,by,bz).setColor(r,g,blue,alpha);
        c.addVertex(pose,cx,cy,cz).setColor(r,g,blue,alpha);
        c.addVertex(pose,dx,dy,dz).setColor(r,g,blue,alpha);
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
