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
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SkyLogistics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.CONFIGURATOR.get(), ConfiguratorScreen::new);
            MenuScreens.register(ModMenus.SKY_NODE.get(), SkyNodeScreen::new);
            MenuScreens.register(ModMenus.SKY_NECKLACE.get(), SkyNecklaceScreen::new);
            MenuScreens.register(ModMenus.FILTER_LIST.get(), FilterListScreen::new);
            MenuScreens.register(ModMenus.TAG_FILTER_LIST.get(), TagFilterListScreen::new);
            MenuScreens.register(ModMenus.ITEM_VAULT.get(), ItemVaultScreen::new);
            MenuScreens.register(ModMenus.FLUID_VAULT.get(), FluidVaultScreen::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CELESTIAL_GLASS.get(), RenderType.translucent());
            BlockEntityRenderers.register(ModBlockEntities.OFFERING_ALTAR.get(), SingleSlotDisplayRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.OFFERING_TABLE.get(), SingleSlotDisplayRenderer::new);
            ItemProperties.register(ModItems.EULOGIA_CRYSTAL.get(),
                    new ResourceLocation(SkyLogistics.MOD_ID, "charged"),
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

    private static void wrapPipeModels(ModelEvent.ModifyBakingResult event, Block block, String name) {
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
        return new ModelResourceLocation(
                new ResourceLocation(SkyLogistics.MOD_ID, "block/" + name + "_extract"), "standalone");
    }
}
