package com.skylogistics.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
class FilterContainerContentsTest {
    @Test
    void samplesBucketsWithoutConsumingThem(MinecraftServer server) {
        ItemStack water = new ItemStack(Items.WATER_BUCKET);
        assertEquals(Fluids.WATER, FilterContainerContents.fluid(water).getFluid());
        assertEquals(1, FilterContainerContents.fluid(water).getAmount());
        assertTrue(water.is(Items.WATER_BUCKET));
        assertEquals(1, water.getCount());
        assertEquals(Fluids.LAVA, FilterContainerContents.fluid(new ItemStack(Items.LAVA_BUCKET)).getFluid());
        assertTrue(FilterContainerContents.fluid(new ItemStack(Items.BUCKET)).isEmpty());
        assertTrue(FilterContainerContents.fluid(new ItemStack(Items.APPLE)).isEmpty());
    }

    @Test
    void rightClickSamplesContentsWhileLeftClickAndEmptyContainersKeepItemMarkers(MinecraftServer server) {
        server.submit(() -> {
            if (server.overworld() == null) {
                try {
                    var createLevels = MinecraftServer.class.getDeclaredMethod("createLevels");
                    createLevels.setAccessible(true);
                    createLevels.invoke(server);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            }
            var player = FakePlayerFactory.getMinecraft(server.overworld());
            ItemStack filters = new ItemStack(ModItems.FILTER_LIST.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, filters);
            FilterListMenu menu = new FilterListMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
            menu.setCarried(new ItemStack(Items.WATER_BUCKET));
            menu.clicked(0, 1, ContainerInput.PICKUP, player);
            assertEquals(Fluids.WATER, menu.getFluidFilter(0).getFluid());
            assertTrue(FilterListItem.getFilter(filters, 0, server.registryAccess()).isEmpty());
            assertTrue(menu.getCarried().is(Items.WATER_BUCKET));
            assertEquals(1, menu.getCarried().getCount());

            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            assertTrue(menu.getFluidFilter(0).isEmpty());
            assertTrue(FilterListItem.getFilter(filters, 0, server.registryAccess()).is(Items.WATER_BUCKET));

            menu.setCarried(new ItemStack(Items.BUCKET));
            menu.clicked(0, 1, ContainerInput.PICKUP, player);
            assertTrue(menu.getFluidFilter(0).isEmpty());
            assertTrue(FilterListItem.getFilter(filters, 0, server.registryAccess()).is(Items.BUCKET));

            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(0, 1, ContainerInput.PICKUP, player);
            assertTrue(FilterListItem.getFilter(filters, 0, server.registryAccess()).isEmpty());
        }).join();
    }

    @Test
    void fluidSamplingCanBeDisabled(MinecraftServer server) {
        SkyLogisticsConfig.SERVER.allowFilterContainerFluids.set(false);
        try {
            assertTrue(FilterContainerContents.fluid(new ItemStack(Items.WATER_BUCKET)).isEmpty());
        } finally {
            SkyLogisticsConfig.SERVER.allowFilterContainerFluids.set(true);
        }
    }
}
