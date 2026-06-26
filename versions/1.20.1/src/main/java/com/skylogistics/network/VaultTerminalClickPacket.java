package com.skylogistics.network;

import com.skylogistics.menu.FluidVaultMenu;
import com.skylogistics.menu.ItemVaultMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;

public record VaultTerminalClickPacket(ItemStack item, FluidStack fluid, boolean fluidEntry, int button,
                                       boolean shiftDown) {
    public static VaultTerminalClickPacket item(ItemStack item, int button, boolean shiftDown) {
        ItemStack copy = item.copy();
        if (!copy.isEmpty()) {
            copy.setCount(1);
        }
        return new VaultTerminalClickPacket(copy, FluidStack.EMPTY, false, button, shiftDown);
    }

    public static VaultTerminalClickPacket fluid(FluidStack fluid, int button, boolean shiftDown) {
        FluidStack copy = fluid.copy();
        if (!copy.isEmpty()) {
            copy.setAmount(1);
        }
        return new VaultTerminalClickPacket(ItemStack.EMPTY, copy, true, button, shiftDown);
    }

    public static void encode(VaultTerminalClickPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.fluidEntry);
        buffer.writeVarInt(packet.button);
        buffer.writeBoolean(packet.shiftDown);
        if (packet.fluidEntry) {
            buffer.writeFluidStack(packet.fluid);
        } else {
            buffer.writeItem(packet.item);
        }
    }

    public static VaultTerminalClickPacket decode(FriendlyByteBuf buffer) {
        boolean fluidEntry = buffer.readBoolean();
        int button = buffer.readVarInt();
        boolean shiftDown = buffer.readBoolean();
        if (fluidEntry) {
            return fluid(buffer.readFluidStack(), button, shiftDown);
        }
        return item(buffer.readItem(), button, shiftDown);
    }

    public static void handle(VaultTerminalClickPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (packet.fluidEntry && player.containerMenu instanceof FluidVaultMenu menu) {
                menu.handleTerminalClick(player, packet.fluid, packet.button, packet.shiftDown);
            } else if (!packet.fluidEntry && player.containerMenu instanceof ItemVaultMenu menu) {
                menu.handleTerminalClick(player, packet.item, packet.button, packet.shiftDown);
            }
        });
        context.setPacketHandled(true);
    }
}
