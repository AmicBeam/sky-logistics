package com.skylogistics.block;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.network.SkyLineAccess;
import com.skylogistics.network.SkyPlayerLines;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModItems;
import com.skylogistics.util.NodeFaceMode;
import com.skylogistics.util.NodeMode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class SkyNodePlacementPermissionTest {
    @Test
    void newNodeGetsPersonalLineBeforePermissionChecks(MinecraftServer server) {
        verifyPlacement(server, false, false, false);
    }

    @Test
    void anotherPlayersPrivateLineZeroCannotBlockNewNodes(MinecraftServer server) {
        verifyPlacement(server, true, false, false);
    }

    @Test
    void deniedOffhandPresetFallsBackToPersonalLineWithoutClaimingIt(MinecraftServer server) {
        verifyPlacement(server, true, true, false);
    }

    @Test
    void authorizedOffhandPresetStillWorks(MinecraftServer server) {
        verifyPlacement(server, false, false, true);
    }

    private static void verifyPlacement(MinecraftServer server, boolean claimedDefault, boolean deniedPreset,
            boolean ownPreset) {
        server.submit(() -> {
            if (server.overworld() == null) {
                // The ephemeral provider loads registries but creates no worlds by default.
                try {
                    var createLevels = MinecraftServer.class.getDeclaredMethod("createLevels");
                    createLevels.setAccessible(true);
                    createLevels.invoke(server);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            }
            var level = server.overworld();
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Placer" + UUID.randomUUID().toString().substring(0, 8)));
            var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Owner" + UUID.randomUUID().toString().substring(0, 8)));
            UUID defaultLine = ConfiguratorItem.lineIdForName("Line-0");
            if (claimedDefault) SkyPlayerLines.claimOwner(server, defaultLine, other);
            UUID defaultOwnerBefore = SkyPlayerLines.ownerOf(server, defaultLine);
            UUID otherLine = null;
            if (deniedPreset || ownPreset) {
                ItemStack preset = new ItemStack(ModItems.CONFIGURATOR.get());
                UUID presetLine = ConfiguratorItem.readOrCreate(preset, deniedPreset ? other : player).lineId();
                if (deniedPreset) otherLine = presetLine;
                player.setItemInHand(InteractionHand.OFF_HAND, preset);
            }
            BlockPos pos = new BlockPos(0, 80, 0);
            level.getChunkAt(pos);
            level.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
            var state = ModBlocks.SKY_NODE.get().defaultBlockState()
                    .setValue(SkyNodeBlock.MODE, NodeMode.INPUT).setValue(SkyNodeBlock.TARGET, Direction.DOWN);
            level.setBlockAndUpdate(pos, state);
            try {
                var node = (SkyNodeBlockEntity) level.getBlockEntity(pos);
                assertNotNull(node);
                assertEquals(defaultLine, node.getLineId());
                assertFalse(SkyLineAccess.canUse(player, defaultLine));
                state.getBlock().setPlacedBy(level, pos, state, player, new ItemStack(ModItems.SKY_NODE.get()));
                assertEquals(ConfiguratorItem.lineName(ConfiguratorItem.linePrefix(player), 0), node.getLineName());
                assertNotEquals(defaultLine, node.getLineId());
                assertEquals(player.getUUID(), SkyPlayerLines.ownerOf(server, node.getLineId()));
                assertTrue(SkyLineAccess.canUse(player, node.getLineId()));
                assertFalse(SkyLineAccess.canUse(other, node.getLineId()));
                assertEquals(NodeFaceMode.INPUT, node.getFaceMode(Direction.DOWN));
                assertTrue(node.isItemsEnabled(Direction.DOWN));
                assertEquals(defaultOwnerBefore, SkyPlayerLines.ownerOf(server, defaultLine));
                if (otherLine != null) {
                    assertEquals(other.getUUID(), SkyPlayerLines.ownerOf(server, otherLine));
                    assertFalse(SkyLineAccess.canUse(player, otherLine));
                }
            } finally {
                level.removeBlock(pos, false);
                level.removeBlock(pos.below(), false);
            }
        }).join();
    }
}
