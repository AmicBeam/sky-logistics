package com.skylogistics.network;
import com.skylogistics.SkyLogistics; import com.skylogistics.client.ClientKleisOverlays; import com.skylogistics.util.NodeFaceMode; import java.util.*; import net.minecraft.core.*; import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.neoforged.neoforge.network.handling.IPayloadContext;
public record KleisOverlayPacket(UUID lineId,List<Entry> entries) implements CustomPacketPayload {
 public record Entry(BlockPos pos,Direction face,NodeFaceMode mode){}
 public static final Type<KleisOverlayPacket> TYPE=new Type<>(SkyLogistics.id("kleis_overlay"));
 public static final StreamCodec<RegistryFriendlyByteBuf,KleisOverlayPacket> STREAM_CODEC=StreamCodec.ofMember(KleisOverlayPacket::encode,KleisOverlayPacket::decode);
 public static KleisOverlayPacket from(UUID line,List<KleisEndpointSavedData.Snapshot>s){return new KleisOverlayPacket(line,s.stream().limit(512).map(e->new Entry(e.pos(),e.face(),e.mode())).toList());}
 private static void encode(KleisOverlayPacket p,RegistryFriendlyByteBuf b){b.writeUUID(p.lineId);b.writeVarInt(p.entries.size());for(Entry e:p.entries){b.writeBlockPos(e.pos);b.writeEnum(e.face);b.writeEnum(e.mode);}}
 private static KleisOverlayPacket decode(RegistryFriendlyByteBuf b){UUID l=b.readUUID();int n=Math.min(512,b.readVarInt());List<Entry>e=new ArrayList<>(n);for(int i=0;i<n;i++)e.add(new Entry(b.readBlockPos(),b.readEnum(Direction.class),b.readEnum(NodeFaceMode.class)));return new KleisOverlayPacket(l,List.copyOf(e));}
 public static void handle(KleisOverlayPacket p,IPayloadContext c){c.enqueueWork(()->ClientKleisOverlays.apply(p));}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
