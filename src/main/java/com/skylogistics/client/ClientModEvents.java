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
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = SkyLogistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONFIGURATOR.get(), ConfiguratorScreen::new);
        event.register(ModMenus.SKY_NODE.get(), SkyNodeScreen::new);
        event.register(ModMenus.SKY_NECKLACE.get(), SkyNecklaceScreen::new);
        event.register(ModMenus.FILTER_LIST.get(), FilterListScreen::new);
        event.register(ModMenus.ITEM_VAULT.get(), ItemVaultScreen::new);
        event.register(ModMenus.FLUID_VAULT.get(), FluidVaultScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CELESTIAL_GLASS.get(), RenderType.translucent());
            BlockEntityRenderers.register(ModBlockEntities.OFFERING_ALTAR.get(), SingleSlotDisplayRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.OFFERING_TABLE.get(), SingleSlotDisplayRenderer::new);
            ItemProperties.register(ModItems.EULOGIA_CRYSTAL.get(),
                    ResourceLocation.fromNamespaceAndPath(SkyLogistics.MOD_ID, "charged"),
                    (stack, level, entity, seed) -> EulogiaCrystalItem.isCharged(stack) ? 1.0F : 0.0F);
        });
    }
}
