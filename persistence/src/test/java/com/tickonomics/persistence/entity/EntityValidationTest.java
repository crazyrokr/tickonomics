package com.tickonomics.persistence.entity;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EntityValidationTest {

    private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z");

    @Nested
    class TickDataTests {
        @Test
        void givenValidArgs_whenConstruct_thenFieldsSet() {
            var tick = new TickData(NOW, "SPY", 450.50, 1000, new int[]{0, 1});
            assertEquals(NOW, tick.time());
            assertEquals("SPY", tick.symbol());
            assertEquals(450.50, tick.price());
            assertEquals(1000, tick.volume());
            assertArrayEquals(new int[]{0, 1}, tick.conditions());
        }

        @Test
        void givenNullTime_whenConstruct_thenThrows() {
            assertThrows(NullPointerException.class,
                    () -> new TickData(null, "SPY", 450.50, 1000, new int[]{}));
        }

        @Test
        void givenBlankSymbol_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TickData(NOW, "", 450.50, 1000, new int[]{}));
        }

        @Test
        void givenNegativePrice_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TickData(NOW, "SPY", -1.0, 1000, new int[]{}));
        }

        @Test
        void givenNegativeVolume_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TickData(NOW, "SPY", 450.50, -1, new int[]{}));
        }
    }

    @Nested
    class RateSnapshotTests {
        @Test
        void givenValidArgs_whenConstruct_thenFieldsSet() {
            var snapshot = new RateSnapshot(NOW, "SOFR", 4.29, "NY_FED");
            assertEquals(NOW, snapshot.time());
            assertEquals("SOFR", snapshot.rateType());
            assertEquals(4.29, snapshot.value());
            assertEquals("NY_FED", snapshot.source());
        }

        @Test
        void givenNullTime_whenConstruct_thenThrows() {
            assertThrows(NullPointerException.class,
                    () -> new RateSnapshot(null, "SOFR", 4.29, "NY_FED"));
        }

        @Test
        void givenBlankRateType_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RateSnapshot(NOW, "", 4.29, "NY_FED"));
        }

        @Test
        void givenNaNValue_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new RateSnapshot(NOW, "SOFR", Double.NaN, "NY_FED"));
        }
    }

    @Nested
    class IliHistoryTests {
        @Test
        void givenValidArgs_whenConstruct_thenFieldsSet() {
            var entry = new IliHistory(NOW, 0.85, 1.2, -0.5, 0.3, "VALID", "{\"w1\":0.4}", null, null);
            assertEquals(NOW, entry.time());
            assertEquals(0.85, entry.iliValue());
            assertEquals("VALID", entry.dataStatus());
        }

        @Test
        void givenNullTime_whenConstruct_thenThrows() {
            assertThrows(NullPointerException.class,
                    () -> new IliHistory(null, 0.85, 1.2, -0.5, 0.3, "VALID", null, null, null));
        }

        @Test
        void givenBlankDataStatus_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new IliHistory(NOW, 0.85, 1.2, -0.5, 0.3, "", null, null, null));
        }
    }

    @Nested
    class ZscoreSeriesTests {
        @Test
        void givenValidArgs_whenConstruct_thenFieldsSet() {
            var entry = new ZscoreSeries(NOW, "Z_RRP", 0.05, 1.2, 252);
            assertEquals(NOW, entry.time());
            assertEquals("Z_RRP", entry.component());
            assertEquals(252, entry.lookbackDays());
        }

        @Test
        void givenNullTime_whenConstruct_thenThrows() {
            assertThrows(NullPointerException.class,
                    () -> new ZscoreSeries(null, "Z_RRP", 0.05, 1.2, 252));
        }

        @Test
        void givenZeroLookback_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ZscoreSeries(NOW, "Z_RRP", 0.05, 1.2, 0));
        }
    }

    @Nested
    class CorrelationOutputTests {
        @Test
        void givenValidArgs_whenConstruct_thenFieldsSet() {
            var output = new CorrelationOutput(NOW, "SPY", "PEARSON_CORRELATION", 0.85, 0.01, 100, 5, "POSITIVE");
            assertEquals(NOW, output.time());
            assertEquals("SPY", output.symbol());
            assertEquals("PEARSON_CORRELATION", output.metric());
        }

        @Test
        void givenNullTime_whenConstruct_thenThrows() {
            assertThrows(NullPointerException.class,
                    () -> new CorrelationOutput(null, "SPY", "PEARSON_CORRELATION", 0.85, null, null, null, null));
        }
    }

    @Nested
    class SignalLogTests {
        @Test
        void givenValidArgs_whenConstruct_thenFieldsSet() {
            var signal = new SignalLog(NOW, "SPY", "BUY", "ACTIONABLE", 85.0, 0.45, 2.5, 0.15, null);
            assertEquals("SPY", signal.symbol());
            assertEquals("BUY", signal.direction());
            assertEquals("ACTIONABLE", signal.status());
        }

        @Test
        void givenBlankSymbol_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SignalLog(NOW, "", "BUY", "ACTIONABLE", 85.0, 0.45, 2.5, 0.15, null));
        }

        @Test
        void givenNullDirection_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SignalLog(NOW, "SPY", null, "ACTIONABLE", 85.0, 0.45, 2.5, 0.15, null));
        }
    }

    @Nested
    class IngestionDlqEntryTests {
        @Test
        void givenValidArgs_whenConstruct_thenFieldsSet() {
            var entry = new IngestionDlqEntry(NOW, "FRED", "{\"key\":\"value\"}", "timeout", null);
            assertEquals("FRED", entry.source());
            assertEquals("{\"key\":\"value\"}", entry.payload());
        }

        @Test
        void givenBlankSource_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new IngestionDlqEntry(NOW, "", "{}", "error", null));
        }

        @Test
        void givenNullPayload_whenConstruct_thenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new IngestionDlqEntry(NOW, "FRED", null, "error", null));
        }
    }
}
