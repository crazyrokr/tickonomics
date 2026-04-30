package com.tickonomics.computation.kpi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SignalGeneratorTest {

    private SignalGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SignalGenerator(new NormalizationService());
        generator.setCooldownMs(0);
        generator.setTransactionCostBps(0.1);
    }

    private double[] generateHistory(int count, double base, double step) {
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = base + i * step;
        }
        return values;
    }

    private IliResult validIli(double value) {
        return new IliResult(value, 1.0, -0.5, 0.3, IliResult.STATUS_VALID,
                new double[]{0.4, 0.35, 0.25}, null, null);
    }

    private IliResult degradedIli(double value) {
        return new IliResult(value, 1.0, -0.5, 0.3, IliResult.STATUS_DEGRADED,
                new double[]{0.4, 0.35, 0.25}, null, null);
    }

    private IliResult dislocatedIli(double value) {
        return new IliResult(value, 1.0, -0.5, 0.3, IliResult.STATUS_DISLOCATED,
                new double[]{0.4, 0.35, 0.25}, "DIVERGENT", 3.5);
    }

    @Nested
    class Evaluate {
        @Test
        void givenHighPercentile_whenEvaluate_thenBuySignal() {
            var history = generateHistory(100, 0.0, 0.01);
            var ili = validIli(2.0);
            var result = generator.evaluate("SPY", ili, history);
            assertTrue(result.isPresent());
            assertEquals(SignalResult.DIR_BUY, result.get().direction());
            assertEquals(SignalResult.STATUS_ACTIONABLE, result.get().status());
        }

        @Test
        void givenLowPercentile_whenEvaluate_thenSellSignal() {
            var history = generateHistory(100, 1.0, 0.01);
            var ili = validIli(-1.0);
            var result = generator.evaluate("SPY", ili, history);
            assertTrue(result.isPresent());
            assertEquals(SignalResult.DIR_SELL, result.get().direction());
        }

        @Test
        void givenMidPercentile_whenEvaluate_thenNoSignal() {
            var history = generateHistory(100, 0.0, 0.01);
            var ili = validIli(0.5);
            var result = generator.evaluate("SPY", ili, history);
            assertTrue(result.isEmpty());
        }

        @Test
        void givenDislocatedIli_whenEvaluate_thenEmpty() {
            var history = generateHistory(100, 0.0, 0.01);
            var ili = dislocatedIli(2.0);
            var result = generator.evaluate("SPY", ili, history);
            assertTrue(result.isEmpty());
        }

        @Test
        void givenInsufficientHistory_whenEvaluate_thenInsufficientStatus() {
            var history = generateHistory(10, 0.0, 0.01);
            var ili = validIli(2.0);
            var result = generator.evaluate("SPY", ili, history);
            assertTrue(result.isPresent());
            assertEquals(SignalResult.STATUS_INSUFFICIENT, result.get().status());
        }

        @Test
        void givenDegradedIli_whenEvaluate_thenSpeculativeStatus() {
            var history = generateHistory(100, 0.0, 0.01);
            var ili = degradedIli(2.0);
            var result = generator.evaluate("SPY", ili, history);
            assertTrue(result.isPresent());
            assertEquals(SignalResult.STATUS_SPECULATIVE, result.get().status());
        }

        @Test
        void givenNullHistory_whenEvaluate_thenInsufficientStatus() {
            var ili = validIli(2.0);
            var result = generator.evaluate("SPY", ili, null);
            assertTrue(result.isPresent());
            assertEquals(SignalResult.STATUS_INSUFFICIENT, result.get().status());
        }
    }

    @Nested
    class DetermineDirection {
        @Test
        void givenAboveBuyThreshold_whenDetermine_thenBuy() {
            assertEquals(SignalResult.DIR_BUY, generator.determineDirection(85.0));
        }

        @Test
        void givenBelowSellThreshold_whenDetermine_thenSell() {
            assertEquals(SignalResult.DIR_SELL, generator.determineDirection(15.0));
        }

        @Test
        void givenBetweenThresholds_whenDetermine_thenNull() {
            assertNull(generator.determineDirection(50.0));
        }

        @Test
        void givenExactlyBuyThreshold_whenDetermine_thenBuy() {
            assertEquals(SignalResult.DIR_BUY, generator.determineDirection(80.0));
        }

        @Test
        void givenExactlySellThreshold_whenDetermine_thenSell() {
            assertEquals(SignalResult.DIR_SELL, generator.determineDirection(20.0));
        }
    }

    @Nested
    class EstimateExpectedMove {
        @Test
        void givenHighPercentile_whenEstimate_thenPositiveMove() {
            var history = generateHistory(50, 0.0, 0.1);
            double move = generator.estimateExpectedMove(history, 90.0);
            assertTrue(move > 0);
        }

        @Test
        void givenMidPercentile_whenEstimate_thenSmallMove() {
            var history = generateHistory(50, 0.0, 0.1);
            double move = generator.estimateExpectedMove(history, 50.0);
            assertEquals(0.0, move, 1e-9);
        }
    }
}
