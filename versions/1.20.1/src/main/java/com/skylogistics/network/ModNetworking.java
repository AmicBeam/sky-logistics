package com.skylogistics.network;

import com.skylogistics.SkyLogistics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
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
        CHANNEL.registerMessage(6, LineRenamePacket.class, LineRenamePacket::encode, LineRenamePacket::decode,
                LineRenamePacket::handle);
        CHANNEL.registerMessage(7, LineNamePacket.class, LineNamePacket::encode, LineNamePacket::decode,
                LineNamePacket::handle);
        CHANNEL.registerMessage(8, TagFilterEditPacket.class, TagFilterEditPacket::encode,
                TagFilterEditPacket::decode, TagFilterEditPacket::handle);
        CHANNEL.registerMessage(9, DistributorTargetsRequestPacket.class, DistributorTargetsRequestPacket::encode,
                DistributorTargetsRequestPacket::decode, DistributorTargetsRequestPacket::handle);
        CHANNEL.registerMessage(10, DistributorTargetsPacket.class, DistributorTargetsPacket::encode,
                DistributorTargetsPacket::decode, DistributorTargetsPacket::handle);
        CHANNEL.registerMessage(11, ExactQuantityPacket.class, ExactQuantityPacket::encode,
                ExactQuantityPacket::decode, ExactQuantityPacket::handle);
        CHANNEL.registerMessage(12, ChemicalFilterPacket.class, ChemicalFilterPacket::encode,
                ChemicalFilterPacket::decode, ChemicalFilterPacket::handle);
    }

    public static void sendMenuAction(int action) {
        CHANNEL.sendToServer(new MenuActionPacket(action));
    }

    public static void sendExactQuantity(int amount) {
        CHANNEL.sendToServer(new ExactQuantityPacket(amount));
    }

    public static void sendChemicalFilter(int slot, String chemical) {
        CHANNEL.sendToServer(new ChemicalFilterPacket(slot, chemical));
    }

    public static void sendLineRename(String lineName) {
        CHANNEL.sendToServer(new LineRenamePacket(lineName));
    }

    public static void requestDistributorTargets(BlockPos distributorPos) {
        CHANNEL.sendToServer(new DistributorTargetsRequestPacket(distributorPos));
    }

    public static void sendFilterGhostItem(int slot, ItemStack stack) {
        CHANNEL.sendToServer(FilterGhostPacket.item(slot, stack));
    }

    public static void sendFilterGhostFluid(int slot, FluidStack stack) {
        CHANNEL.sendToServer(FilterGhostPacket.fluid(slot, stack));
    }

    public static void sendTagFilterTag(int slot, String tag) {
        CHANNEL.sendToServer(new TagFilterEditPacket(slot, tag));
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
