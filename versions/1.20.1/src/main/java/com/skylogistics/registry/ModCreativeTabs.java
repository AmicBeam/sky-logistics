package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.ae2.AppliedEnergisticsCompat;
import com.skylogistics.compat.beyonddimensions.BeyondDimensionsCompat;
import com.skylogistics.compat.PatchouliCompat;
import com.skylogistics.compat.refinedstorage.RefinedStorageCompat;
import com.skylogistics.item.EulogiaCrystalItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SkyLogistics.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.CONFIGURATOR.get()))
                    .title(Component.translatable("itemGroup.skylogistics"))
                    .displayItems((parameters, output) -> {
                        if (PatchouliCompat.isLoaded()) {
                            output.accept(ModItems.SKY_LOGISTICS_MANUAL.get());
                        }
                        output.accept(ModItems.CONFIGURATOR.get());
                        output.accept(ModItems.FILTER_LIST.get());
                        output.accept(ModItems.TAG_FILTER_LIST.get());
                        output.accept(ModItems.SKY_NECKLACE.get());
                        output.accept(ModItems.SPEED_UPGRADE.get());
                        output.accept(ModItems.DIMENSION_UPGRADE.get());
                        output.accept(ModItems.EULOGIA_CRYSTAL.get());
                        output.accept(EulogiaCrystalItem.chargedStack(ModItems.EULOGIA_CRYSTAL.get()));
                        output.accept(ModItems.CHORA_NECTAR.get());
                        output.accept(ModItems.CELESTIAL_STONE.get());
                        output.accept(ModItems.CELESTIAL_STONE_SLAB.get());
                        output.accept(ModItems.CELESTIAL_STONE_STAIRS.get());
                        output.accept(ModItems.CELESTIAL_STONE_WALL.get());
                        output.accept(ModItems.CELESTIAL_GLASS.get());
                        output.accept(ModItems.OFFERING_ALTAR.get());
                        output.accept(ModItems.OFFERING_TABLE.get());
                        output.accept(ModItems.ITEM_VAULT.get());
                        output.accept(ModItems.FLUID_VAULT.get());
                        if (AppliedEnergisticsCompat.isLoaded()) {
                            output.accept(ModItems.SKY_ME_INTERFACE.get());
                        }
                        if (RefinedStorageCompat.isLoaded()) {
                            output.accept(ModItems.SKY_RS_INTERFACE.get());
                        }
                        if (BeyondDimensionsCompat.isLoaded()) {
                            output.accept(ModItems.SKY_DIMENSION_INTERFACE.get());
                        }
                        output.accept(ModItems.SKY_NODE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
