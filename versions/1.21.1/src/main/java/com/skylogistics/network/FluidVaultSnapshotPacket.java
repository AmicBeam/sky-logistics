package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientVaultSnapshots;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FluidVaultSnapshotPacket(BlockPos pos, int typeLimit, int usedTypes, long totalAmount,
                                       List<Entry> entries) implements CustomPacketPayload {
    public static final Type<FluidVaultSnapshotPacket> TYPE = new Type<>(SkyLogistics.id("fluid_vault_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidVaultSnapshotPacket> STREAM_CODEC =
            StreamCodec.ofMember(FluidVaultSnapshotPacket::encode, FluidVaultSnapshotPacket::decode);

    public record Entry(FluidStack stack, long amount) {
    }

    private static final int MAX_ENTRIES = 256;

    public static void encode(FluidVaultSnapshotPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.typeLimit);
        buffer.writeVarInt(packet.usedTypes);
        buffer.writeVarLong(packet.totalAmount);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack);
            buffer.writeVarLong(entry.amount);
        }
    }

    public static FluidVaultSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int typeLimit = buffer.readVarInt();
        int usedTypes = buffer.readVarInt();
        long totalAmount = buffer.readVarLong();
        int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarLong()));
        }
        return new FluidVaultSnapshotPacket(pos, typeLimit, usedTypes, totalAmount, entries);
    }

    public static void handle(FluidVaultSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientVaultSnapshots.apply(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
