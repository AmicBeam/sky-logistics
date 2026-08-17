package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DistributorTargetsRequestPacket(BlockPos distributorPos) implements CustomPacketPayload {
    public static final Type<DistributorTargetsRequestPacket> TYPE = new Type<>(SkyLogistics.id("distributor_targets_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DistributorTargetsRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buffer) -> buffer.writeBlockPos(packet.distributorPos),
                    buffer -> new DistributorTargetsRequestPacket(buffer.readBlockPos()));

    public static void handle(DistributorTargetsRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !holdsConfigurator(player)
                    || player.distanceToSqr(Vec3.atCenterOf(packet.distributorPos)) > 256.0D
                    || !(player.level().getBlockEntity(packet.distributorPos) instanceof SkyDistributorBlockEntity distributor)) {
                return;
            }
            ModNetworking.sendToPlayer(player, DistributorTargetsPacket.from(packet.distributorPos,
                    distributor.targetSnapshot()));
        });
    }

    private static boolean holdsConfigurator(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof ConfiguratorItem
                || player.getOffhandItem().getItem() instanceof ConfiguratorItem;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
