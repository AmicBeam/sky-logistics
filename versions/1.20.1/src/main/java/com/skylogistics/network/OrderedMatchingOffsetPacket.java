package com.skylogistics.network;

import com.skylogistics.item.UpgradeCardItem;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.OrderedMatchingMode;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record OrderedMatchingOffsetPacket(boolean increase) {
    public static void encode(OrderedMatchingOffsetPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.increase);
    }

    public static OrderedMatchingOffsetPacket decode(FriendlyByteBuf buffer) {
        return new OrderedMatchingOffsetPacket(buffer.readBoolean());
    }

    public static void handle(OrderedMatchingOffsetPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> apply(context.getSender(), packet.increase));
        context.setPacketHandled(true);
    }

    private static void apply(ServerPlayer player, boolean increase) {
        if (player == null || !player.isShiftKeyDown()) return;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.ORDERED_MATCHING_UPGRADE.get())
                || UpgradeCardItem.orderedMatchingMode(stack) != OrderedMatchingMode.PER_SLOT) return;
        int current = UpgradeCardItem.orderedMatchingOffset(stack);
        int offset = increase
                ? (current == Integer.MAX_VALUE ? current : current + 1)
                : (current == Integer.MIN_VALUE ? current : current - 1);
        UpgradeCardItem.setOrderedMatchingOffset(stack, offset);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable(
                "message.skylogistics.ordered_matching_upgrade.offset", offset), true);
    }
}
