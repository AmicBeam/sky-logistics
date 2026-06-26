package com.skylogistics.client;

import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.network.FluidVaultSnapshotPacket;
import com.skylogistics.network.ItemVaultSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ClientVaultSnapshots {
    private ClientVaultSnapshots() {
    }

    public static void apply(ItemVaultSnapshotPacket packet) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(packet.pos());
        if (blockEntity instanceof ItemVaultBlockEntity vault) {
            vault.applyClientSnapshot(packet.typeLimit(), packet.usedTypes(), packet.totalAmount(), packet.entries());
        }
    }

    public static void apply(FluidVaultSnapshotPacket packet) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(packet.pos());
        if (blockEntity instanceof FluidVaultBlockEntity vault) {
            vault.applyClientSnapshot(packet.typeLimit(), packet.usedTypes(), packet.totalAmount(), packet.entries());
        }
    }
}
