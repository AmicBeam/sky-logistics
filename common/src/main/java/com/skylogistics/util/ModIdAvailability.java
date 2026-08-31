package com.skylogistics.util;

import java.util.List;
import java.util.function.Predicate;

public final class ModIdAvailability {
    private ModIdAvailability() {
    }

    public static boolean anyLoaded(List<? extends Object> configuredModIds, Predicate<String> isLoaded) {
        return configuredModIds.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .anyMatch(isLoaded);
    }
}
