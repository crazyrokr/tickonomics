package com.tickonomics.computation.signal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TimeOfDayThresholdManagerTest {

    private TimeOfDayThresholdManager manager;

    @BeforeEach
    void setUp() {
        manager = new TimeOfDayThresholdManager();
    }

    private Instant atUtc(int hour, int minute) {
        return LocalDateTime.of(2026, 6, 3, hour, minute)
                .toInstant(ZoneOffset.UTC);
    }

    @Nested
    class UsMarketOpen {

        @Test
        void givenTimeAtMarketOpenStart_whenCompute_thenOpenFactor() {
            /*
             * Given: timestamp at 13:30 UTC (US market open)
             * When: compute() is called
             * Then: timeOfDay = "OPEN", currentFactor = 1.5
             */
            var result = manager.compute(atUtc(13, 30));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_OPEN, result.timeOfDay());
            assertEquals(1.5, result.currentFactor(), 1e-10);
            assertEquals(1.5, result.openCloseFactor(), 1e-10);
        }

        @Test
        void givenTimeAtMidOpenPeriod_whenCompute_thenOpenFactor() {
            /*
             * Given: timestamp at 14:00 UTC (mid open period)
             * When: compute() is called
             * Then: timeOfDay = "OPEN", currentFactor = 1.5
             */
            var result = manager.compute(atUtc(14, 0));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_OPEN, result.timeOfDay());
            assertEquals(1.5, result.currentFactor(), 1e-10);
        }

        @Test
        void givenTimeJustBeforeOpenEnd_whenCompute_thenOpenFactor() {
            /*
             * Given: timestamp at 14:29 UTC (just before open period ends)
             * When: compute() is called
             * Then: timeOfDay = "OPEN"
             */
            var result = manager.compute(atUtc(14, 29));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_OPEN, result.timeOfDay());
        }
    }

    @Nested
    class UsMarketClose {

        @Test
        void givenTimeAtMarketCloseStart_whenCompute_thenCloseFactor() {
            /*
             * Given: timestamp at 19:00 UTC (US market close)
             * When: compute() is called
             * Then: timeOfDay = "CLOSE", currentFactor = 1.5
             */
            var result = manager.compute(atUtc(19, 0));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_CLOSE, result.timeOfDay());
            assertEquals(1.5, result.currentFactor(), 1e-10);
        }

        @Test
        void givenTimeAtMidClosePeriod_whenCompute_thenCloseFactor() {
            /*
             * Given: timestamp at 19:30 UTC (mid close period)
             * When: compute() is called
             * Then: timeOfDay = "CLOSE", currentFactor = 1.5
             */
            var result = manager.compute(atUtc(19, 30));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_CLOSE, result.timeOfDay());
            assertEquals(1.5, result.currentFactor(), 1e-10);
        }
    }

    @Nested
    class MiddayPeriod {

        @Test
        void givenTimeAtMiddayStart_whenCompute_thenMiddayFactor() {
            /*
             * Given: timestamp at 15:00 UTC (midday start)
             * When: compute() is called
             * Then: timeOfDay = "MIDDAY", currentFactor = 0.8
             */
            var result = manager.compute(atUtc(15, 0));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_MIDDAY, result.timeOfDay());
            assertEquals(0.8, result.currentFactor(), 1e-10);
        }

        @Test
        void givenTimeAtMiddayEnd_whenCompute_thenMiddayFactor() {
            /*
             * Given: timestamp at 17:59 UTC (just before midday end)
             * When: compute() is called
             * Then: timeOfDay = "MIDDAY", currentFactor = 0.8
             */
            var result = manager.compute(atUtc(17, 59));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_MIDDAY, result.timeOfDay());
            assertEquals(0.8, result.currentFactor(), 1e-10);
        }
    }

    @Nested
    class OtherPeriods {

        @Test
        void givenTimeOutsideAllPeriods_whenCompute_thenDefaultFactor() {
            /*
             * Given: timestamp at 22:00 UTC (outside all special periods)
             * When: compute() is called
             * Then: timeOfDay = "OTHER", currentFactor = 1.0
             */
            var result = manager.compute(atUtc(22, 0));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_OTHER, result.timeOfDay());
            assertEquals(1.0, result.currentFactor(), 1e-10);
        }

        @Test
        void givenTimeBetweenOpenAndMidday_whenCompute_thenDefaultFactor() {
            /*
             * Given: timestamp at 14:30 UTC (gap between open and midday)
             * When: compute() is called
             * Then: timeOfDay = "OTHER"
             */
            var result = manager.compute(atUtc(14, 30));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_OTHER, result.timeOfDay());
            assertEquals(1.0, result.currentFactor(), 1e-10);
        }

        @Test
        void givenEarlyMorning_whenCompute_thenDefaultFactor() {
            /*
             * Given: timestamp at 03:00 UTC
             * When: compute() is called
             * Then: timeOfDay = "OTHER", currentFactor = 1.0
             */
            var result = manager.compute(atUtc(3, 0));

            assertEquals(TimeOfDayThresholdManager.TIME_OF_DAY_OTHER, result.timeOfDay());
            assertEquals(1.0, result.currentFactor(), 1e-10);
        }
    }

    @Nested
    class ThresholdAdjustmentRecord {

        @Test
        void givenAnyTime_whenCompute_thenOpenCloseAndMiddayFactorsAlwaysPresent() {
            /*
             * Given: any timestamp
             * When: compute() is called
             * Then: openCloseFactor=1.5 and middayFactor=0.8 are always in the result
             */
            var result = manager.compute(atUtc(22, 0));

            assertEquals(1.5, result.openCloseFactor(), 1e-10);
            assertEquals(0.8, result.middayFactor(), 1e-10);
            assertEquals(1.0, result.currentFactor(), 1e-10);
        }
    }
}
