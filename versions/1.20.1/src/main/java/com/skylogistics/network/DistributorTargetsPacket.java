package com.skylogistics.network;

import com.skylogistics.block.entity.SkyDistributorBlockEntity.TargetSnapshot;
import com.skylogistics.client.ClientDistributorHighlights;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record DistributorTargetsPacket(BlockPos distributorPos, List<Entry> entries) {
    private static final int MAX_ENTRIES = 64;

    public record Entry(BlockPos pos, int resourceMask) {}

    public static DistributorTargetsPacket from(BlockPos distributorPos, List<TargetSnapshot> targets) {
        return new DistributorTargetsPacket(distributorPos, targets.stream()
                .limit(MAX_ENTRIES).map(target -> new Entry(target.pos(), target.resourceMask())).toList());
    }

    public static void encode(DistributorTargetsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.distributorPos);
        int size = Math.min(packet.entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Entry entry = packet.entries.get(i);
            buffer.writeBlockPos(entry.pos);
            buffer.writeByte(entry.resourceMask & 7);
        }
    }

    public static DistributorTargetsPacket decode(FriendlyByteBuf buffer) {
        int size;
        BlockPos distributorPos = buffer.readBlockPos();
        size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readBlockPos(), buffer.readUnsignedByte() & 7));
        }
        return new DistributorTargetsPacket(distributorPos, List.copyOf(entries));
    }

    public static void handle(DistributorTargetsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientDistributorHighlights.apply(packet)));
        context.setPacketHandled(true);
    }
}
