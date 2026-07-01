package com.skylogistics.client;

import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class ClientModEvents {
    private ClientModEvents() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientModEvents::registerMenuScreens);
        modBus.addListener(ClientModEvents::registerBlockEntityRenderers);
    }

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CONFIGURATOR.get(), ConfiguratorScreen::new);
        event.register(ModMenus.SKY_NODE.get(), SkyNodeScreen::new);
        event.register(ModMenus.SKY_NECKLACE.get(), SkyNecklaceScreen::new);
        event.register(ModMenus.FILTER_LIST.get(), FilterListScreen::new);
        event.register(ModMenus.TAG_FILTER_LIST.get(), TagFilterListScreen::new);
        event.register(ModMenus.ITEM_VAULT.get(), ItemVaultScreen::new);
        event.register(ModMenus.FLUID_VAULT.get(), FluidVaultScreen::new);
    }

    private static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.OFFERING_ALTAR.get(), SingleSlotDisplayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.OFFERING_TABLE.get(), SingleSlotDisplayRenderer::new);
    }
}
