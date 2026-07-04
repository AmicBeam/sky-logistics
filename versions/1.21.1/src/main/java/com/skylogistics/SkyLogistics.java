package com.skylogistics;

import com.skylogistics.block.entity.SingleSlotDisplayBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.event.ManualGiftHandler;
import com.skylogistics.compat.ae2.AppliedEnergisticsCompat;
import com.skylogistics.network.SkyNetworkRegistry;
import com.skylogistics.network.SkyNetworkTicker;
import com.skylogistics.network.SkyNecklaceTicker;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModCreativeTabs;
import com.skylogistics.registry.ModDataComponents;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
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
        ModDataComponents.register(modBus);
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
        if (level instanceof ServerLevel serverLevel) {
            dismantleIntoPlayerInventory(serverLevel, event.getPos(), state, player, event.getItemStack());
        }
        return true;
    }

    private static void dismantleIntoPlayerInventory(ServerLevel level, BlockPos pos, BlockState state, Player player,
            ItemStack tool) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, blockEntity, player, tool));
        collectAdditionalDismantleDrops(blockEntity, drops);

        for (ItemStack drop : drops) {
            player.getInventory().placeItemBackInInventory(drop);
        }

        Block block = state.getBlock();
        block.playerWillDestroy(level, pos, state, player);
        level.removeBlock(pos, false);
        block.destroy(level, pos, state);
    }

    private static void collectAdditionalDismantleDrops(BlockEntity blockEntity, List<ItemStack> drops) {
        if (blockEntity instanceof SingleSlotDisplayBlockEntity display) {
            ItemStack stored = display.removeDisplayedItem();
            if (!stored.isEmpty()) {
                drops.add(stored);
            }
        }
        if (blockEntity instanceof SkyNodeBlockEntity node) {
            node.removeUpgrades(drops);
        }
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
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SKY_ME_INTERFACE.get(),
                (blockEntity, side) -> blockEntity.exposedItemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.SKY_ME_INTERFACE.get(),
                (blockEntity, side) -> blockEntity.exposedFluidHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SKY_ME_INTERFACE.get(),
                (blockEntity, side) -> blockEntity.exposedEnergyHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SKY_RS_INTERFACE.get(),
                (blockEntity, side) -> blockEntity.exposedItemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.SKY_RS_INTERFACE.get(),
                (blockEntity, side) -> blockEntity.exposedFluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.SKY_DIMENSION_INTERFACE.get(),
                (blockEntity, side) -> blockEntity.exposedItemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.SKY_DIMENSION_INTERFACE.get(),
                (blockEntity, side) -> blockEntity.exposedFluidHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.SKY_DIMENSION_INTERFACE.get(),
                (blockEntity, side) -> blockEntity.exposedEnergyHandler());
        AppliedEnergisticsCompat.registerCapabilities(event, ModBlockEntities.SKY_ME_INTERFACE.get());
    }
}
