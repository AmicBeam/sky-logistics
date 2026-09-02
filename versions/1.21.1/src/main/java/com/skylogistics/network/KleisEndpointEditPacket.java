package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record KleisEndpointEditPacket(BlockPos pos, Direction face, int revision, boolean copy)
        implements CustomPacketPayload {
    public static final Type<KleisEndpointEditPacket> TYPE = new Type<>(SkyLogistics.id("kleis_endpoint_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, KleisEndpointEditPacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buffer) -> {
                buffer.writeBlockPos(packet.pos); buffer.writeEnum(packet.face);
                buffer.writeVarInt(packet.revision); buffer.writeBoolean(packet.copy);
            }, buffer -> new KleisEndpointEditPacket(buffer.readBlockPos(), buffer.readEnum(Direction.class),
                    buffer.readVarInt(), buffer.readBoolean()));

    public static void handle(KleisEndpointEditPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
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
            };
            player.displayClientMessage(message, true);
            data.syncVisibleOverlays(player.getServer(), player.level().dimension(), packet.pos);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
