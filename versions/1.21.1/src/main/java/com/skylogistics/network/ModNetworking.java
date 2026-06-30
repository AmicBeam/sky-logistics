package com.skylogistics.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private static final String PROTOCOL = "1";

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("skylogistics").versioned(PROTOCOL);
        registrar.playToServer(MenuActionPacket.TYPE, MenuActionPacket.STREAM_CODEC, MenuActionPacket::handle);
        registrar.playToClient(ItemVaultSnapshotPacket.TYPE, ItemVaultSnapshotPacket.STREAM_CODEC,
                ItemVaultSnapshotPacket::handle);
        registrar.playToClient(FluidVaultSnapshotPacket.TYPE, FluidVaultSnapshotPacket.STREAM_CODEC,
                FluidVaultSnapshotPacket::handle);
        registrar.playToServer(FilterGhostPacket.TYPE, FilterGhostPacket.STREAM_CODEC, FilterGhostPacket::handle);
        registrar.playToClient(ConfiguratorLineDetailsPacket.TYPE, ConfiguratorLineDetailsPacket.STREAM_CODEC,
                ConfiguratorLineDetailsPacket::handle);
        registrar.playToServer(VaultTerminalClickPacket.TYPE, VaultTerminalClickPacket.STREAM_CODEC,
                VaultTerminalClickPacket::handle);
        registrar.playToServer(LineRenamePacket.TYPE, LineRenamePacket.STREAM_CODEC, LineRenamePacket::handle);
        registrar.playToClient(LineNamePacket.TYPE, LineNamePacket.STREAM_CODEC, LineNamePacket::handle);
        registrar.playToServer(TagFilterEditPacket.TYPE, TagFilterEditPacket.STREAM_CODEC,
                TagFilterEditPacket::handle);
    }

    public static void sendMenuAction(int action) {
        PacketDistributor.sendToServer(new MenuActionPacket(action));
    }

    public static void sendLineRename(String lineName) {
        PacketDistributor.sendToServer(new LineRenamePacket(lineName));
    }

    public static void sendFilterGhostItem(int slot, ItemStack stack) {
        PacketDistributor.sendToServer(FilterGhostPacket.item(slot, stack));
    }

    public static void sendFilterGhostFluid(int slot, FluidStack stack) {
        PacketDistributor.sendToServer(FilterGhostPacket.fluid(slot, stack));
    }

    public static void sendTagFilterTag(int slot, String tag) {
        PacketDistributor.sendToServer(new TagFilterEditPacket(slot, tag));
    }

    public static void sendItemVaultTerminalClick(ItemStack stack, int button, boolean shiftDown) {
        PacketDistributor.sendToServer(VaultTerminalClickPacket.item(stack, button, shiftDown));
    }

    public static void sendFluidVaultTerminalClick(FluidStack stack, int button, boolean shiftDown) {
        PacketDistributor.sendToServer(VaultTerminalClickPacket.fluid(stack, button, shiftDown));
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
