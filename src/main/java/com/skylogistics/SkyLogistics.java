package com.skylogistics;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.event.ManualGiftHandler;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.network.SkyNetworkTicker;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModCreativeTabs;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.registry.ModRecipes;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@Mod(SkyLogistics.MOD_ID)
public class SkyLogistics {
    public static final String MOD_ID = "skylogistics";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public SkyLogistics(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        ModRecipes.register(modBus);
        modBus.addListener(ModNetworking::register);
        modBus.addListener(this::registerCapabilities);
        container.registerConfig(ModConfig.Type.SERVER, SkyLogisticsConfig.SERVER_SPEC);

        NeoForge.EVENT_BUS.addListener(SkyNetworkTicker::onServerTick);
        NeoForge.EVENT_BUS.addListener(ManualGiftHandler::onAdvancementEarned);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && event.getItemStack().is(ModItems.SKY_NODE.get())) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.TRUE);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        SkyNetworkRegistry.clear();
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.ITEM_VAULT.get(),
                (vault, side) -> vault.itemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.FLUID_VAULT.get(),
                (vault, side) -> vault.fluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.OFFERING_ALTAR.get(),
                (altar, side) -> altar.itemHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.OFFERING_TABLE.get(),
                (table, side) -> table.itemHandler());
    }
}
