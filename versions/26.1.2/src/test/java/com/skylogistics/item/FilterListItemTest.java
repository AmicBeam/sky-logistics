package com.skylogistics.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skylogistics.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class FilterListItemTest {
    @Test
    void preservesRegistryBackedComponentsAndMatchesAllComponents(MinecraftServer server) {
        ItemStack filterList = new ItemStack(ModItems.FILTER_LIST.get());
        ItemStack enchantedSword = new ItemStack(Items.DIAMOND_SWORD);
        Holder.Reference<Enchantment> sharpness = server.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        enchantedSword.enchant(sharpness, 3);

        for (int slot = 0; slot < 16; slot++) {
            FilterListItem.setFilter(filterList, slot, enchantedSword, server.registryAccess());
        }
        FilterListItem.setMatchNbt(filterList, true);

        ItemStack restored = FilterListItem.getFilter(filterList, 0, server.registryAccess());
        assertTrue(ItemStack.isSameItemSameComponents(enchantedSword, restored));
        assertTrue(FilterListItem.compile(filterList, server.registryAccess()).matches(enchantedSword));
        assertFalse(FilterListItem.compile(filterList, server.registryAccess())
                .matches(new ItemStack(Items.DIAMOND_SWORD)));
    }

    @Test
    void durabilityMatchingRemainsIndependentFromComponentMatching(MinecraftServer server) {
        ItemStack filterList = new ItemStack(ModItems.FILTER_LIST.get());
        ItemStack sample = new ItemStack(Items.DIAMOND_SWORD);
        sample.setDamageValue(12);
        ItemStack candidate = sample.copy();
        candidate.setDamageValue(24);

        FilterListItem.setFilter(filterList, 0, sample, server.registryAccess());
        FilterListItem.setMatchNbt(filterList, true);
        FilterListItem.setMatchDurability(filterList, false);
        assertTrue(FilterListItem.compile(filterList, server.registryAccess()).matches(candidate));

        FilterListItem.setMatchDurability(filterList, true);
        assertFalse(FilterListItem.compile(filterList, server.registryAccess()).matches(candidate));
    }
}
