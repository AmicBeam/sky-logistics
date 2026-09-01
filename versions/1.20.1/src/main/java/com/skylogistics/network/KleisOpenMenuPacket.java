package com.skylogistics.network;

import com.skylogistics.block.entity.KleisVirtualNodeBlockEntity;
import com.skylogistics.menu.KleisDominionWandMenu;
import com.skylogistics.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

public record KleisOpenMenuPacket(BlockPos pos, Direction face) {
    public static void encode(KleisOpenMenuPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos); buffer.writeEnum(packet.face);
    }
    public static KleisOpenMenuPacket decode(FriendlyByteBuf buffer) {
        return new KleisOpenMenuPacket(buffer.readBlockPos(), buffer.readEnum(Direction.class));
    }
    public static void handle(KleisOpenMenuPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())
                    || !player.getOffhandItem().is(ModItems.CONFIGURATOR.get())
                    || player.distanceToSqr(packet.pos.getX() + .5, packet.pos.getY() + .5, packet.pos.getZ() + .5) > 64) return;
            KleisEndpointSavedData.Key key = new KleisEndpointSavedData.Key(player.level().dimension(), packet.pos, packet.face);
            KleisVirtualNodeBlockEntity node = KleisEndpointSavedData.get(player.getServer()).runtimeNode(key);
            if (node == null) return;
            int mask = (node.isItemsEnabled(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION) ? 1 : 0)
                    | (node.isFluidsEnabled(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION) ? 2 : 0)
                    | (node.isEnergyEnabled(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION) ? 4 : 0);
            NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, ignored) ->
                    new KleisDominionWandMenu(id, inv, packet.pos, packet.face, node),
                    Component.translatable("menu.skylogistics.kleis_dominion_wand")), buffer -> {
                        buffer.writeBlockPos(packet.pos); buffer.writeEnum(packet.face); buffer.writeUtf(node.getLineName(), 48);
                        buffer.writeEnum(node.getFaceMode(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION));
                        buffer.writeByte(mask); buffer.writeInt(node.getPriority(KleisVirtualNodeBlockEntity.ENDPOINT_DIRECTION));
                    });
        });
        context.setPacketHandled(true);
    }
}
