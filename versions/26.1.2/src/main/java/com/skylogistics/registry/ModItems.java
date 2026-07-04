package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.item.ChoraNectarItem;
import com.skylogistics.item.ConfiguratorItem;
import com.skylogistics.item.FilterListItem;
import com.skylogistics.item.EulogiaCrystalItem;
import com.skylogistics.item.OfferingAltarBlockItem;
import com.skylogistics.item.SkyLogisticsManualItem;
import com.skylogistics.item.SkyNodeBlockItem;
import com.skylogistics.item.SkyNecklaceItem;
import com.skylogistics.item.TagFilterListItem;
import com.skylogistics.item.UpgradeCardItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, SkyLogistics.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> ITEM_VAULT = ITEMS.register("item_vault",
            () -> new BlockItem(ModBlocks.ITEM_VAULT.get(), blockItemProperties("item_vault")));
    public static final DeferredHolder<Item, BlockItem> FLUID_VAULT = ITEMS.register("fluid_vault",
            () -> new BlockItem(ModBlocks.FLUID_VAULT.get(), blockItemProperties("fluid_vault")));
    public static final DeferredHolder<Item, SkyNodeBlockItem> SKY_NODE = ITEMS.register("sky_node",
            () -> new SkyNodeBlockItem(ModBlocks.SKY_NODE.get(), blockItemProperties("sky_node")));
    public static final DeferredHolder<Item, BlockItem> SKY_ME_INTERFACE = ITEMS.register("sky_me_interface",
            () -> new BlockItem(ModBlocks.SKY_ME_INTERFACE.get(), blockItemProperties("sky_me_interface")));
    public static final DeferredHolder<Item, BlockItem> SKY_RS_INTERFACE = ITEMS.register("sky_rs_interface",
            () -> new BlockItem(ModBlocks.SKY_RS_INTERFACE.get(), blockItemProperties("sky_rs_interface")));
    public static final DeferredHolder<Item, BlockItem> SKY_DIMENSION_INTERFACE = ITEMS.register("sky_dimension_interface",
            () -> new BlockItem(ModBlocks.SKY_DIMENSION_INTERFACE.get(), blockItemProperties("sky_dimension_interface")));
    public static final DeferredHolder<Item, BlockItem> CELESTIAL_STONE = ITEMS.register("celestial_stone",
            () -> new BlockItem(ModBlocks.CELESTIAL_STONE.get(), blockItemProperties("celestial_stone")));
    public static final DeferredHolder<Item, BlockItem> CELESTIAL_STONE_SLAB = ITEMS.register("celestial_stone_slab",
            () -> new BlockItem(ModBlocks.CELESTIAL_STONE_SLAB.get(), blockItemProperties("celestial_stone_slab")));
    public static final DeferredHolder<Item, BlockItem> CELESTIAL_STONE_STAIRS = ITEMS.register("celestial_stone_stairs",
            () -> new BlockItem(ModBlocks.CELESTIAL_STONE_STAIRS.get(), blockItemProperties("celestial_stone_stairs")));
    public static final DeferredHolder<Item, BlockItem> CELESTIAL_STONE_WALL = ITEMS.register("celestial_stone_wall",
            () -> new BlockItem(ModBlocks.CELESTIAL_STONE_WALL.get(), blockItemProperties("celestial_stone_wall")));
    public static final DeferredHolder<Item, BlockItem> CELESTIAL_GLASS = ITEMS.register("celestial_glass",
            () -> new BlockItem(ModBlocks.CELESTIAL_GLASS.get(), blockItemProperties("celestial_glass")));
    public static final DeferredHolder<Item, OfferingAltarBlockItem> OFFERING_ALTAR = ITEMS.register("offering_altar",
            () -> new OfferingAltarBlockItem(ModBlocks.OFFERING_ALTAR.get(), blockItemProperties("offering_altar")));
    public static final DeferredHolder<Item, BlockItem> OFFERING_TABLE = ITEMS.register("offering_table",
            () -> new BlockItem(ModBlocks.OFFERING_TABLE.get(), blockItemProperties("offering_table")));

    public static final DeferredHolder<Item, ConfiguratorItem> CONFIGURATOR = ITEMS.register("configurator",
            () -> new ConfiguratorItem(itemProperties("configurator").stacksTo(1)));
    public static final DeferredHolder<Item, FilterListItem> FILTER_LIST = ITEMS.register("filter_list",
            () -> new FilterListItem(itemProperties("filter_list").stacksTo(1)));
    public static final DeferredHolder<Item, TagFilterListItem> TAG_FILTER_LIST = ITEMS.register("tag_filter_list",
            () -> new TagFilterListItem(itemProperties("tag_filter_list").stacksTo(1)));
    public static final DeferredHolder<Item, SkyNecklaceItem> SKY_NECKLACE = ITEMS.register("sky_necklace",
            () -> new SkyNecklaceItem(itemProperties("sky_necklace").stacksTo(1)));
    public static final DeferredHolder<Item, UpgradeCardItem> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new UpgradeCardItem(itemProperties("speed_upgrade").stacksTo(64),
                    "tooltip.skylogistics.speed_upgrade"));
    public static final DeferredHolder<Item, UpgradeCardItem> DIMENSION_UPGRADE = ITEMS.register("dimension_upgrade",
            () -> new UpgradeCardItem(itemProperties("dimension_upgrade").stacksTo(64),
                    "tooltip.skylogistics.dimension_upgrade"));
    public static final DeferredHolder<Item, EulogiaCrystalItem> EULOGIA_CRYSTAL = ITEMS.register("eulogia_crystal",
            () -> new EulogiaCrystalItem(itemProperties("eulogia_crystal").stacksTo(64)));
    public static final DeferredHolder<Item, ChoraNectarItem> CHORA_NECTAR = ITEMS.register("chora_nectar",
            () -> new ChoraNectarItem(itemProperties("chora_nectar")));
    public static final DeferredHolder<Item, SkyLogisticsManualItem> SKY_LOGISTICS_MANUAL = ITEMS.register(
            "sky_logistics_manual",
            () -> new SkyLogisticsManualItem(itemProperties("sky_logistics_manual").stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private static Item.Properties itemProperties(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, SkyLogistics.id(name)));
    }

    private static Item.Properties blockItemProperties(String name) {
        return itemProperties(name).useBlockDescriptionPrefix();
    }
}
