package com.skylogistics.compat.distributor;

/** Marks a distributor proxy whose current tick budget stopped an otherwise incomplete scan. */
public interface BudgetedDistributorHandler {
    boolean distributorBudgetExhausted();
}
