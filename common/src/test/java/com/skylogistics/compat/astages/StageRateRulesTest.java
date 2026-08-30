package com.skylogistics.compat.astages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageRateRulesTest {
    @Test
    void requiresAtLeastOneUnlockForProgressionLimiting() {
        TransferRates initial = new TransferRates(64, 10_000, 10_000, 100_000, 100_000, 100_000);

        assertFalse(new StageRateRules(initial, Map.of()).hasUnlocks());
        assertTrue(new StageRateRules(initial,
                Map.of("first", stageRates(Map.of(TransferResource.ITEMS, 128L)))).hasUnlocks());
    }

    @Test
    void independentlyUnlocksEachResourceAndTakesHighestOwnedStage() {
        TransferRates initial = new TransferRates(64, 10_000, 10_000, 8_000, 100_000, 100_000, 100_000);
        StageTransferRates itemStage = stageRates(Map.of(TransferResource.ITEMS, 128L));
        StageTransferRates mixedStage = stageRates(Map.of(
                TransferResource.ITEMS, 96L,
                TransferResource.FLUIDS, 25_000L,
                TransferResource.SOULS, 30_000L,
                TransferResource.MANA, 250_000L));
        StageRateRules rules = new StageRateRules(initial, Map.of(
                "item_stage", itemStage,
                "mixed_stage", mixedStage));

        TransferRates result = rules.ratesFor(Set.of("item_stage", "mixed_stage", "unconfigured_stage"));

        assertEquals(128L, result.items());
        assertEquals(25_000L, result.fluids());
        assertEquals(10_000L, result.chemicals());
        assertEquals(30_000L, result.souls());
        assertEquals(100_000L, result.energy());
        assertEquals(250_000L, result.mana());
        assertEquals(100_000L, result.source());
    }

    @Test
    void valuesBelowInitialNeverReduceInitialRates() {
        TransferRates initial = new TransferRates(64, 10_000, 10_000, 100_000, 100_000, 100_000);
        StageRateRules rules = new StageRateRules(initial,
                Map.of("small", stageRates(Map.of(TransferResource.ITEMS, 1L))));

        assertEquals(initial, rules.ratesFor(Set.of("small")));
    }

    private static StageTransferRates stageRates(Map<TransferResource, Long> values) {
        return new StageTransferRates(new EnumMap<>(values));
    }
}
