package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.FluidVaultBlock;
import com.skylogistics.block.ItemVaultBlock;
import com.skylogistics.block.OfferingAltarBlock;
import com.skylogistics.block.OfferingTableBlock;
import com.skylogistics.block.SkyNodeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SkyLogistics.MOD_ID);

    public static final RegistryObject<Block> ITEM_VAULT = BLOCKS.register("item_vault",
            () -> new ItemVaultBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0F, 9.0F)
                    .sound(SoundType.AMETHYST)));

    public static final RegistryObject<Block> FLUID_VAULT = BLOCKS.register("fluid_vault",
            () -> new FluidVaultBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(3.0F, 9.0F)
                    .sound(SoundType.AMETHYST)));

    public static final RegistryObject<Block> SKY_NODE = BLOCKS.register("sky_node",
            () -> new SkyNodeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(1.5F, 6.0F)
                    .noOcclusion()
                    .sound(SoundType.COPPER)));

    public static final RegistryObject<Block> CELESTIAL_STONE = BLOCKS.register("celestial_stone",
            () -> new Block(celestialStoneProperties()));

    public static final RegistryObject<Block> CELESTIAL_STONE_SLAB = BLOCKS.register("celestial_stone_slab",
            () -> new SlabBlock(celestialStoneProperties()));

    public static final RegistryObject<Block> CELESTIAL_STONE_STAIRS = BLOCKS.register("celestial_stone_stairs",
            () -> new StairBlock(() -> CELESTIAL_STONE.get().defaultBlockState(), celestialStoneProperties()));

    public static final RegistryObject<Block> CELESTIAL_STONE_WALL = BLOCKS.register("celestial_stone_wall",
            () -> new WallBlock(celestialStoneProperties()));

    public static final RegistryObject<Block> CELESTIAL_GLASS = BLOCKS.register("celestial_glass",
            () -> new GlassBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(0.6F, 3.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .lightLevel(state -> 15)));

    public static final RegistryObject<Block> OFFERING_ALTAR = BLOCKS.register("offering_altar",
            () -> new OfferingAltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.0F, 9.0F)
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    public static final RegistryObject<Block> OFFERING_TABLE = BLOCKS.register("offering_table",
            () -> new OfferingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(2.5F, 6.0F)
                    .noOcclusion()
                    .sound(SoundType.AMETHYST)));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    private static BlockBehaviour.Properties celestialStoneProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(2.0F, 6.0F)
                .sound(SoundType.AMETHYST);
    }
}
