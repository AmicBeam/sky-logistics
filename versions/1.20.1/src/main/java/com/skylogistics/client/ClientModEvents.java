package com.skylogistics.client;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.EulogiaCrystalItem;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SkyLogistics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.CONFIGURATOR.get(), ConfiguratorScreen::new);
            MenuScreens.register(ModMenus.SKY_NODE.get(), SkyNodeScreen::new);
            MenuScreens.register(ModMenus.SKY_NECKLACE.get(), SkyNecklaceScreen::new);
            MenuScreens.register(ModMenus.FILTER_LIST.get(), FilterListScreen::new);
            MenuScreens.register(ModMenus.ITEM_VAULT.get(), ItemVaultScreen::new);
            MenuScreens.register(ModMenus.FLUID_VAULT.get(), FluidVaultScreen::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CELESTIAL_GLASS.get(), RenderType.translucent());
            BlockEntityRenderers.register(ModBlockEntities.OFFERING_ALTAR.get(), SingleSlotDisplayRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.OFFERING_TABLE.get(), SingleSlotDisplayRenderer::new);
            ItemProperties.register(ModItems.EULOGIA_CRYSTAL.get(),
                    new ResourceLocation(SkyLogistics.MOD_ID, "charged"),
                    (stack, level, entity, seed) -> EulogiaCrystalItem.isCharged(stack) ? 1.0F : 0.0F);
        });
    }
}
