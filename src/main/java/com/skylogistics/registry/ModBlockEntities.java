package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.OfferingAltarBlockEntity;
import com.skylogistics.block.entity.OfferingTableBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SkyLogistics.MOD_ID);

    public static final RegistryObject<BlockEntityType<ItemVaultBlockEntity>> ITEM_VAULT = BLOCK_ENTITIES.register("item_vault",
            () -> BlockEntityType.Builder.of(ItemVaultBlockEntity::new, ModBlocks.ITEM_VAULT.get()).build(null));

    public static final RegistryObject<BlockEntityType<FluidVaultBlockEntity>> FLUID_VAULT = BLOCK_ENTITIES.register("fluid_vault",
            () -> BlockEntityType.Builder.of(FluidVaultBlockEntity::new, ModBlocks.FLUID_VAULT.get()).build(null));

    public static final RegistryObject<BlockEntityType<SkyNodeBlockEntity>> SKY_NODE = BLOCK_ENTITIES.register("sky_node",
            () -> BlockEntityType.Builder.of(SkyNodeBlockEntity::new, ModBlocks.SKY_NODE.get()).build(null));

    public static final RegistryObject<BlockEntityType<OfferingAltarBlockEntity>> OFFERING_ALTAR =
            BLOCK_ENTITIES.register("offering_altar",
                    () -> BlockEntityType.Builder.of(OfferingAltarBlockEntity::new,
                            ModBlocks.OFFERING_ALTAR.get()).build(null));

    public static final RegistryObject<BlockEntityType<OfferingTableBlockEntity>> OFFERING_TABLE =
            BLOCK_ENTITIES.register("offering_table",
                    () -> BlockEntityType.Builder.of(OfferingTableBlockEntity::new,
                            ModBlocks.OFFERING_TABLE.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
