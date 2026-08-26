package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.UpgradeCardItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.OrderedMatchingMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OrderedMatchingOffsetPacket(boolean increase) implements CustomPacketPayload {
    public static final Type<OrderedMatchingOffsetPacket> TYPE =
            new Type<>(SkyLogistics.id("ordered_matching_offset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrderedMatchingOffsetPacket> STREAM_CODEC =
            StreamCodec.ofMember(OrderedMatchingOffsetPacket::encode, OrderedMatchingOffsetPacket::decode);

    private static void encode(OrderedMatchingOffsetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.increase);
    }

    private static OrderedMatchingOffsetPacket decode(FriendlyByteBuf buffer) {
        return new OrderedMatchingOffsetPacket(buffer.readBoolean());
    }

    public static void handle(OrderedMatchingOffsetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.isShiftKeyDown()) return;
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.ORDERED_MATCHING_UPGRADE.get())
                    || UpgradeCardItem.orderedMatchingMode(stack) != OrderedMatchingMode.PER_SLOT) return;
            int current = UpgradeCardItem.orderedMatchingOffset(stack);
            int offset = packet.increase
                    ? (current == Integer.MAX_VALUE ? current : current + 1)
                    : (current == Integer.MIN_VALUE ? current : current - 1);
            UpgradeCardItem.setOrderedMatchingOffset(stack, offset);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.sendOverlayMessage(Component.translatable(
                    "message.skylogistics.ordered_matching_upgrade.offset", offset));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
