package com.skylogistics.network;

import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record KleisEndpointEditPacket(BlockPos pos, Direction face, int revision, boolean copy) {
    public static void encode(KleisEndpointEditPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos); buffer.writeEnum(packet.face);
        buffer.writeVarInt(packet.revision); buffer.writeBoolean(packet.copy);
    }

    public static KleisEndpointEditPacket decode(FriendlyByteBuf buffer) {
        return new KleisEndpointEditPacket(buffer.readBlockPos(), buffer.readEnum(Direction.class),
                buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(KleisEndpointEditPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                    || !player.getOffhandItem().is(ModItems.KLEIS_DOMINION_WAND.get())) return;
            ItemStack configurator = player.getMainHandItem();
            KleisEndpointSavedData data = KleisEndpointSavedData.get(player.getServer());
            KleisEndpointSavedData.Key key = new KleisEndpointSavedData.Key(
                    player.level().dimension(), packet.pos, packet.face);
            KleisEndpointSavedData.EditResult result = packet.copy
                    ? data.copyToConfigurator(player, key, packet.revision, configurator)
                    : data.pasteFromConfigurator(player, key, packet.revision, configurator);
            ConfiguratorItem.ToolConfig config = ConfiguratorItem.read(configurator);
            Component message = switch (result) {
                case COPIED -> Component.translatable("message.skylogistics.configurator.copied_paste",
                        config == null ? "?" : config.lineName());
                case PASTED -> Component.translatable("message.skylogistics.configurator.pasted",
                        config == null ? "?" : config.lineName());
                case NO_CONFIG -> Component.translatable("message.skylogistics.kleis_dominion_wand.no_paste_config");
                case STALE -> Component.translatable("message.skylogistics.kleis_dominion_wand.stale_endpoint");
                case DENIED -> Component.translatable("message.skylogistics.kleis_dominion_wand.edit_denied");
                case INVALID_TARGET -> Component.translatable("message.skylogistics.kleis_dominion_wand.invalid_target");
            };
            player.displayClientMessage(message, true);
            data.syncVisibleOverlays(player.getServer(), player.level().dimension(), packet.pos);
        });
        context.setPacketHandled(true);
    }
}
