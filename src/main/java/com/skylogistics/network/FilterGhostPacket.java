package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.menu.FilterListMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FilterGhostPacket(int slot, ItemStack item, FluidStack fluid, boolean fluidEntry) implements CustomPacketPayload {
    public static final Type<FilterGhostPacket> TYPE = new Type<>(SkyLogistics.id("filter_ghost"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterGhostPacket> STREAM_CODEC =
            StreamCodec.ofMember(FilterGhostPacket::encode, FilterGhostPacket::decode);

    public static FilterGhostPacket item(int slot, ItemStack item) {
        ItemStack copy = item.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
        }
        return new FilterGhostPacket(slot, copy, FluidStack.EMPTY, false);
    }

    public static FilterGhostPacket fluid(int slot, FluidStack fluid) {
        FluidStack copy = fluid.copy();
        if (!copy.isEmpty()) {
            copy.setAmount(1);
        }
        return new FilterGhostPacket(slot, ItemStack.EMPTY, copy, true);
    }

    public static void encode(FilterGhostPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot);
        buffer.writeBoolean(packet.fluidEntry);
        if (packet.fluidEntry) {
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.fluid);
        } else {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.item);
        }
    }

    public static FilterGhostPacket decode(RegistryFriendlyByteBuf buffer) {
        int slot = buffer.readVarInt();
        boolean fluidEntry = buffer.readBoolean();
        if (fluidEntry) {
            return fluid(slot, FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        return item(slot, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    public static void handle(FilterGhostPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof FilterListMenu menu)) {
                return;
            }
            if (packet.fluidEntry) {
                menu.setGhostFluid(packet.slot, packet.fluid);
            } else {
                menu.setGhostItem(packet.slot, packet.item);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
