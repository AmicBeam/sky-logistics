package com.skylogistics;

import com.skylogistics.block.entity.SingleSlotDisplayBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(SkyLogistics.MOD_ID)
public class SkyLogistics {
    public static final String MOD_ID = "skylogistics";
    private static final TagKey<Item> FORGE_TOOLS_WRENCH = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "tools/wrench"));
    private static final TagKey<Item> COMMON_TOOLS_WRENCH = TagKey.create(Registries.ITEM,
            new ResourceLocation("c", "tools/wrench"));
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
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SkyLogisticsConfig.CLIENT_SPEC);

        MinecraftForge.EVENT_BUS.addListener(SkyNecklaceTicker::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(SkyNetworkTicker::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ManualGiftHandler::onAdvancementEarned);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryDismantleWithWrench(event)) {
            return;
        }
        if (event.getHand() == InteractionHand.MAIN_HAND
                && event.getItemStack().is(ModItems.SKY_NODE.get())) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.ALLOW);
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

        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
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
                && (stack.is(FORGE_TOOLS_WRENCH) || stack.is(COMMON_TOOLS_WRENCH));
    }

    private static boolean isSkyLogisticsBlock(BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && MOD_ID.equals(id.getNamespace());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        SkyNetworkRegistry.clear();
        SkyNecklaceTicker.clear();
    }
}
