package com.skylogistics.network;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KleisRuntimeEndpointContractTest {
    @Test
    void savedDataEndpointIsNotABlockEntity() {
        assertSame(Object.class, KleisRuntimeEndpoint.class.getSuperclass());
        assertTrue(ConfigurableLogisticsEndpoint.class.isAssignableFrom(KleisRuntimeEndpoint.class));
    }
}
