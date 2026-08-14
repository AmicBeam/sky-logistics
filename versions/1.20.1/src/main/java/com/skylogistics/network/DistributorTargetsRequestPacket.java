package com.skylogistics.network;

import com.skylogistics.block.entity.SkyDistributorBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

public record DistributorTargetsRequestPacket(BlockPos distributorPos) {
    public static void encode(DistributorTargetsRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.distributorPos);
    }

    public static DistributorTargetsRequestPacket decode(FriendlyByteBuf buffer) {
        return new DistributorTargetsRequestPacket(buffer.readBlockPos());
    }

    public static void handle(DistributorTargetsRequestPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !holdsConfigurator(player)
                    || player.distanceToSqr(Vec3.atCenterOf(packet.distributorPos)) > 256.0D
                    || !(player.level().getBlockEntity(packet.distributorPos) instanceof SkyDistributorBlockEntity distributor)) {
                return;
            }
            ModNetworking.sendToPlayer(player, DistributorTargetsPacket.from(packet.distributorPos,
                    distributor.targetSnapshot()));
        });
        context.setPacketHandled(true);
    }

    private static boolean holdsConfigurator(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof ConfiguratorItem
                || player.getOffhandItem().getItem() instanceof ConfiguratorItem;
    }
}
