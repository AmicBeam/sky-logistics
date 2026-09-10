package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.util.NodeFaceMode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkyNetworkRegistryDimensionLifecycleTest {
    private static final UUID LINE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    @AfterEach
    void clearRegistry() {
        SkyNetworkRegistry.clear();
    }

    @Test
    void firstRebuildInAnotherDimensionInvalidatesPreviouslyCachedOutputs() throws Exception {
        assertTrue(SkyNetworkRegistry.globalItemOutputs(LINE).isEmpty());
        rebuildNewDimensionAfterServerHasTicked(NodeFaceMode.OUTPUT);

        // The same invalidation gate used by readyLines(): clean caches are not rebuilt.
        if (flag("globalOutputsDirty").getBoolean(null)) {
            Method refresh = SkyNetworkRegistry.class.getDeclaredMethod("rebuildGlobalOutputs");
            refresh.setAccessible(true);
            refresh.invoke(null);
        }
        assertEquals(1, SkyNetworkRegistry.globalItemOutputs(LINE).size(),
                "An extractor that cached no targets must discover the first inserter in another dimension");
        assertFalse(SkyNetworkRegistry.globalItemOutputs(LINE).get(0).node().hasDimensionUpgrade(),
                "The receiving endpoint must not need a dimension upgrade");
    }

    @Test
    void firstRebuildInAnotherDimensionInvalidatesPreviouslyBuiltScheduler() throws Exception {
        rebuildNewDimensionAfterServerHasTicked(NodeFaceMode.INPUT);

        assertTrue(flag("runtimeCachesDirty").getBoolean(null),
                "Extractors in a newly indexed dimension must be added to the ready-line scheduler");
    }

    private static void rebuildNewDimensionAfterServerHasTicked(NodeFaceMode mode) throws Exception {
        // readyLines() clears both flags each tick. A DimensionIndex created later starts
        // with fullRebuild=true, even though the server-wide caches are already clean.
        flag("runtimeCachesDirty").setBoolean(null, false);
        flag("globalOutputsDirty").setBoolean(null, false);
        Class<?> indexType = Class.forName(SkyNetworkRegistry.class.getName() + "$DimensionIndex");
        Constructor<?> constructor = indexType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object index = constructor.newInstance();
        Field dimensions = flag("DIMENSIONS");
        @SuppressWarnings("unchecked")
        Map<Object, Object> dimensionMap = (Map<Object, Object>) dimensions.get(null);
        // Output aggregation only visits the map values; a synthetic key avoids
        // initializing Minecraft's dimension registries in this unit fixture.
        dimensionMap.put(null, index);
        Field endpoints = indexType.getDeclaredField("virtualEndpoints");
        endpoints.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<LogisticsEndpoint> nodes = (Set<LogisticsEndpoint>) endpoints.get(index);
        // Use the shared endpoint interface to exercise actual indexing without booting a world.
        nodes.add((LogisticsEndpoint) Proxy.newProxyInstance(LogisticsEndpoint.class.getClassLoader(),
                new Class<?>[] {LogisticsEndpoint.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "getLineId" -> LINE;
                    case "getFaceMode" -> args[0] == Direction.NORTH ? mode : NodeFaceMode.NONE;
                    case "getTargetPos", "getBlockPos" -> BlockPos.ZERO;
                    case "getAccessSide" -> Direction.SOUTH;
                    case "isItemsEnabled" -> true;
                    case "isFluidsEnabled", "isEnergyEnabled", "hasDimensionUpgrade" -> false;
                    default -> throw new AssertionError("Unexpected endpoint call: " + method.getName());
                }));
        Method rebuild = SkyNetworkRegistry.class.getDeclaredMethod("rebuild", ServerLevel.class, indexType);
        rebuild.setAccessible(true);
        // Config loading belongs to the game loader; seed and restore the one option
        // consulted by the empty pipe scan in this isolated registry fixture.
        Object option = SkyLogisticsConfig.SERVER.enforceSimplePipeConnectionLimit;
        Field cached = option.getClass().getSuperclass().getDeclaredField("cachedValue");
        cached.setAccessible(true);
        Object previous = cached.get(option);
        cached.set(option, false);
        try {
            rebuild.invoke(null, null, index);
        } finally {
            cached.set(option, previous);
        }
    }

    private static Field flag(String name) throws Exception {
        Field field = SkyNetworkRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
