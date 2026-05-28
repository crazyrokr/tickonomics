package com.tickonomics.computation.pairs;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PairsTradingEngineTest {

    @InjectMocks
    private PairsTradingEngine engine;

    private List<Double> generateSeries(int count, double base, double noise) {
        List<Double> series = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            series.add(base + i * 0.1 + (i % 2 == 0 ? noise : -noise));
        }
        return series;
    }

    @Nested
    class Evaluate {
        @Test
        void givenSpreadAboveEntry_whenEvaluate_thenShortALongB() {
            /* Given series A deviates positively vs B beyond entry threshold */
            /* When evaluating pairs signal */
            /* Then signal is OPEN_SHORT_A_LONG_B */
            List<Double> seriesA = generateSeries(20, 100.0, 0.0);
            List<Double> seriesB = generateSeries(20, 100.0, 0.0);
            seriesA.set(19, 200.0);

            var result = engine.evaluate("AAA", "BBB", seriesA, seriesB, 1.5, 0.5);

            assertEquals("AAA", result.symbolA());
            assertEquals("BBB", result.symbolB());
            assertEquals(PairsTradingEngine.PairsSignalType.OPEN_SHORT_A_LONG_B, result.signal());
            assertTrue(result.spread() > 1.5);
        }

        @Test
        void givenSpreadBelowNegEntry_whenEvaluate_thenLongAShortB() {
            /* Given series A deviates negatively vs B beyond entry threshold */
            /* When evaluating pairs signal */
            /* Then signal is OPEN_LONG_A_SHORT_B */
            List<Double> seriesA = generateSeries(20, 100.0, 0.0);
            List<Double> seriesB = generateSeries(20, 100.0, 0.0);
            seriesB.set(19, 200.0);

            var result = engine.evaluate("AAA", "BBB", seriesA, seriesB, 1.5, 0.5);

            assertEquals(PairsTradingEngine.PairsSignalType.OPEN_LONG_A_SHORT_B, result.signal());
            assertTrue(result.spread() < -1.5);
        }

        @Test
        void givenSpreadWithinExit_whenEvaluate_thenClose() {
            /* Given spread within exit threshold */
            /* When evaluating pairs signal */
            /* Then signal is CLOSE */
            List<Double> seriesA = List.of(100.0, 101.0, 102.0, 103.0, 104.0);
            List<Double> seriesB = List.of(100.0, 101.0, 102.0, 103.0, 104.0);

            var result = engine.evaluate("AAA", "BBB", seriesA, seriesB, 2.0, 0.5);

            assertEquals(PairsTradingEngine.PairsSignalType.CLOSE, result.signal());
            assertTrue(result.distance() < 0.5);
        }

        @Test
        void givenSpreadBetweenExitAndEntry_whenEvaluate_thenHold() {
            /* Given spread between exit and entry thresholds */
            /* When evaluating pairs signal */
            /* Then signal is HOLD */
            List<Double> seriesA = generateSeries(20, 100.0, 0.0);
            List<Double> seriesB = generateSeries(20, 100.0, 0.0);
            seriesA.set(19, 110.0);

            var result = engine.evaluate("AAA", "BBB", seriesA, seriesB, 3.0, 0.2);

            assertEquals(PairsTradingEngine.PairsSignalType.HOLD, result.signal());
        }

        @Test
        void givenNullSeriesA_whenEvaluate_thenHold() {
            /* Given null series A */
            /* When evaluating pairs signal */
            /* Then signal is HOLD */
            var result = engine.evaluate("A", "B", null, List.of(1.0), 2.0, 0.5);

            assertEquals(PairsTradingEngine.PairsSignalType.HOLD, result.signal());
        }

        @Test
        void givenNullSeriesB_whenEvaluate_thenHold() {
            /* Given null series B */
            /* When evaluating pairs signal */
            /* Then signal is HOLD */
            var result = engine.evaluate("A", "B", List.of(1.0), null, 2.0, 0.5);

            assertEquals(PairsTradingEngine.PairsSignalType.HOLD, result.signal());
        }

        @Test
        void givenMismatchedSizes_whenEvaluate_thenHold() {
            /* Given series with different sizes */
            /* When evaluating pairs signal */
            /* Then signal is HOLD */
            var result = engine.evaluate("A", "B",
                    List.of(1.0, 2.0), List.of(1.0), 2.0, 0.5);

            assertEquals(PairsTradingEngine.PairsSignalType.HOLD, result.signal());
        }

        @Test
        void givenEmptySeries_whenEvaluate_thenHold() {
            /* Given empty series */
            /* When evaluating pairs signal */
            /* Then signal is HOLD */
            var result = engine.evaluate("A", "B", List.of(), List.of(), 2.0, 0.5);

            assertEquals(PairsTradingEngine.PairsSignalType.HOLD, result.signal());
        }
    }

    @Nested
    class DetermineSignal {
        @Test
        void givenSpreadAboveEntry_whenDetermine_thenShortA() {
            /* Given spread > entry threshold */
            /* When determining signal */
            /* Then result is OPEN_SHORT_A_LONG_B */
            assertEquals(PairsTradingEngine.PairsSignalType.OPEN_SHORT_A_LONG_B,
                    engine.determineSignal(2.0, 2.0, 1.5, 0.5));
        }

        @Test
        void givenSpreadBelowNegEntry_whenDetermine_thenLongA() {
            /* Given spread < -entry threshold */
            /* When determining signal */
            /* Then result is OPEN_LONG_A_SHORT_B */
            assertEquals(PairsTradingEngine.PairsSignalType.OPEN_LONG_A_SHORT_B,
                    engine.determineSignal(-2.0, 2.0, 1.5, 0.5));
        }

        @Test
        void givenDistanceBelowExit_whenDetermine_thenClose() {
            /* Given distance < exit threshold */
            /* When determining signal */
            /* Then result is CLOSE */
            assertEquals(PairsTradingEngine.PairsSignalType.CLOSE,
                    engine.determineSignal(0.3, 0.3, 1.5, 0.5));
        }

        @Test
        void givenModerateDistance_whenDetermine_thenHold() {
            /* Given distance between exit and entry */
            /* When determining signal */
            /* Then result is HOLD */
            assertEquals(PairsTradingEngine.PairsSignalType.HOLD,
                    engine.determineSignal(1.0, 1.0, 1.5, 0.5));
        }
    }

    @Nested
    class MeanAndStdDev {
        @Test
        void givenEmptyList_whenMean_thenZero() {
            /* Given empty list */
            /* When computing mean */
            /* Then result is zero */
            assertEquals(0.0, engine.mean(List.of()), 0.01);
        }

        @Test
        void givenUniformValues_whenStdDev_thenZero() {
            /* Given all same values */
            /* When computing standard deviation */
            /* Then result is zero */
            assertEquals(0.0, engine.stdDev(List.of(5.0, 5.0, 5.0), 5.0), 0.01);
        }
    }
}
