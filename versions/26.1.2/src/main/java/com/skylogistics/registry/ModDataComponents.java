package com.skylogistics.registry;

import com.mojang.serialization.Codec;
import com.skylogistics.SkyLogistics;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SkyLogistics.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> EULOGIA_CHARGED =
            DATA_COMPONENTS.register("eulogia_charged", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    private ModDataComponents() {
    }

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}
