package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.CelestialGlassBlock;
import com.skylogistics.block.FluidVaultBlock;
import com.skylogistics.block.ItemVaultBlock;
import com.skylogistics.block.OfferingAltarBlock;
import com.skylogistics.block.OfferingTableBlock;
import com.skylogistics.block.SkyDimensionInterfaceBlock;
import com.skylogistics.block.SkyMEInterfaceBlock;
import com.skylogistics.block.SkyNodeBlock;
import com.skylogistics.block.SkyRSInterfaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, SkyLogistics.MOD_ID);

    public static final DeferredHolder<Block, ItemVaultBlock> ITEM_VAULT = BLOCKS.register("item_vault",
            () -> new ItemVaultBlock(blockProperties("item_vault", MapColor.COLOR_CYAN)
                    .strength(3.0F, 10.0F)
                    .sound(SoundType.AMETHYST)));

    public static final DeferredHolder<Block, FluidVaultBlock> FLUID_VAULT = BLOCKS.register("fluid_vault",
            () -> new FluidVaultBlock(blockProperties("fluid_vault", MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0F, 10.0F)
                    .sound(SoundType.AMETHYST)));

    public static final DeferredHolder<Block, SkyNodeBlock> SKY_NODE = BLOCKS.register("sky_node",
            () -> new SkyNodeBlock(blockProperties("sky_node", MapColor.COLOR_BLUE)
                    .strength(1.5F, 7.0F)
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    public static final DeferredHolder<Block, SkyMEInterfaceBlock> SKY_ME_INTERFACE = BLOCKS.register("sky_me_interface",
            () -> new SkyMEInterfaceBlock(interfaceProperties("sky_me_interface", MapColor.COLOR_BLUE)));

    public static final DeferredHolder<Block, SkyRSInterfaceBlock> SKY_RS_INTERFACE = BLOCKS.register("sky_rs_interface",
            () -> new SkyRSInterfaceBlock(interfaceProperties("sky_rs_interface", MapColor.COLOR_LIGHT_BLUE)));

    public static final DeferredHolder<Block, SkyDimensionInterfaceBlock> SKY_DIMENSION_INTERFACE =
            BLOCKS.register("sky_dimension_interface",
                    () -> new SkyDimensionInterfaceBlock(interfaceProperties("sky_dimension_interface", MapColor.COLOR_CYAN)));

    public static final DeferredHolder<Block, Block> CELESTIAL_STONE = BLOCKS.register("celestial_stone",
            () -> new Block(celestialStoneProperties("celestial_stone")));

    public static final DeferredHolder<Block, SlabBlock> CELESTIAL_STONE_SLAB = BLOCKS.register("celestial_stone_slab",
            () -> new SlabBlock(celestialStoneProperties("celestial_stone_slab")));

    public static final DeferredHolder<Block, StairBlock> CELESTIAL_STONE_STAIRS = BLOCKS.register("celestial_stone_stairs",
            () -> new StairBlock(CELESTIAL_STONE.get().defaultBlockState(), celestialStoneProperties("celestial_stone_stairs")));

    public static final DeferredHolder<Block, WallBlock> CELESTIAL_STONE_WALL = BLOCKS.register("celestial_stone_wall",
            () -> new WallBlock(celestialStoneProperties("celestial_stone_wall")));

    public static final DeferredHolder<Block, CelestialGlassBlock> CELESTIAL_GLASS = BLOCKS.register("celestial_glass",
            () -> new CelestialGlassBlock(blockProperties("celestial_glass", MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.6F, 3.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .lightLevel(state -> 15)));

    public static final DeferredHolder<Block, OfferingAltarBlock> OFFERING_ALTAR = BLOCKS.register("offering_altar",
            () -> new OfferingAltarBlock(blockProperties("offering_altar", MapColor.COLOR_CYAN)
                    .strength(3.0F, 10.0F)
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    public static final DeferredHolder<Block, OfferingTableBlock> OFFERING_TABLE = BLOCKS.register("offering_table",
            () -> new OfferingTableBlock(blockProperties("offering_table", MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.5F, 7.0F)
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private static BlockBehaviour.Properties blockProperties(String name, MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, SkyLogistics.id(name)))
                .mapColor(mapColor);
    }

    private static BlockBehaviour.Properties celestialStoneProperties(String name) {
        return blockProperties(name, MapColor.COLOR_LIGHT_BLUE)
                .strength(2.0F, 7.0F)
                .sound(SoundType.AMETHYST);
    }

    private static BlockBehaviour.Properties interfaceProperties(String name, MapColor mapColor) {
        return blockProperties(name, mapColor)
                .strength(2.5F, 7.0F)
                .noOcclusion()
                .sound(SoundType.AMETHYST);
    }
}
