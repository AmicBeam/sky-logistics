package com.skylogistics.compat.astages;

import java.util.EnumMap;
import java.util.Map;

public final class StageTransferRates {
    private final EnumMap<TransferResource, Long> rates;

    public StageTransferRates(Map<TransferResource, Long> rates) {
        this.rates = new EnumMap<>(TransferResource.class);
        rates.forEach((resource, rate) -> {
            if (rate == null || rate < 1L) {
                throw new IllegalArgumentException("Stage transfer rates must be positive");
            }
            this.rates.put(resource, rate);
        });
    }

    public long getOrDefault(TransferResource resource, long fallback) {
        return rates.getOrDefault(resource, fallback);
    }

    public StageTransferRates mergeMax(StageTransferRates other) {
        EnumMap<TransferResource, Long> merged = new EnumMap<>(rates);
        other.rates.forEach((resource, rate) -> merged.merge(resource, rate, Math::max));
        return new StageTransferRates(merged);
    }
}
