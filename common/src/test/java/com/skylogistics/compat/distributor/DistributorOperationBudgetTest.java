package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistributorOperationBudgetTest {
    @Test
    void consumesTheActiveLineBudgetAndReportsExhaustion() {
        try (DistributorOperationBudget.Scope scope = DistributorOperationBudget.open(2)) {
            assertTrue(DistributorOperationBudget.takeOperation());
            assertTrue(DistributorOperationBudget.takeOperation());
            assertFalse(DistributorOperationBudget.takeOperation());
            assertTrue(DistributorOperationBudget.exhausted());
            assertEquals(2, scope.consumedOperations());
        }
    }

    @Test
    void nestedScopesShareTheSameLineBudget() {
        try (DistributorOperationBudget.Scope outer = DistributorOperationBudget.open(2)) {
            assertTrue(DistributorOperationBudget.takeOperation());
            try (DistributorOperationBudget.Scope inner = DistributorOperationBudget.open(99)) {
                assertTrue(DistributorOperationBudget.takeOperation());
                assertFalse(DistributorOperationBudget.takeOperation());
                assertEquals(1, inner.consumedOperations());
            }
            assertEquals(2, outer.consumedOperations());
        }
    }

    @Test
    void callsOutsideALineScopeRemainAvailable() {
        assertTrue(DistributorOperationBudget.takeOperation());
        assertFalse(DistributorOperationBudget.exhausted());
    }
}
