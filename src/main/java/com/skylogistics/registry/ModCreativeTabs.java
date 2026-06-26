package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.compat.PatchouliCompat;
import com.skylogistics.item.EulogiaCrystalItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SkyLogistics.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.CONFIGURATOR.get()))
                    .title(Component.translatable("itemGroup.skylogistics"))
                    .displayItems((parameters, output) -> {
                        PatchouliCompat.createManualStack().ifPresent(output::accept);
                        output.accept(ModItems.CONFIGURATOR.get());
                        output.accept(ModItems.FILTER_LIST.get());
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
                        output.accept(ModItems.SKY_NODE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
