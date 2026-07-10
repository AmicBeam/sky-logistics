package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientConfiguratorLineDetails;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.RedstoneControl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfiguratorLineDetailsPacket(UUID lineId, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<ConfiguratorLineDetailsPacket> TYPE = new Type<>(SkyLogistics.id("configurator_line_details"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorLineDetailsPacket> STREAM_CODEC =
            StreamCodec.ofMember(ConfiguratorLineDetailsPacket::encode, ConfiguratorLineDetailsPacket::decode);

    private static final int MAX_ENTRIES = 64;
    private static final int MAX_TEXTURE_PROPERTY_LENGTH = 4096;

    public record Entry(String dimension, BlockPos nodePos, Direction face, BlockPos targetPos,
                        String targetBlockId, String displayName, UUID profileId, String profileTexture,
                        String profileTextureSignature, NodeFaceMode mode, boolean itemsEnabled, boolean fluidsEnabled,
                        boolean energyEnabled, RedstoneControl redstoneControl, int priority) {
        public Entry {
            profileTexture = profileTexture == null ? "" : profileTexture;
            profileTextureSignature = profileTextureSignature == null ? "" : profileTextureSignature;
        }
    }

    public static void encode(ConfiguratorLineDetailsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.lineId);
        int size = Math.min(packet.entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            Entry entry = packet.entries.get(i);
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeBlockPos(entry.nodePos);
            buffer.writeEnum(entry.face);
            buffer.writeBlockPos(entry.targetPos);
            buffer.writeUtf(entry.targetBlockId, 128);
            buffer.writeUtf(entry.displayName, 128);
            buffer.writeBoolean(entry.profileId != null);
            if (entry.profileId != null) {
                buffer.writeUUID(entry.profileId);
            }
            buffer.writeUtf(entry.profileTexture, MAX_TEXTURE_PROPERTY_LENGTH);
            buffer.writeUtf(entry.profileTextureSignature, MAX_TEXTURE_PROPERTY_LENGTH);
            buffer.writeEnum(entry.mode);
            buffer.writeBoolean(entry.itemsEnabled);
            buffer.writeBoolean(entry.fluidsEnabled);
            buffer.writeBoolean(entry.energyEnabled);
            buffer.writeEnum(entry.redstoneControl);
            buffer.writeVarInt(entry.priority);
        }
    }

    public static ConfiguratorLineDetailsPacket decode(FriendlyByteBuf buffer) {
        UUID lineId = buffer.readUUID();
        int size = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String dimension = buffer.readUtf(128);
            BlockPos nodePos = buffer.readBlockPos();
            Direction face = buffer.readEnum(Direction.class);
            BlockPos targetPos = buffer.readBlockPos();
            String targetBlockId = buffer.readUtf(128);
            String displayName = buffer.readUtf(128);
            UUID profileId = buffer.readBoolean() ? buffer.readUUID() : null;
            String profileTexture = buffer.readUtf(MAX_TEXTURE_PROPERTY_LENGTH);
            String profileTextureSignature = buffer.readUtf(MAX_TEXTURE_PROPERTY_LENGTH);
            entries.add(new Entry(dimension, nodePos, face, targetPos, targetBlockId, displayName,
                    profileId, profileTexture, profileTextureSignature, buffer.readEnum(NodeFaceMode.class),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readEnum(RedstoneControl.class), buffer.readVarInt()));
        }
        return new ConfiguratorLineDetailsPacket(lineId, entries);
    }

    public static void handle(ConfiguratorLineDetailsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientConfiguratorLineDetails.apply(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
