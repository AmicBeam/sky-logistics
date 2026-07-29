package com.skylogistics.client;

import com.mojang.math.OctahedralGroup;
import com.skylogistics.SkyLogistics;
import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.recipe.OfferingRecipe;
import com.skylogistics.network.ModNetworking;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModMenus;
import com.skylogistics.registry.ModRecipes;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientModEvents {
    private static final Map<String, Map<Direction, StandaloneModelKey<BlockStateModelPart>>> PIPE_EXTRACT_MODELS =
            createExtractModelKeys();

    private ClientModEvents() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientModEvents::registerMenuScreens);
        modBus.addListener(ClientModEvents::registerBlockEntityRenderers);
        modBus.addListener(ClientModEvents::registerStandaloneModels);
        modBus.addListener(ClientModEvents::modifyBakedModels);
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

    private static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        PIPE_EXTRACT_MODELS.forEach((name, keys) -> keys.forEach((direction, key) ->
                event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(
                        SkyLogistics.id("block/" + name + "_extract"), rotation(direction)))));
    }

    private static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        wrapPipeModels(event, ModBlocks.SIMPLE_ITEM_PIPE.get(), "simple_item_pipe");
        wrapPipeModels(event, ModBlocks.SIMPLE_FLUID_PIPE.get(), "simple_fluid_pipe");
        wrapPipeModels(event, ModBlocks.SIMPLE_ENERGY_PIPE.get(), "simple_energy_pipe");
    }

    private static void wrapPipeModels(ModelEvent.ModifyBakingResult event, SimplePipeBlock block, String name) {
        Map<Direction, BlockStateModelPart> extractParts = new EnumMap<>(Direction.class);
        PIPE_EXTRACT_MODELS.get(name).forEach((direction, key) -> {
            BlockStateModelPart part = event.getBakingResult().standaloneModels().get(key);
            if (part != null) {
                extractParts.put(direction, part);
            }
        });
        if (extractParts.size() != Direction.values().length) {
            return;
        }
        var blockModels = event.getBakingResult().blockStateModels();
        for (var state : block.getStateDefinition().getPossibleStates()) {
            BlockStateModel original = blockModels.get(state);
            if (original != null) {
                blockModels.put(state, new SimplePipeBlockStateModel(original, extractParts));
            }
        }
    }

    private static Map<String, Map<Direction, StandaloneModelKey<BlockStateModelPart>>> createExtractModelKeys() {
        return Map.of(
                "simple_item_pipe", createExtractModelKeys("simple_item_pipe"),
                "simple_fluid_pipe", createExtractModelKeys("simple_fluid_pipe"),
                "simple_energy_pipe", createExtractModelKeys("simple_energy_pipe"));
    }

    private static Map<Direction, StandaloneModelKey<BlockStateModelPart>> createExtractModelKeys(String name) {
        Map<Direction, StandaloneModelKey<BlockStateModelPart>> keys = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            keys.put(direction, new StandaloneModelKey<>(
                    () -> SkyLogistics.MOD_ID + ":block/" + name + "_extract/" + direction.getSerializedName()));
        }
        return keys;
    }

    private static BlockModelRotation rotation(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockModelRotation.IDENTITY;
            case SOUTH -> BlockModelRotation.get(OctahedralGroup.BLOCK_ROT_Y_180);
            case WEST -> BlockModelRotation.get(OctahedralGroup.BLOCK_ROT_Y_270);
            case EAST -> BlockModelRotation.get(OctahedralGroup.BLOCK_ROT_Y_90);
            case UP -> BlockModelRotation.get(OctahedralGroup.BLOCK_ROT_X_270);
            case DOWN -> BlockModelRotation.get(OctahedralGroup.BLOCK_ROT_X_90);
        };
    }

    private static void onRecipesReceived(RecipesReceivedEvent event) {
        if (!event.getRecipeTypes().contains(ModRecipes.SKY_OFFERING_TYPE.get())) {
            return;
        }
        List<RecipeHolder<OfferingRecipe>> recipes = event.getRecipeMap()
                .byType(ModRecipes.SKY_OFFERING_TYPE.get()).stream()
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
