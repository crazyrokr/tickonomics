package com.tickonomics.ingestion.quality;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCancellationMonitorTest {

    private OrderCancellationMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new OrderCancellationMonitor();
    }

    @Nested
    class RecordingOrders {

        @Test
        void givenCanceledOrder_whenRecordOrder_thenCancellationCounted() {
            // Given
            String symbol = "AAPL";

            // When
            monitor.recordOrder(symbol, true);

            // Then
            OrderCancellationMonitor.CancellationStats stats = monitor.getStats(symbol);
            assertThat(stats.totalOrders()).isEqualTo(1);
            assertThat(stats.canceledOrders()).isEqualTo(1);
        }

        @Test
        void givenNonCanceledOrder_whenRecordOrder_thenOnlyTotalCounted() {
            // Given
            String symbol = "AAPL";

            // When
            monitor.recordOrder(symbol, false);

            // Then
            OrderCancellationMonitor.CancellationStats stats = monitor.getStats(symbol);
            assertThat(stats.totalOrders()).isEqualTo(1);
            assertThat(stats.canceledOrders()).isZero();
        }

        @Test
        void givenMixedOrders_whenRecordOrder_thenAccumulatesCorrectly() {
            // Given
            String symbol = "AAPL";

            // When
            monitor.recordOrder(symbol, true);
            monitor.recordOrder(symbol, false);
            monitor.recordOrder(symbol, true);
            monitor.recordOrder(symbol, true);
            monitor.recordOrder(symbol, false);

            // Then
            OrderCancellationMonitor.CancellationStats stats = monitor.getStats(symbol);
            assertThat(stats.totalOrders()).isEqualTo(5);
            assertThat(stats.canceledOrders()).isEqualTo(3);
        }

        @Test
        void givenMultipleSymbols_whenRecordOrder_thenTracksIndependently() {
            // Given
            monitor.recordOrder("AAPL", true);
            monitor.recordOrder("AAPL", false);
            monitor.recordOrder("GOOG", true);

            // When
            OrderCancellationMonitor.CancellationStats aapl = monitor.getStats("AAPL");
            OrderCancellationMonitor.CancellationStats goog = monitor.getStats("GOOG");

            // Then
            assertThat(aapl.totalOrders()).isEqualTo(2);
            assertThat(aapl.canceledOrders()).isEqualTo(1);
            assertThat(goog.totalOrders()).isEqualTo(1);
            assertThat(goog.canceledOrders()).isEqualTo(1);
        }
    }

    @Nested
    class CancellationRatio {

        @Test
        void givenAllCanceled_whenGetCancellationRatio_thenReturnsOne() {
            // Given
            for (int i = 0; i < 10; i++) {
                monitor.recordOrder("AAPL", true);
            }

            // When
            double ratio = monitor.getCancellationRatio("AAPL");

            // Then
            assertThat(ratio).isEqualTo(1.0);
        }

        @Test
        void givenNoneCanceled_whenGetCancellationRatio_thenReturnsZero() {
            // Given
            for (int i = 0; i < 10; i++) {
                monitor.recordOrder("AAPL", false);
            }

            // When
            double ratio = monitor.getCancellationRatio("AAPL");

            // Then
            assertThat(ratio).isEqualTo(0.0);
        }

        @Test
        void givenMixedOrders_whenGetCancellationRatio_thenReturnsCorrectRatio() {
            // Given - 3 canceled out of 10
            for (int i = 0; i < 3; i++) {
                monitor.recordOrder("AAPL", true);
            }
            for (int i = 0; i < 7; i++) {
                monitor.recordOrder("AAPL", false);
            }

            // When
            double ratio = monitor.getCancellationRatio("AAPL");

            // Then
            assertThat(ratio).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        void givenUnknownSymbol_whenGetCancellationRatio_thenReturnsZero() {
            // Given - no orders recorded for MSFT

            // When
            double ratio = monitor.getCancellationRatio("MSFT");

            // Then
            assertThat(ratio).isEqualTo(0.0);
        }
    }

    @Nested
    class HighRateDetection {

        @Test
        void givenHighCancellationRateWithSufficientSample_whenIsHighCancellationRate_thenReturnTrue() {
            // Given - 40 canceled out of 50 = 0.8 ratio
            for (int i = 0; i < 40; i++) {
                monitor.recordOrder("AAPL", true);
            }
            for (int i = 0; i < 10; i++) {
                monitor.recordOrder("AAPL", false);
            }

            // When
            boolean result = monitor.isHighCancellationRate("AAPL");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void givenHighRateButInsufficientSample_whenIsHighCancellationRate_thenReturnFalse() {
            // Given - 8 canceled out of 10 = 0.8 ratio but below minimum 50
            for (int i = 0; i < 8; i++) {
                monitor.recordOrder("AAPL", true);
            }
            for (int i = 0; i < 2; i++) {
                monitor.recordOrder("AAPL", false);
            }

            // When
            boolean result = monitor.isHighCancellationRate("AAPL");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenLowCancellationRateWithSufficientSample_whenIsHighCancellationRate_thenReturnFalse() {
            // Given - 10 canceled out of 100 = 0.1 ratio
            for (int i = 0; i < 10; i++) {
                monitor.recordOrder("AAPL", true);
            }
            for (int i = 0; i < 90; i++) {
                monitor.recordOrder("AAPL", false);
            }

            // When
            boolean result = monitor.isHighCancellationRate("AAPL");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void givenExactlySeventyPercent_whenIsHighCancellationRate_thenReturnFalse() {
            // Given - exactly 0.7 ratio (35 canceled, 15 filled, total 50)
            for (int i = 0; i < 35; i++) {
                monitor.recordOrder("AAPL", true);
            }
            for (int i = 0; i < 15; i++) {
                monitor.recordOrder("AAPL", false);
            }

            // When
            boolean result = monitor.isHighCancellationRate("AAPL");

            // Then - threshold is strictly greater than 0.7
            assertThat(result).isFalse();
        }

        @Test
        void givenUnknownSymbol_whenIsHighCancellationRate_thenReturnFalse() {
            // Given - no orders for MSFT

            // When
            boolean result = monitor.isHighCancellationRate("MSFT");

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class ResetBehavior {

        @Test
        void givenRecordedStats_whenResetHourlyStats_thenAllStatsCleared() {
            // Given
            for (int i = 0; i < 60; i++) {
                monitor.recordOrder("AAPL", true);
            }
            for (int i = 0; i < 30; i++) {
                monitor.recordOrder("GOOG", false);
            }
            assertThat(monitor.getAllStats()).hasSize(2);

            // When
            monitor.resetHourlyStats();

            // Then
            assertThat(monitor.getAllStats()).isEmpty();
            assertThat(monitor.getStats("AAPL").totalOrders()).isZero();
            assertThat(monitor.getStats("GOOG").totalOrders()).isZero();
        }

        @Test
        void givenResetMonitor_whenRecordNewOrders_thenTracksFresh() {
            // Given
            monitor.recordOrder("AAPL", true);
            monitor.resetHourlyStats();

            // When
            monitor.recordOrder("AAPL", false);
            monitor.recordOrder("AAPL", false);

            // Then
            OrderCancellationMonitor.CancellationStats stats = monitor.getStats("AAPL");
            assertThat(stats.totalOrders()).isEqualTo(2);
            assertThat(stats.canceledOrders()).isZero();
        }
    }

    @Nested
    class HighCancellationSymbols {

        @Test
        void givenMultipleSymbols_whenGetHighCancellationSymbols_thenReturnsOnlyAboveThreshold() {
            // Given - AAPL: 40/50 = 0.8, GOOG: 10/50 = 0.2, MSFT: 45/50 = 0.9
            for (int i = 0; i < 40; i++) {
                monitor.recordOrder("AAPL", true);
            }
            for (int i = 0; i < 10; i++) {
                monitor.recordOrder("AAPL", false);
            }
            for (int i = 0; i < 10; i++) {
                monitor.recordOrder("GOOG", true);
            }
            for (int i = 0; i < 40; i++) {
                monitor.recordOrder("GOOG", false);
            }
            for (int i = 0; i < 45; i++) {
                monitor.recordOrder("MSFT", true);
            }
            for (int i = 0; i < 5; i++) {
                monitor.recordOrder("MSFT", false);
            }

            // When
            List<String> result = monitor.getHighCancellationSymbols(0.7);

            // Then
            assertThat(result).containsExactlyInAnyOrder("AAPL", "MSFT");
            assertThat(result).doesNotContain("GOOG");
        }

        @Test
        void givenNoSymbolsAboveThreshold_whenGetHighCancellationSymbols_thenReturnsEmpty() {
            // Given - all below threshold
            for (int i = 0; i < 50; i++) {
                monitor.recordOrder("AAPL", false);
            }

            // When
            List<String> result = monitor.getHighCancellationSymbols(0.7);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void givenCustomThreshold_whenGetHighCancellationSymbols_thenAppliesThreshold() {
            // Given - 30/50 = 0.6
            for (int i = 0; i < 30; i++) {
                monitor.recordOrder("AAPL", true);
            }
            for (int i = 0; i < 20; i++) {
                monitor.recordOrder("AAPL", false);
            }

            // When
            List<String> strictResult = monitor.getHighCancellationSymbols(0.7);
            List<String> lenientResult = monitor.getHighCancellationSymbols(0.5);

            // Then
            assertThat(strictResult).isEmpty();
            assertThat(lenientResult).containsExactly("AAPL");
        }
    }

    @Nested
    class EmptyStats {

        @Test
        void givenNoRecordedOrders_whenGetStats_thenReturnsDefaultEmpty() {
            // Given - no orders recorded

            // When
            OrderCancellationMonitor.CancellationStats stats = monitor.getStats("UNKNOWN");

            // Then
            assertThat(stats.totalOrders()).isZero();
            assertThat(stats.canceledOrders()).isZero();
        }

        @Test
        void givenNoRecordedOrders_whenGetAllStats_thenReturnsEmptyMap() {
            // Given - no orders recorded

            // When
            Map<String, OrderCancellationMonitor.CancellationStats> all = monitor.getAllStats();

            // Then
            assertThat(all).isEmpty();
        }

        @Test
        void givenNoRecordedOrders_whenGetCancellationRatio_thenReturnsZero() {
            // Given - no orders for symbol

            // When
            double ratio = monitor.getCancellationRatio("UNKNOWN");

            // Then
            assertThat(ratio).isEqualTo(0.0);
        }
    }
}
