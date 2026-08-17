package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.SkyDistributorBlockEntity.TargetSnapshot;
import com.skylogistics.client.ClientDistributorHighlights;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DistributorTargetsPacket(BlockPos distributorPos, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<DistributorTargetsPacket> TYPE = new Type<>(SkyLogistics.id("distributor_targets"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DistributorTargetsPacket> STREAM_CODEC =
            StreamCodec.ofMember(DistributorTargetsPacket::encode, DistributorTargetsPacket::decode);
    private static final int MAX_ENTRIES = 64;

    public record Entry(BlockPos pos, int resourceMask) {}

    public static DistributorTargetsPacket from(BlockPos distributorPos, List<TargetSnapshot> targets) {
        return new DistributorTargetsPacket(distributorPos, targets.stream()
                .limit(MAX_ENTRIES).map(target -> new Entry(target.pos(), target.resourceMask())).toList());
    }

    private static void encode(DistributorTargetsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.distributorPos);
        int size = Math.min(packet.entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Entry entry = packet.entries.get(i);
            buffer.writeBlockPos(entry.pos);
            buffer.writeByte(entry.resourceMask & 7);
        }
    }

    private static DistributorTargetsPacket decode(FriendlyByteBuf buffer) {
        BlockPos distributorPos = buffer.readBlockPos();
        int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(buffer.readBlockPos(), buffer.readUnsignedByte() & 7));
        return new DistributorTargetsPacket(distributorPos, List.copyOf(entries));
    }

    public static void handle(DistributorTargetsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDistributorHighlights.apply(packet));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
