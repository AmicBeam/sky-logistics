package com.skylogistics.block.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class SkyDistributorFaceContractTest {
    @Test void everyProxyHandlerRequiresTheDistributorAccessFace() throws Exception {
        assertArrayEquals(new Class<?>[] {Direction.class},
                SkyDistributorBlockEntity.class.getMethod("itemHandler", Direction.class).getParameterTypes());
        assertArrayEquals(new Class<?>[] {Direction.class},
                SkyDistributorBlockEntity.class.getMethod("fluidHandler", Direction.class).getParameterTypes());
        assertArrayEquals(new Class<?>[] {Direction.class},
                SkyDistributorBlockEntity.class.getMethod("energyHandler", Direction.class).getParameterTypes());
        assertArrayEquals(new Class<?>[] {Direction.class},
                SkyDistributorBlockEntity.class.getMethod("chemicalHandler", Direction.class).getParameterTypes());
        assertArrayEquals(new Class<?>[] {Direction.class},
                SkyDistributorBlockEntity.class.getMethod("manaHandler", Direction.class).getParameterTypes());
        assertArrayEquals(new Class<?>[] {Direction.class},
                SkyDistributorBlockEntity.class.getMethod("sourceHandler", Direction.class).getParameterTypes());
        assertThrows(NoSuchMethodException.class,
                () -> SkyDistributorBlockEntity.class.getMethod("energyHandler"));
    }

    @Test void logisticsNodesInheritOptionalResourceProxies() throws Exception {
        assertSame(NetworkEndpointBlockEntity.class,
                SkyNodeBlockEntity.class.getMethod("getEndpointChemicalHandler", Direction.class, long.class)
                        .getDeclaringClass());
        assertSame(NetworkEndpointBlockEntity.class,
                SkyNodeBlockEntity.class.getMethod("getEndpointManaHandler", Direction.class, long.class)
                        .getDeclaringClass());
        assertSame(NetworkEndpointBlockEntity.class,
                SkyNodeBlockEntity.class.getMethod("getEndpointSourceHandler", Direction.class, long.class)
                        .getDeclaringClass());
    }
}
