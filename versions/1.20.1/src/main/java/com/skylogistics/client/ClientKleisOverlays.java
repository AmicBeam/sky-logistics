package com.skylogistics.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.network.KleisOverlayPacket;
import com.skylogistics.network.KleisEndpointPolicy;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeFaceMode;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

public final class ClientKleisOverlays {
    private static final RenderType XRAY_CENTER = XrayRenderType.createCenter();
    private static List<KleisOverlayPacket.Entry> entries = List.of();
    private static UUID lineId;
    private static boolean editNearby;
    private static boolean active;
    private static Object lastRequestLevel;
    private static Object snapshotLevel;
    private static int lastRequestChunkX = Integer.MIN_VALUE;
    private static int lastRequestChunkZ = Integer.MIN_VALUE;
    private static final Map<BlockPos, Integer> centerModes = new HashMap<>();
    private ClientKleisOverlays() {}

    public static void apply(KleisOverlayPacket packet) {
        lineId = packet.selectedLineId();
        editNearby = packet.editNearby();
        entries = packet.entries();
    }
    public static void clear() {
        lineId = null; editNearby = false; active = false; entries = List.of(); centerModes.clear();
        snapshotLevel = null; resetRequestLocation();
    }

    public static KleisOverlayPacket.Entry entryAt(BlockPos pos, Direction face) {
        for (KleisOverlayPacket.Entry entry : entries) {
            if (entry.pos().equals(pos) && entry.face() == face) return entry;
        }
        return null;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        if (snapshotLevel != mc.level) {
            snapshotLevel = mc.level;
            entries = List.of();
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
            editNearby = edit; lineId = selected; entries = List.of(); resetRequestLocation();
        } else if (edit) {
            lineId = selected;
        }
        int chunkX = mc.player.blockPosition().getX() >> 4;
        int chunkZ = mc.player.blockPosition().getZ() >> 4;
        if (lastRequestLevel != mc.level || chunkX != lastRequestChunkX || chunkZ != lastRequestChunkZ) {
            lastRequestLevel = mc.level; lastRequestChunkX = chunkX; lastRequestChunkZ = chunkZ;
            ModNetworking.requestKleisOverlays(edit);
        }
        Vec3 camera = event.getCamera().getPosition();
        net.minecraft.world.phys.BlockHitResult hit = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit
                ? blockHit : null;
        renderMasks(event, mc, camera, hit, edit);
        renderXrayCenters(event, mc, camera, hit, edit);
        renderAnimation(event, mc, camera, hit, edit, now);
    }

    private static void renderMasks(RenderLevelStageEvent event, Minecraft mc, Vec3 camera,
            net.minecraft.world.phys.BlockHitResult hit, boolean edit) {
        RenderType type = RenderType.debugFilledBox();
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(type);
        for (KleisOverlayPacket.Entry entry : entries) {
            if (!mc.level.isLoaded(entry.pos()) || mc.level.getBlockState(entry.pos()).isAir()) continue;
            boolean extract = entry.mode() == NodeFaceMode.INPUT;
            float r = extract ? 1.0F : 0.25F, g = extract ? 0.62F : 0.86F, b = extract ? 0.22F : 0.94F;
            boolean focused = hit != null && hit.getBlockPos().equals(entry.pos()) && hit.getDirection() == entry.face();
            float alphaScale = edit && !focused && lineId != null && !lineId.equals(entry.lineId()) ? 0.45F : 1.0F;
            AABB mask = faceBox(entry.pos(), entry.face(), 0);
            LevelRenderer.addChainedFilledBoxVertices(event.getPoseStack(), consumer,
                    mask.minX-camera.x, mask.minY-camera.y, mask.minZ-camera.z,
                    mask.maxX-camera.x, mask.maxY-camera.y, mask.maxZ-camera.z,
                    r, g, b, 0.22F * alphaScale);
        }
        mc.renderBuffers().bufferSource().endBatch(type);
    }

    private static void renderXrayCenters(RenderLevelStageEvent event, Minecraft mc, Vec3 camera,
            net.minecraft.world.phys.BlockHitResult hit, boolean edit) {
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(XRAY_CENTER);
        centerModes.clear();
        Set<BlockPos> currentLineBlocks = new HashSet<>();
        for (KleisOverlayPacket.Entry entry : entries) {
            centerModes.merge(entry.pos(), entry.mode() == NodeFaceMode.INPUT ? 1 : 2,
                    (mask, ignored) -> KleisEndpointPolicy.addEndpointMode(mask,
                            entry.mode() == NodeFaceMode.INPUT));
            if (lineId != null && lineId.equals(entry.lineId())) currentLineBlocks.add(entry.pos());
        }
        for (Map.Entry<BlockPos, Integer> center : centerModes.entrySet()) {
            BlockPos pos = center.getKey();
            if (!mc.level.isLoaded(pos) || mc.level.getBlockState(pos).isAir()) continue;
            boolean mixed = KleisEndpointPolicy.hasMixedEndpointModes(center.getValue());
            boolean extract = (center.getValue() & 1) != 0;
            float r = mixed ? 0.78F : extract ? 1.0F : 0.25F;
            float g = mixed ? 0.64F : extract ? 0.62F : 0.86F;
            float b = mixed ? 1.0F : extract ? 0.22F : 0.94F;
            boolean focused = hit != null && hit.getBlockPos().equals(pos);
            boolean current = currentLineBlocks.contains(pos);
            float alphaScale = edit && !focused && lineId != null && !current ? 0.45F : 1.0F;
            drawCenterCube(event.getPoseStack().last().pose(), consumer, pos, camera,
                    r, g, b, 0.50F * alphaScale);
        }
        mc.renderBuffers().bufferSource().endBatch(XRAY_CENTER);
    }

    private static void renderAnimation(RenderLevelStageEvent event, Minecraft mc, Vec3 camera,
            net.minecraft.world.phys.BlockHitResult hit, boolean edit, long now) {
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        int phase = (int)(Math.floorMod(now, 50L)) / 5;
        for (KleisOverlayPacket.Entry entry : entries) {
            if (!mc.level.isLoaded(entry.pos()) || mc.level.getBlockState(entry.pos()).isAir()) continue;
            boolean extract = entry.mode() == NodeFaceMode.INPUT;
            float r = extract ? 1.0F : 0.25F, g = extract ? 0.62F : 0.86F, b = extract ? 0.22F : 0.94F;
            boolean focused = hit != null && hit.getBlockPos().equals(entry.pos()) && hit.getDirection() == entry.face();
            float alphaScale = edit && !focused && lineId != null && !lineId.equals(entry.lineId()) ? 0.45F : 1.0F;
            if (phase < 8) {
                int size = extract ? 2 + phase * 2 : 16 - phase * 2;
                draw(event, lines, faceBox(entry.pos(), entry.face(), (16 - size) / 32.0D), camera, r, g, b, 0.50F * alphaScale);
            }
        }
        mc.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void resetRequestLocation() {
        lastRequestLevel = null;
        lastRequestChunkX = Integer.MIN_VALUE;
        lastRequestChunkZ = Integer.MIN_VALUE;
    }

    public static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || mc.screen != null
                || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) return;
        KleisOverlayPacket.Entry entry = entryAt(hit.getBlockPos(), hit.getDirection());
        if (entry == null) return;
        String name = ClientLineNames.displayName(entry.lineId(), entry.lineName());
        Component resources = resourceLabel(entry.resourceMask());
        int centerX = graphics.guiWidth() / 2;
        int firstLineY = graphics.guiHeight() - 69;
        graphics.drawCenteredString(mc.font, name, centerX, firstLineY, 0xFFFFFFFF);
        graphics.drawCenteredString(mc.font, resources, centerX, firstLineY + 10, 0xFFFFFFFF);
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

    private static void draw(RenderLevelStageEvent event, VertexConsumer consumer, AABB box, Vec3 camera,
            float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(event.getPoseStack(), consumer, box.minX-camera.x, box.minY-camera.y, box.minZ-camera.z,
                box.maxX-camera.x, box.maxY-camera.y, box.maxZ-camera.z, r,g,b,a);
    }

    private static void drawCenterCube(Matrix4f matrix, VertexConsumer consumer, BlockPos pos, Vec3 camera,
            float r, float g, float b, float a) {
        float x0=(float)(pos.getX()-camera.x)+.25F, y0=(float)(pos.getY()-camera.y)+.25F, z0=(float)(pos.getZ()-camera.z)+.25F;
        float x1=x0+.5F, y1=y0+.5F, z1=z0+.5F;
        quad(consumer,matrix,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,r,g,b,a,0,-1,0);
        quad(consumer,matrix,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0,r,g,b,a,0,1,0);
        quad(consumer,matrix,x0,y0,z0,x0,y1,z0,x1,y1,z0,x1,y0,z0,r,g,b,a,0,0,-1);
        quad(consumer,matrix,x1,y0,z1,x1,y1,z1,x0,y1,z1,x0,y0,z1,r,g,b,a,0,0,1);
        quad(consumer,matrix,x0,y0,z1,x0,y1,z1,x0,y1,z0,x0,y0,z0,r,g,b,a,-1,0,0);
        quad(consumer,matrix,x1,y0,z0,x1,y1,z0,x1,y1,z1,x1,y0,z1,r,g,b,a,1,0,0);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
            float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,
            float r,float g,float b,float a,float nx,float ny,float nz) {
        consumer.vertex(matrix,ax,ay,az).color(r,g,b,a).normal(nx,ny,nz).endVertex();
        consumer.vertex(matrix,bx,by,bz).color(r,g,b,a).normal(nx,ny,nz).endVertex();
        consumer.vertex(matrix,cx,cy,cz).color(r,g,b,a).normal(nx,ny,nz).endVertex();
        consumer.vertex(matrix,dx,dy,dz).color(r,g,b,a).normal(nx,ny,nz).endVertex();
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

    private abstract static class XrayRenderType extends RenderType {
        private XrayRenderType() {
            super("unused", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.QUADS,
                    0, false, false, () -> {}, () -> {});
        }

        private static RenderType createCenter() {
            return RenderType.create("skylogistics_kleis_xray_center", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    VertexFormat.Mode.QUADS, 65536, false, false, RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setTextureState(NO_TEXTURE)
                            .setLightmapState(NO_LIGHTMAP)
                            .setDepthTestState(GREATER_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }
}
