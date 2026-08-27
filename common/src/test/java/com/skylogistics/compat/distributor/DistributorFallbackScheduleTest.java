package com.skylogistics.compat.distributor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DistributorFallbackScheduleTest {
    @Test void guaranteesAPeriodicRecoveryAttempt() {
        DistributorFallbackSchedule schedule = new DistributorFallbackSchedule();

        assertTrue(schedule.consume(100L, 40));
        assertFalse(schedule.consume(139L, 40));
        assertTrue(schedule.consume(140L, 40));
        schedule.reset();
        assertTrue(schedule.consume(141L, 40));
    }
}
