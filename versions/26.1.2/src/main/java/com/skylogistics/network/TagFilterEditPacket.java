package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.menu.TagFilterListMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TagFilterEditPacket(int slot, String tag) implements CustomPacketPayload {
    public static final Type<TagFilterEditPacket> TYPE = new Type<>(SkyLogistics.id("tag_filter_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TagFilterEditPacket> STREAM_CODEC =
            StreamCodec.ofMember(TagFilterEditPacket::encode, TagFilterEditPacket::decode);

    public static void encode(TagFilterEditPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot);
        buffer.writeUtf(TagFilterListItem.normalizeTag(packet.tag), TagFilterListItem.MAX_TAG_LENGTH);
    }

    public static TagFilterEditPacket decode(FriendlyByteBuf buffer) {
        return new TagFilterEditPacket(buffer.readVarInt(), buffer.readUtf(TagFilterListItem.MAX_TAG_LENGTH));
    }

    public static void handle(TagFilterEditPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof TagFilterListMenu menu)) {
                return;
            }
            menu.setTag(packet.slot, packet.tag);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
