package com.skylogistics.registry;

import com.skylogistics.SkyLogistics;
import com.skylogistics.block.entity.FluidVaultBlockEntity;
import com.skylogistics.block.entity.ItemVaultBlockEntity;
import com.skylogistics.block.entity.OfferingAltarBlockEntity;
import com.skylogistics.block.entity.OfferingTableBlockEntity;
import com.skylogistics.block.entity.SkyDimensionInterfaceBlockEntity;
import com.skylogistics.block.entity.SkyMEInterfaceBlockEntity;
import com.skylogistics.block.entity.SkyNodeBlockEntity;
import com.skylogistics.block.entity.SkyRSInterfaceBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SkyLogistics.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemVaultBlockEntity>> ITEM_VAULT = BLOCK_ENTITIES.register("item_vault",
            () -> new BlockEntityType<>(ItemVaultBlockEntity::new, java.util.Set.of(ModBlocks.ITEM_VAULT.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidVaultBlockEntity>> FLUID_VAULT = BLOCK_ENTITIES.register("fluid_vault",
            () -> new BlockEntityType<>(FluidVaultBlockEntity::new, java.util.Set.of(ModBlocks.FLUID_VAULT.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SkyNodeBlockEntity>> SKY_NODE = BLOCK_ENTITIES.register("sky_node",
            () -> new BlockEntityType<>(SkyNodeBlockEntity::new, java.util.Set.of(ModBlocks.SKY_NODE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SkyMEInterfaceBlockEntity>> SKY_ME_INTERFACE =
            BLOCK_ENTITIES.register("sky_me_interface",
                    () -> new BlockEntityType<>(SkyMEInterfaceBlockEntity::new, java.util.Set.of(ModBlocks.SKY_ME_INTERFACE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SkyRSInterfaceBlockEntity>> SKY_RS_INTERFACE =
            BLOCK_ENTITIES.register("sky_rs_interface",
                    () -> new BlockEntityType<>(SkyRSInterfaceBlockEntity::new, java.util.Set.of(ModBlocks.SKY_RS_INTERFACE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SkyDimensionInterfaceBlockEntity>>
            SKY_DIMENSION_INTERFACE = BLOCK_ENTITIES.register("sky_dimension_interface",
                    () -> new BlockEntityType<>(SkyDimensionInterfaceBlockEntity::new, java.util.Set.of(ModBlocks.SKY_DIMENSION_INTERFACE.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OfferingAltarBlockEntity>> OFFERING_ALTAR =
            BLOCK_ENTITIES.register("offering_altar",
                    () -> new BlockEntityType<>(OfferingAltarBlockEntity::new, java.util.Set.of(ModBlocks.OFFERING_ALTAR.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OfferingTableBlockEntity>> OFFERING_TABLE =
            BLOCK_ENTITIES.register("offering_table",
                    () -> new BlockEntityType<>(OfferingTableBlockEntity::new, java.util.Set.of(ModBlocks.OFFERING_TABLE.get())));

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
