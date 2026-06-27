package com.skylogistics;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.event.ManualGiftHandler;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.network.SkyNetworkTicker;
import com.skylogistics.network.SkyNecklaceTicker;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModCreativeTabs;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.registry.ModRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
    private static final TagKey<Item> COMMON_TOOLS_WRENCH = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));
    private static final TagKey<Item> FORGE_TOOLS_WRENCH = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("forge", "tools/wrench"));
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

        NeoForge.EVENT_BUS.addListener(SkyNecklaceTicker::onServerTick);
        NeoForge.EVENT_BUS.addListener(SkyNetworkTicker::onServerTick);
        NeoForge.EVENT_BUS.addListener(ManualGiftHandler::onAdvancementEarned);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryDismantleWithWrench(event)) {
            return;
        }
        if (event.getHand() == InteractionHand.MAIN_HAND
                && event.getItemStack().is(ModItems.SKY_NODE.get())) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.TRUE);
        }
    }

    private boolean tryDismantleWithWrench(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown()
                || player.isSpectator()
                || !player.mayBuild()
                || !isWrench(event.getItemStack())
                || !isSkyLogisticsBlock(state)) {
            return false;
        }

        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
        if (!level.isClientSide) {
            level.destroyBlock(event.getPos(), true, player, 512);
        }
        return true;
    }

    private static boolean isWrench(ItemStack stack) {
        return !stack.isEmpty()
                && !stack.is(ModItems.CONFIGURATOR.get())
                && (stack.is(COMMON_TOOLS_WRENCH) || stack.is(FORGE_TOOLS_WRENCH));
    }

    private static boolean isSkyLogisticsBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return MOD_ID.equals(id.getNamespace());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        SkyNetworkRegistry.clear();
        SkyNecklaceTicker.clear();
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
