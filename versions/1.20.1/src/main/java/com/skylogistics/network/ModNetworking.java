package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SkyLogistics.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private ModNetworking() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, MenuActionPacket.class, MenuActionPacket::encode, MenuActionPacket::decode,
                MenuActionPacket::handle);
        CHANNEL.registerMessage(1, ItemVaultSnapshotPacket.class, ItemVaultSnapshotPacket::encode,
                ItemVaultSnapshotPacket::decode, ItemVaultSnapshotPacket::handle);
        CHANNEL.registerMessage(2, FluidVaultSnapshotPacket.class, FluidVaultSnapshotPacket::encode,
                FluidVaultSnapshotPacket::decode, FluidVaultSnapshotPacket::handle);
        CHANNEL.registerMessage(3, FilterGhostPacket.class, FilterGhostPacket::encode, FilterGhostPacket::decode,
                FilterGhostPacket::handle);
        CHANNEL.registerMessage(4, ConfiguratorLineDetailsPacket.class, ConfiguratorLineDetailsPacket::encode,
                ConfiguratorLineDetailsPacket::decode, ConfiguratorLineDetailsPacket::handle);
        CHANNEL.registerMessage(5, VaultTerminalClickPacket.class, VaultTerminalClickPacket::encode,
                VaultTerminalClickPacket::decode, VaultTerminalClickPacket::handle);
    }

    public static void sendMenuAction(int action) {
        CHANNEL.sendToServer(new MenuActionPacket(action));
    }

    public static void sendFilterGhostItem(int slot, ItemStack stack) {
        CHANNEL.sendToServer(FilterGhostPacket.item(slot, stack));
    }

    public static void sendFilterGhostFluid(int slot, FluidStack stack) {
        CHANNEL.sendToServer(FilterGhostPacket.fluid(slot, stack));
    }

    public static void sendItemVaultTerminalClick(ItemStack stack, int button, boolean shiftDown) {
        CHANNEL.sendToServer(VaultTerminalClickPacket.item(stack, button, shiftDown));
    }

    public static void sendFluidVaultTerminalClick(FluidStack stack, int button, boolean shiftDown) {
        CHANNEL.sendToServer(VaultTerminalClickPacket.fluid(stack, button, shiftDown));
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
