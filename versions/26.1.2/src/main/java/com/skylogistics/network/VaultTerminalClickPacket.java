package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import com.skylogistics.menu.FluidVaultMenu;
import com.skylogistics.menu.ItemVaultMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record VaultTerminalClickPacket(ItemStack item, FluidStack fluid, boolean fluidEntry, int button,
                                       boolean shiftDown) implements CustomPacketPayload {
    public static final Type<VaultTerminalClickPacket> TYPE = new Type<>(SkyLogistics.id("vault_terminal_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VaultTerminalClickPacket> STREAM_CODEC =
            StreamCodec.ofMember(VaultTerminalClickPacket::encode, VaultTerminalClickPacket::decode);

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

    public static void encode(VaultTerminalClickPacket packet, RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.fluidEntry);
        buffer.writeVarInt(packet.button);
        buffer.writeBoolean(packet.shiftDown);
        if (packet.fluidEntry) {
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.fluid);
        } else {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, packet.item);
        }
    }

    public static VaultTerminalClickPacket decode(RegistryFriendlyByteBuf buffer) {
        boolean fluidEntry = buffer.readBoolean();
        int button = buffer.readVarInt();
        boolean shiftDown = buffer.readBoolean();
        if (fluidEntry) {
            return fluid(FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer), button, shiftDown);
        }
        return item(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), button, shiftDown);
    }

    public static void handle(VaultTerminalClickPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (packet.fluidEntry && player.containerMenu instanceof FluidVaultMenu menu) {
                menu.handleTerminalClick(player, packet.fluid, packet.button, packet.shiftDown);
            } else if (!packet.fluidEntry && player.containerMenu instanceof ItemVaultMenu menu) {
                menu.handleTerminalClick(player, packet.item, packet.button, packet.shiftDown);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
