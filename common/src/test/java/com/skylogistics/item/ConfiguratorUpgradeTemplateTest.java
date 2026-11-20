package com.skylogistics.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConfiguratorUpgradeTemplateTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void copiedUpgradeListPreservesAnEmptyFirstSlot() {
        ItemStack tool = new ItemStack(Items.STICK);
        ItemStack sample = new ItemStack(Items.DIAMOND, 3);
        var config = ConfiguratorItem.readOrCreate(tool).withUpgrades(List.of(ItemStack.EMPTY, sample));
        ConfiguratorItem.writeConfig(tool, config);
        sample.setCount(1);

        var upgrades = ConfiguratorItem.read(tool).upgrades();
        assertEquals(2, upgrades.size());
        assertTrue(upgrades.get(0).isEmpty());
        assertTrue(upgrades.get(1).is(Items.DIAMOND));
        assertEquals(3, upgrades.get(1).getCount());
    }
}
