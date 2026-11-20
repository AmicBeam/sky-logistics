package com.skylogistics.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skylogistics.item.ConfiguratorItem;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConfiguratorTemplateSlotsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void selectsFirstCopiedFilterWithoutReplacingPlacementPreset() {
        ItemStack tool = new ItemStack(Items.STICK);
        var original = ConfiguratorItem.readOrCreate(tool);
        var placement = original.placement().withFilter(new ItemStack(Items.STONE));
        var copied = new ConfiguratorItem.ToolConfig(original.lineId(), original.lineName(), placement,
                Map.of(Direction.NORTH, placement.withFilter(new ItemStack(Items.DIAMOND)),
                        Direction.WEST, placement.withFilter(new ItemStack(Items.EMERALD))), true, List.of());
        ConfiguratorItem.writeConfig(tool, copied);

        assertEquals(Direction.NORTH, ConfiguratorTemplateSlots.firstCopiedFace(tool));
        var editedPlacement = copied.withTemplateFilter(null, new ItemStack(Items.IRON_INGOT));
        assertTrue(editedPlacement.placement().filter(0).is(Items.IRON_INGOT));
        assertTrue(editedPlacement.face(Direction.NORTH).filter(0).is(Items.DIAMOND));
    }
}
