package com.tickonomics.computation.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tickonomics.persistence.entity.TickData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

class PriceBasedIndicatorComputerTest {

    private static List<TickData> series(double... prices) {
        List<TickData> ticks = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            ticks.add(new TickData(Instant.parse("2025-01-01T00:00:00Z").plusSeconds((long) i * 86400),
                    "SPY", BigDecimal.valueOf(prices[i]), 1000L + i, new int[]{}));
        }
        return ticks;
    }

    private final PriceBasedIndicatorComputer computer = new PriceBasedIndicatorComputer();

    @Nested
    class PriceAndReturn {

        @Test
        void givenSeries_whenComputeAtLastIndex_thenPriceAndChangePresent() {
            var ticks = series(100.0, 102.0, 105.0);
            Map<String, Double> result = computer.compute(ticks, 2);

            assertEquals(105.0, result.get("price"));
            assertEquals((105.0 - 102.0) / 102.0, result.get("priceChange"), 1e-9);
        }

        @Test
        void givenFirstBar_whenCompute_thenNoPriceChangeKey() {
            var ticks = series(100.0, 102.0);
            Map<String, Double> result = computer.compute(ticks, 0);

            assertNull(result.get("priceChange"));
            assertEquals(100.0, result.get("price"));
        }
    }

    @Nested
    class Rsi {

        @Test
        void givenMonotonicRise_whenCompute_thenRsiNear100() {
            double[] prices = new double[30];
            double p = 100.0;
            for (int i = 0; i < prices.length; i++) {
                p *= 1.01;
                prices[i] = p;
            }
            Map<String, Double> result = computer.compute(series(prices), prices.length - 1);

            assertTrue(result.get("rsi") > 99.0, "rising series should push RSI toward 100");
        }

        @Test
        void givenInsufficientBars_whenCompute_thenNoRsiKey() {
            Map<String, Double> result = computer.compute(series(100.0, 101.0, 102.0), 2);

            assertNull(result.get("rsi"));
        }
    }

    @Nested
    class BollingerAndVolatility {

        @Test
        void givenFullWindow_whenCompute_thenBandsStraddlePrice() {
            double[] prices = new double[22];
            for (int i = 0; i < prices.length; i++) {
                prices[i] = 100.0 + (i % 2 == 0 ? 1 : -1);
            }
            Map<String, Double> result = computer.compute(series(prices), prices.length - 1);

            assertNotNull(result.get("upperBand"));
            assertNotNull(result.get("lowerBand"));
            assertTrue(result.get("upperBand") > result.get("lowerBand"));
            assertNotNull(result.get("sigma"));
        }

        @Test
        void givenShortWindow_whenCompute_thenNoBollingerKeys() {
            Map<String, Double> result = computer.compute(series(100.0, 101.0), 1);

            assertNull(result.get("upperBand"));
            assertNull(result.get("lowerBand"));
        }
    }

    @Nested
    class HighLowVolume {

        @Test
        void givenSeries_whenCompute_thenHighLowAndVolumePresent() {
            var ticks = series(100.0, 110.0, 95.0, 105.0);
            Map<String, Double> result = computer.compute(ticks, 3);

            assertEquals(110.0, result.get("high20"));
            assertEquals(95.0, result.get("low20"));
            assertEquals(1003.0, result.get("volumeCurrent"));
            assertNotNull(result.get("volumeAvg"));
        }

        @Test
        void givenEmptyOrNull_whenCompute_thenEmptyMap() {
            assertTrue(computer.compute(null, 0).isEmpty());
            assertTrue(computer.compute(List.of(), 0).isEmpty());
        }
    }
}
