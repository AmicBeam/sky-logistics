package com.skylogistics.client;

import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.registry.ModRecipes;
import java.util.List;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientModEvents {
    private ClientModEvents() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientModEvents::registerMenuScreens);
        modBus.addListener(ClientModEvents::registerBlockEntityRenderers);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onRecipesReceived);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(ClientModEvents::onLoggingOut);
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

    private static void onRecipesReceived(RecipesReceivedEvent event) {
        if (!event.getRecipeTypes().contains(ModRecipes.SKY_OFFERING_TYPE.get())) {
            return;
        }
        List<OfferingRecipe> recipes = event.getRecipeMap().byType(ModRecipes.SKY_OFFERING_TYPE.get()).stream()
                .map(holder -> holder.value())
                .toList();
        ClientOfferingRecipes.apply(recipes);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ModNetworking.requestSkyOfferingRecipes();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientOfferingRecipes.clear();
    }
}
