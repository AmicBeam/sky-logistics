package com.skylogistics.network;
import com.skylogistics.SkyLogistics; import com.skylogistics.item.ConfiguratorItem; import com.skylogistics.registry.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.server.level.ServerPlayer; import net.neoforged.neoforge.network.handling.IPayloadContext;
public record KleisOverlayRequestPacket() implements CustomPacketPayload {
 public static final Type<KleisOverlayRequestPacket> TYPE=new Type<>(SkyLogistics.id("kleis_overlay_request"));
 public static final StreamCodec<RegistryFriendlyByteBuf,KleisOverlayRequestPacket> STREAM_CODEC=StreamCodec.unit(new KleisOverlayRequestPacket());
 public static void handle(KleisOverlayRequestPacket p, IPayloadContext c){c.enqueueWork(()->{if(!(c.player() instanceof ServerPlayer player)||!player.getMainHandItem().is(ModItems.KLEIS_DOMINION_WAND.get())||!player.getOffhandItem().is(ModItems.CONFIGURATOR.get()))return; java.util.UUID line=ConfiguratorItem.readLineId(player.getOffhandItem()); if(line!=null)ModNetworking.sendToPlayer(player,KleisOverlayPacket.from(line,KleisEndpointSavedData.get(player.level().getServer()).snapshots(player.level().dimension(),line,player.blockPosition(),64)));});}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
