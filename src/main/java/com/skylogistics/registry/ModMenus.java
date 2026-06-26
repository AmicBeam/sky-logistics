package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.menu.ConfiguratorMenu;
import com.skylogistics.menu.FilterListMenu;
import com.skylogistics.menu.FluidVaultMenu;
import com.skylogistics.menu.ItemVaultMenu;
import com.skylogistics.menu.SkyNodeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, SkyLogistics.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ConfiguratorMenu>> CONFIGURATOR = MENUS.register("configurator",
            () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new ConfiguratorMenu(id, inventory, buffer.readEnum(net.minecraft.world.InteractionHand.class))));

    public static final DeferredHolder<MenuType<?>, MenuType<SkyNodeMenu>> SKY_NODE = MENUS.register("sky_node",
            () -> IMenuTypeExtension.create((id, inventory, buffer) -> new SkyNodeMenu(id, inventory, buffer.readBlockPos(),
                    buffer.readBoolean(), buffer.readEnum(net.minecraft.world.InteractionHand.class))));

    public static final DeferredHolder<MenuType<?>, MenuType<FilterListMenu>> FILTER_LIST = MENUS.register("filter_list",
            () -> IMenuTypeExtension.create((id, inventory, buffer) ->
                    new FilterListMenu(id, inventory, buffer.readEnum(net.minecraft.world.InteractionHand.class))));

    public static final DeferredHolder<MenuType<?>, MenuType<ItemVaultMenu>> ITEM_VAULT = MENUS.register("item_vault",
            () -> IMenuTypeExtension.create((id, inventory, buffer) -> new ItemVaultMenu(id, inventory, buffer.readBlockPos())));

    public static final DeferredHolder<MenuType<?>, MenuType<FluidVaultMenu>> FLUID_VAULT = MENUS.register("fluid_vault",
            () -> IMenuTypeExtension.create((id, inventory, buffer) -> new FluidVaultMenu(id, inventory, buffer.readBlockPos())));

    private ModMenus() {
    }

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
