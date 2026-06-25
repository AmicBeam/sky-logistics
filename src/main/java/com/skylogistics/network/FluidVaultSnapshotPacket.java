package com.skylogistics.network;

import com.skylogistics.client.ClientVaultSnapshots;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record FluidVaultSnapshotPacket(BlockPos pos, int typeLimit, int usedTypes, long totalAmount,
                                       List<Entry> entries) {
    public record Entry(FluidStack stack, long amount) {
    }

    private static final int MAX_ENTRIES = 256;

    public static void encode(FluidVaultSnapshotPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.typeLimit);
        buffer.writeVarInt(packet.usedTypes);
        buffer.writeVarLong(packet.totalAmount);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buffer.writeFluidStack(entry.stack);
            buffer.writeVarLong(entry.amount);
        }
    }

    public static FluidVaultSnapshotPacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int typeLimit = buffer.readVarInt();
        int usedTypes = buffer.readVarInt();
        long totalAmount = buffer.readVarLong();
        int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readFluidStack(), buffer.readVarLong()));
        }
        return new FluidVaultSnapshotPacket(pos, typeLimit, usedTypes, totalAmount, entries);
    }

    public static void handle(FluidVaultSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientVaultSnapshots.apply(packet)));
        context.setPacketHandled(true);
    }
}
