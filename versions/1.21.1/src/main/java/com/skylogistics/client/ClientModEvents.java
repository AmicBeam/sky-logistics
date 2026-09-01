package com.skylogistics.client;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.EulogiaCrystalItem;
import com.skylogistics.block.SimplePipeBlock;
import com.skylogistics.registry.ModBlockEntities;
import com.skylogistics.registry.ModBlocks;
import com.skylogistics.registry.ModItems;
import com.skylogistics.registry.ModMenus;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
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
        event.register(ModMenus.KLEIS_DOMINION_WAND.get(), KleisDominionWandScreen::new);
            event.register(ModMenus.SKY_NECKLACE.get(), SkyNecklaceScreen::new);
            event.register(ModMenus.FILTER_LIST.get(), FilterListScreen::new);
            event.register(ModMenus.TAG_FILTER_LIST.get(), TagFilterListScreen::new);
            event.register(ModMenus.ITEM_VAULT.get(), ItemVaultScreen::new);
        event.register(ModMenus.FLUID_VAULT.get(), FluidVaultScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BlockEntityRenderers.register(ModBlockEntities.OFFERING_ALTAR.get(), SingleSlotDisplayRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.OFFERING_TABLE.get(), SingleSlotDisplayRenderer::new);
            ItemProperties.register(ModItems.EULOGIA_CRYSTAL.get(),
                    ResourceLocation.fromNamespaceAndPath(SkyLogistics.MOD_ID, "charged"),
                    (stack, level, entity, seed) -> EulogiaCrystalItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.EULOGIA_COMPANION_STONE.get(),
                    ResourceLocation.fromNamespaceAndPath(SkyLogistics.MOD_ID, "charged"),
                    (stack, level, entity, seed) -> EulogiaCrystalItem.isCharged(stack) ? 1.0F : 0.0F);
        });
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(extractModel("simple_item_pipe"));
        event.register(extractModel("simple_fluid_pipe"));
        event.register(extractModel("simple_energy_pipe"));
    }

    @SubscribeEvent
    public static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        wrapPipeModels(event, ModBlocks.SIMPLE_ITEM_PIPE.get(), "simple_item_pipe");
        wrapPipeModels(event, ModBlocks.SIMPLE_FLUID_PIPE.get(), "simple_fluid_pipe");
        wrapPipeModels(event, ModBlocks.SIMPLE_ENERGY_PIPE.get(), "simple_energy_pipe");
    }

    private static void wrapPipeModels(ModelEvent.ModifyBakingResult event, SimplePipeBlock block, String name) {
        BakedModel extractModel = event.getModels().get(extractModel(name));
        if (extractModel == null) {
            return;
        }
        var extractQuads = SimplePipeBakedModel.rotatedExtractQuads(extractModel);
        for (var state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
            BakedModel original = event.getModels().get(location);
            if (original != null) {
                event.getModels().put(location, new SimplePipeBakedModel(original, extractQuads));
            }
        }
    }

    private static ModelResourceLocation extractModel(String name) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(SkyLogistics.MOD_ID, "block/" + name + "_extract"));
    }
}
