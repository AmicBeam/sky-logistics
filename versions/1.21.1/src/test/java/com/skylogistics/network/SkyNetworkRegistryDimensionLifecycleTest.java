package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.skylogistics.config.SkyLogisticsConfig;
import com.skylogistics.util.NodeFaceMode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceKey;
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

        assertEquals(1, SkyNetworkRegistry.globalItemOutputs(LINE).size(),
                "An extractor that cached no targets must discover the first inserter in another dimension");
        assertFalse(SkyNetworkRegistry.globalItemOutputs(LINE).get(0).node().hasDimensionUpgrade(),
                "The receiving endpoint must not need a dimension upgrade");
    }

    @Test
    void firstRebuildInAnotherDimensionInvalidatesPreviouslyBuiltScheduler() throws Exception {
        rebuildNewDimensionAfterServerHasTicked(NodeFaceMode.INPUT);

        assertEquals(1, ready().size());
        assertEquals(LINE, ready().get(0).lineId());
    }

    @Test
    void rebuildingSleepingInputsReplacesSchedulerEntriesAndInvalidatesInputSnapshot() throws Exception {
        Object index = rebuildNewDimensionAfterServerHasTicked(NodeFaceMode.INPUT);
        var before = inputs();
        assertEquals(1, before.size());
        assertSame(before, inputs());
        var oldLine = ready().get(0);
        oldLine.sleepUntil(1000L);
        assertEquals(0, ready().size());
        rebuild(index);
        assertEquals(1, ready().size());
        assertNotSame(oldLine, ready().get(0));
        assertTrue(ready().get(0).canProcess(0L));
        assertTrue(((Map<?, ?>) flag("SCHEDULED_WAKE").get(null)).isEmpty());
        assertNotSame(before, inputs());
    }

    @Test
    void changingLineInvalidatesBothOldAndNewRoutesAndKeepsExistingSnapshotStable() throws Exception {
        UUID next = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var lineId = new AtomicReference<>(LINE);
        Object index = newDimension(NodeFaceMode.OUTPUT, lineId);
        var oldSnapshot = SkyNetworkRegistry.globalItemOutputs(LINE);
        SkyNetworkRegistry.globalItemOutputs(next);
        lineId.set(next);
        rebuild(index);
        assertTrue(SkyNetworkRegistry.globalItemOutputs(LINE).isEmpty());
        assertEquals(1, SkyNetworkRegistry.globalItemOutputs(next).size());
        assertEquals(1, oldSnapshot.size(), "In-flight snapshots must not be mutated during a rebuild");
    }

    @Test
    void unloadingDimensionRemovesItsRoutesAndScheduledWork() throws Exception {
        Object index = rebuildNewDimensionAfterServerHasTicked(NodeFaceMode.INPUT);
        assertEquals(1, inputs().size());
        ready().get(0).sleepUntil(1000L);
        Method remove = SkyNetworkRegistry.class.getDeclaredMethod("removeDimension", ResourceKey.class, index.getClass());
        remove.setAccessible(true);
        remove.invoke(null, null, index);
        assertTrue(inputs().isEmpty());
        assertEquals(0, ready().size());
        assertTrue(((Map<?, ?>) flag("SCHEDULED_WAKE").get(null)).isEmpty());
        rebuildNewDimensionAfterServerHasTicked(NodeFaceMode.INPUT);
        assertEquals(1, inputs().size(), "Reloaded endpoints must replace the cached empty route");
        assertEquals(1, ready().size());
    }

    @Test
    void dimensionUpgradeSelectsGlobalScopeEvenWhenItsSnapshotIsEmpty() throws Exception {
        rebuildNewDimensionAfterServerHasTicked(NodeFaceMode.OUTPUT);
        var local = SkyNetworkRegistry.globalItemOutputs(LINE);
        assertFalse(local.isEmpty());
        Method select = SkyNetworkTicker.class.getDeclaredMethod("targetsFor", boolean.class, List.class, List.class);
        select.setAccessible(true);
        assertTrue(((List<?>) select.invoke(null, true, local, List.of())).isEmpty());
        assertSame(local, select.invoke(null, false, local, List.of()));
    }

    private static SkyNetworkRegistry.ReadyLines ready() throws Exception {
        Method method = SkyNetworkRegistry.class.getDeclaredMethod("activeLinesView");
        method.setAccessible(true);
        return (SkyNetworkRegistry.ReadyLines) method.invoke(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<SkyNetworkRegistry.CachedEndpoint> inputs() throws Exception {
        Class<? extends Enum> query = (Class<? extends Enum>) Class.forName(SkyNetworkRegistry.class.getName() + "$RouteQuery");
        Method method = SkyNetworkRegistry.class.getDeclaredMethod("globalEndpoints", UUID.class, query, Function.class);
        method.setAccessible(true);
        Method view = SkyNetworkRegistry.LineIndex.class.getDeclaredMethod("itemInputsView");
        view.setAccessible(true);
        Function<SkyNetworkRegistry.LineIndex, List<SkyNetworkRegistry.CachedEndpoint>> select = line -> {
            try {
                return (List<SkyNetworkRegistry.CachedEndpoint>) view.invoke(line);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        };
        return (List<SkyNetworkRegistry.CachedEndpoint>) method.invoke(null, LINE,
                Enum.valueOf(query, "ITEM_INPUT"), select);
    }

    private static Object rebuildNewDimensionAfterServerHasTicked(NodeFaceMode mode) throws Exception {
        return newDimension(mode, new AtomicReference<>(LINE));
    }

    private static Object newDimension(NodeFaceMode mode, AtomicReference<UUID> lineId) throws Exception {
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
                    case "getLineId" -> lineId.get();
                    case "getFaceMode" -> args[0] == Direction.NORTH ? mode : NodeFaceMode.NONE;
                    case "getTargetPos", "getBlockPos" -> BlockPos.ZERO;
                    case "getAccessSide" -> Direction.SOUTH;
                    case "isItemsEnabled" -> true;
                    case "isFluidsEnabled", "isEnergyEnabled", "hasDimensionUpgrade" -> false;
                    default -> throw new AssertionError("Unexpected endpoint call: " + method.getName());
                }));
        rebuild(index);
        return index;
    }

    private static void rebuild(Object index) throws Exception {
        Method rebuild = SkyNetworkRegistry.class.getDeclaredMethod("rebuild", ServerLevel.class, index.getClass());
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
