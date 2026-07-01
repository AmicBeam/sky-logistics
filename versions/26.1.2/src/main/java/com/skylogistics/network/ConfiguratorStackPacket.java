package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.client.ClientConfiguratorStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfiguratorStackPacket(InteractionHand hand, ItemStack stack) implements CustomPacketPayload {
    public static final Type<ConfiguratorStackPacket> TYPE = new Type<>(SkyLogistics.id("configurator_stack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfiguratorStackPacket> STREAM_CODEC =
            StreamCodec.ofMember(ConfiguratorStackPacket::encode, ConfiguratorStackPacket::decode);

    public static void encode(ConfiguratorStackPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.stack);
    }

    public static ConfiguratorStackPacket decode(RegistryFriendlyByteBuf buffer) {
        return new ConfiguratorStackPacket(buffer.readEnum(InteractionHand.class),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    public static void handle(ConfiguratorStackPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientConfiguratorStack.apply(packet.hand(), packet.stack()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
