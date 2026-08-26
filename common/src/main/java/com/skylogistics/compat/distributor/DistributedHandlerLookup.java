package com.skylogistics.compat.distributor;

public interface DistributedHandlerLookup<T> {
    int size();

    T handler(int index);

    boolean takeOperation();

    default boolean sequentialInsertion() { return false; }

    default boolean budgetExhausted() { return false; }

    default long gameTime() { return Long.MIN_VALUE; }

    default AdaptiveRoutingConfig adaptiveRoutingConfig() { return AdaptiveRoutingConfig.DISABLED; }
}
