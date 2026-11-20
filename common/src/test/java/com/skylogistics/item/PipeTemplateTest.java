package com.skylogistics.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skylogistics.util.SimplePipeConnection;
import com.skylogistics.util.SimplePipeType;
import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PipeTemplateTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void roundTripsSixFacesAndKeepsPipePasteSeparateFromNodePaste() {
        UUID owner = UUID.randomUUID();
        EnumMap<Direction, PipeTemplate.Face> faces = new EnumMap<>(Direction.class);
        faces.put(Direction.NORTH, new PipeTemplate.Face(SimplePipeConnection.EXTRACT, true, ItemStack.EMPTY));
        faces.put(Direction.SOUTH, new PipeTemplate.Face(SimplePipeConnection.PIPE, false, ItemStack.EMPTY));
        ItemStack tool = new ItemStack(Items.STICK);
        ConfiguratorItem.writePipeTemplate(tool, new PipeTemplate(SimplePipeType.FLUID, owner, faces));
        ConfiguratorItem.setPasteMode(tool, true);

        PipeTemplate loaded = ConfiguratorItem.readPipeTemplate(tool.copy());
        assertEquals(SimplePipeType.FLUID, loaded.type());
        assertEquals(owner, loaded.owner());
        assertEquals(SimplePipeConnection.EXTRACT, loaded.face(Direction.NORTH).connection());
        assertTrue(loaded.face(Direction.NORTH).disconnected());
        assertEquals(SimplePipeConnection.PIPE, loaded.face(Direction.SOUTH).connection());
        assertEquals(SimplePipeConnection.NONE, loaded.face(Direction.UP).connection());
        assertTrue(ConfiguratorItem.isPipePasteMode(tool));
        assertFalse(ConfiguratorItem.isPasteMode(tool));
        assertTrue(ConfiguratorItem.exitPasteMode(tool));
        assertFalse(ConfiguratorItem.isPipePasteMode(tool));
        assertEquals(owner, ConfiguratorItem.readPipeTemplate(tool).owner());

        ConfiguratorItem.setPasteMode(tool, true);
        ConfiguratorItem.writePipeTemplate(tool, null);
        assertNull(ConfiguratorItem.readPipeTemplate(tool));
        assertFalse(ConfiguratorItem.isPipePasteMode(tool));
        assertTrue(ConfiguratorItem.isPasteMode(tool));
    }

    @Test
    void filterSampleIsCopiedWithoutChangingItsCount() {
        ItemStack sample = new ItemStack(Items.DIAMOND, 16);
        PipeTemplate.Face face = new PipeTemplate.Face(SimplePipeConnection.INSERT, false, sample);
        assertEquals(1, face.filter().getCount());
        assertEquals(16, sample.getCount());
        sample.setCount(8);
        assertEquals(1, face.filter().getCount());
    }
}
