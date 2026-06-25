package com.skylogistics.network;

import com.skylogistics.menu.FilterListMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

public record FilterGhostPacket(int slot, ItemStack item, FluidStack fluid, boolean fluidEntry) {
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

    public static void encode(FilterGhostPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.slot);
        buffer.writeBoolean(packet.fluidEntry);
        if (packet.fluidEntry) {
            packet.fluid.writeToPacket(buffer);
        } else {
            buffer.writeItem(packet.item);
        }
    }

    public static FilterGhostPacket decode(FriendlyByteBuf buffer) {
        int slot = buffer.readVarInt();
        boolean fluidEntry = buffer.readBoolean();
        if (fluidEntry) {
            return fluid(slot, FluidStack.readFromPacket(buffer));
        }
        return item(slot, buffer.readItem());
    }

    public static void handle(FilterGhostPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof FilterListMenu menu)) {
                return;
            }
            if (packet.fluidEntry) {
                menu.setGhostFluid(packet.slot, packet.fluid);
            } else {
                menu.setGhostItem(packet.slot, packet.item);
            }
        });
        context.setPacketHandled(true);
    }
}
