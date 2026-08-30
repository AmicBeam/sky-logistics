package com.skylogistics.block.entity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.skylogistics.util.DistributorPushDirection;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class SkyDistributorFaceContractTest {
    @Test void wrenchDirectionSequenceMatchesMePatternProvider() {
        assertEquals(DistributorPushDirection.SOUTH,
                DistributorPushDirection.ALL.afterWrenchClick(Direction.NORTH));
        assertEquals(DistributorPushDirection.NORTH,
                DistributorPushDirection.SOUTH.afterWrenchClick(Direction.NORTH));
        assertEquals(DistributorPushDirection.ALL,
                DistributorPushDirection.NORTH.afterWrenchClick(Direction.NORTH));
        assertEquals(DistributorPushDirection.SOUTH,
                DistributorPushDirection.EAST.afterWrenchClick(Direction.UP));
    }

    @Test void transferCursorsAreStoredPerAccessFace() throws Exception {
        assertSame(int[].class, SkyDistributorBlockEntity.class.getDeclaredField("itemInsertCursors").getType());
        assertSame(int[].class, SkyDistributorBlockEntity.class.getDeclaredField("fluidInsertCursors").getType());
        assertSame(int[].class, SkyDistributorBlockEntity.class.getDeclaredField("fluidDrainCursors").getType());
        assertSame(int[].class, SkyDistributorBlockEntity.class.getDeclaredField("energyReceiveCursors").getType());
        assertSame(int[].class, SkyDistributorBlockEntity.class.getDeclaredField("energyExtractCursors").getType());
    }

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
