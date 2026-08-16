package com.skylogistics.compat.distributor;

public interface DistributedHandlerLookup<T> {
    int size();

    T handler(int index);

    boolean takeOperation();
}
