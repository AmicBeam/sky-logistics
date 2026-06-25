package com.skylogistics;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.network.SkyNetworkTicker;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModCreativeTabs;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.registry.ModRecipes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SkyLogistics.MOD_ID)
public class SkyLogistics {
    public static final String MOD_ID = "skylogistics";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public SkyLogistics() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        ModRecipes.register(modBus);
        ModNetworking.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SkyLogisticsConfig.SERVER_SPEC);

        MinecraftForge.EVENT_BUS.addListener(SkyNetworkTicker::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && event.getItemStack().is(ModItems.SKY_NODE.get())) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.ALLOW);
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        SkyNetworkRegistry.clear();
    }
}
