package com.skylogistics.util;

import static com.skylogistics.util.SimplePipeRecoveryPolicy.Neighbor.*;
import static com.skylogistics.util.SimplePipeConnection.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SimplePipeRecoveryPolicyTest {
    @Test void savedExtractorSurvivesUnloadedAndTemporarilyMissingCapability() {
        var unloaded = SimplePipeRecoveryPolicy.resolve(false, true, true, UNLOADED);
        assertEquals(EXTRACT, unloaded.connection());
        assertTrue(unloaded.retry());
        var pending = SimplePipeRecoveryPolicy.resolve(false, true, true, PENDING_CONTAINER);
        assertEquals(EXTRACT, pending.connection());
        assertTrue(pending.retry());
        var restored = SimplePipeRecoveryPolicy.resolve(false, true, true, CONTAINER);
        assertEquals(EXTRACT, restored.connection());
        assertFalse(restored.retry());
    }

    @Test void savedInserterKeepsItsConnectionAcrossAnotherSaveWhileCapabilityIsMissing() {
        var pending = SimplePipeRecoveryPolicy.resolve(false, false, true, PENDING_CONTAINER);
        assertEquals(INSERT, pending.connection());
        assertTrue(pending.retry());
        var reloaded = SimplePipeRecoveryPolicy.resolve(false, false, pending.connection() != NONE, PENDING_CONTAINER);
        assertEquals(INSERT, reloaded.connection());
        assertTrue(reloaded.retry());
        var restored = SimplePipeRecoveryPolicy.resolve(false, false, true, CONTAINER);
        assertEquals(INSERT, restored.connection());
        assertFalse(restored.retry());
    }

    @Test void manualDisconnectionsAndIncompatibleNeighborsDoNotPollOrReconnect() {
        for (var neighbor : SimplePipeRecoveryPolicy.Neighbor.values()) {
            var result = SimplePipeRecoveryPolicy.resolve(true, true, false, neighbor);
            assertEquals(NONE, result.connection());
            assertFalse(result.retry());
        }
        assertEquals(NONE, SimplePipeRecoveryPolicy.resolve(false, false, true, BLOCKED_PIPE).connection());
        assertFalse(SimplePipeRecoveryPolicy.resolve(false, false, false, PENDING_CONTAINER).retry());
        assertFalse(SimplePipeRecoveryPolicy.resolve(false, false, false, UNLOADED).retry());
        assertFalse(SimplePipeRecoveryPolicy.resolve(false, true, true, OTHER).retry());
    }

    @Test void disconnectedSavedExtractorCanRecoverAndPipeLinksRemainPipeLinks() {
        assertTrue(SimplePipeRecoveryPolicy.resolve(false, true, false, PENDING_CONTAINER).retry());
        assertEquals(EXTRACT, SimplePipeRecoveryPolicy.resolve(false, true, false, CONTAINER).connection());
        assertEquals(SimplePipeConnection.PIPE, SimplePipeRecoveryPolicy.resolve(false, true, true, SimplePipeRecoveryPolicy.Neighbor.PIPE).connection());
    }
}
