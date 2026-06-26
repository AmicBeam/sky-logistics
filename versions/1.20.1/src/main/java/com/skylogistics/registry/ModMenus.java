package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.menu.FluidVaultMenu;
import com.skylogistics.menu.ItemVaultMenu;
import com.skylogistics.menu.SkyNodeMenu;
import com.skylogistics.menu.SkyNecklaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SkyLogistics.MOD_ID);

    public static final RegistryObject<MenuType<ConfiguratorMenu>> CONFIGURATOR = MENUS.register("configurator",
            () -> IForgeMenuType.create((id, inventory, buffer) ->
                    new ConfiguratorMenu(id, inventory, buffer.readEnum(net.minecraft.world.InteractionHand.class))));

    public static final RegistryObject<MenuType<SkyNodeMenu>> SKY_NODE = MENUS.register("sky_node",
            () -> IForgeMenuType.create((id, inventory, buffer) -> new SkyNodeMenu(id, inventory, buffer.readBlockPos(),
                    buffer.readBoolean(), buffer.readEnum(net.minecraft.world.InteractionHand.class))));

    public static final RegistryObject<MenuType<FilterListMenu>> FILTER_LIST = MENUS.register("filter_list",
            () -> IForgeMenuType.create((id, inventory, buffer) ->
                    new FilterListMenu(id, inventory, buffer.readEnum(net.minecraft.world.InteractionHand.class))));

    public static final RegistryObject<MenuType<SkyNecklaceMenu>> SKY_NECKLACE = MENUS.register("sky_necklace",
            () -> IForgeMenuType.create((id, inventory, buffer) ->
                    new SkyNecklaceMenu(id, inventory, buffer.readEnum(net.minecraft.world.InteractionHand.class))));

    public static final RegistryObject<MenuType<ItemVaultMenu>> ITEM_VAULT = MENUS.register("item_vault",
            () -> IForgeMenuType.create((id, inventory, buffer) -> new ItemVaultMenu(id, inventory, buffer.readBlockPos())));

    public static final RegistryObject<MenuType<FluidVaultMenu>> FLUID_VAULT = MENUS.register("fluid_vault",
            () -> IForgeMenuType.create((id, inventory, buffer) -> new FluidVaultMenu(id, inventory, buffer.readBlockPos())));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
