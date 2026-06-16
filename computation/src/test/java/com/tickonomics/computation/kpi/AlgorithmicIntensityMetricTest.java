package com.tickonomics.computation.kpi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class AlgorithmicIntensityMetricTest {

    private AlgorithmicIntensityMetric metric;

    @BeforeEach
    void setUp() {
        metric = new AlgorithmicIntensityMetric();
    }

    @Nested
    class ComputeAtProxy {

        @Test
        void givenValidInputs_whenCompute_thenAtProxyIsAbsoluteDollarVolumeDividedByMessageCount() {
            /*
             * Given: dollarVolume=1000.0 and messageCount=500
             * When: compute() is called
             * Then: atProxy = 1000.0 / 500 = 2.0
             */
            double[] history = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0};

            var result = metric.compute(1000.0, 500, history);

            assertEquals(2.0, result.atProxy(), 1e-10);
        }

        @Test
        void givenNegativeDollarVolume_whenCompute_thenAtProxyIsPositive() {
            /*
             * Given: dollarVolume=-1000.0 and messageCount=500
             * When: compute() is called
             * Then: atProxy = abs(-1000.0) / 500 = 2.0
             */
            double[] history = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0};

            var result = metric.compute(-1000.0, 500, history);

            assertEquals(2.0, result.atProxy(), 1e-10);
        }

        @Test
        void givenZeroMessageCount_whenCompute_thenAtProxyUsesMaxOne() {
            /*
             * Given: messageCount=0
             * When: compute() is called
             * Then: atProxy = abs(1000.0) / max(1, 0) = 1000.0
             */
            double[] history = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0};

            var result = metric.compute(1000.0, 0, history);

            assertEquals(1000.0, result.atProxy(), 1e-10);
        }
    }

    @Nested
    class QuintileRanking {

        @Test
        void givenNoHistory_whenCompute_thenDefaultQuintile3() {
            /*
             * Given: null historical data
             * When: compute() is called
             * Then: quintile defaults to 3
             */
            var result = metric.compute(100.0, 10, null);

            assertEquals(3, result.quintile());
            assertEquals("MEDIUM", result.intensityLevel());
        }

        @Test
        void givenFewerThanFiveHistoryPoints_whenCompute_thenDefaultQuintile3() {
            /*
             * Given: only 3 historical data points (less than 5)
             * When: compute() is called
             * Then: quintile defaults to 3
             */
            double[] history = {1.0, 2.0, 3.0};

            var result = metric.compute(100.0, 10, history);

            assertEquals(3, result.quintile());
        }

        @Test
        void givenVeryLowAtProxy_whenCompute_thenQuintile1() {
            /*
             * Given: atProxy well below the first quintile boundary
             * When: compute() is called
             * Then: quintile = 1 (VERY_LOW)
             */
            double[] history = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0};

            var result = metric.compute(5.0, 1, history);

            assertEquals(1, result.quintile());
            assertEquals("VERY_LOW", result.intensityLevel());
        }

        @Test
        void givenVeryHighAtProxy_whenCompute_thenQuintile5() {
            /*
             * Given: atProxy well above the fourth quintile boundary
             * When: compute() is called
             * Then: quintile = 5 (VERY_HIGH)
             */
            double[] history = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0};

            var result = metric.compute(150.0, 1, history);

            assertEquals(5, result.quintile());
            assertEquals("VERY_HIGH", result.intensityLevel());
        }

        @Test
        void givenMidRangeAtProxy_whenCompute_thenQuintile3() {
            /*
             * Given: atProxy around the middle of the distribution
             * When: compute() is called
             * Then: quintile = 3 (MEDIUM)
             */
            double[] history = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0};

            var result = metric.compute(50.0, 1, history);

            assertEquals(3, result.quintile());
            assertEquals("MEDIUM", result.intensityLevel());
        }
    }

    @Nested
    class IntensityLevelLabels {

        @Test
        void givenEachQuintile_whenCompute_thenCorrectLabelAssigned() {
            /*
             * Given: history producing clear quintile boundaries
             * When: computing for each quintile range
             * Then: correct labels are VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
             */
            double[] history = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0};

            String[] expectedLabels = {"VERY_LOW", "LOW", "MEDIUM", "HIGH", "VERY_HIGH"};
            double[] testValues = {5.0, 25.0, 50.0, 75.0, 150.0};

            for (int i = 0; i < 5; i++) {
                var result = metric.compute(testValues[i], 1, history);
                assertEquals(expectedLabels[i], result.intensityLevel(),
                        "Mismatch at quintile " + (i + 1));
            }
        }
    }
}
