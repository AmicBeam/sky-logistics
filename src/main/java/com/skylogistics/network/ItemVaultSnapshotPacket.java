package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientVaultSnapshots;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ItemVaultSnapshotPacket(BlockPos pos, int typeLimit, int usedTypes, long totalAmount,
                                      List<Entry> entries) implements CustomPacketPayload {
    public static final Type<ItemVaultSnapshotPacket> TYPE = new Type<>(SkyLogistics.id("item_vault_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemVaultSnapshotPacket> STREAM_CODEC =
            StreamCodec.ofMember(ItemVaultSnapshotPacket::encode, ItemVaultSnapshotPacket::decode);

    public record Entry(ItemStack stack, long amount) {
    }

    private static final int MAX_ENTRIES = 256;

    public static void encode(ItemVaultSnapshotPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeVarInt(packet.typeLimit);
        buffer.writeVarInt(packet.usedTypes);
        buffer.writeVarLong(packet.totalAmount);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack);
            buffer.writeVarLong(entry.amount);
        }
    }

    public static ItemVaultSnapshotPacket decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int typeLimit = buffer.readVarInt();
        int usedTypes = buffer.readVarInt();
        long totalAmount = buffer.readVarLong();
        int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarLong()));
        }
        return new ItemVaultSnapshotPacket(pos, typeLimit, usedTypes, totalAmount, entries);
    }

    public static void handle(ItemVaultSnapshotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientVaultSnapshots.apply(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
