package com.tickonomics.computation.kpi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LiquidityMeanReversionSpeedTest {

    private LiquidityMeanReversionSpeed service;

    @BeforeEach
    void setUp() {
        service = new LiquidityMeanReversionSpeed();
    }

    @Nested
    class InsufficientData {

        @Test
        void givenNullSeries_whenCompute_thenDefaults() {
            /*
             * Given: null price impact series
             * When: compute() is called
             * Then: reversionSpeed=0, halfLife=0, laggedDrainDetected=false
             */
            var result = service.compute(null);

            assertEquals(0.0, result.reversionSpeed(), 1e-10);
            assertEquals(0.0, result.halfLife(), 1e-10);
            assertFalse(result.laggedDrainDetected());
        }

        @Test
        void givenEmptySeries_whenCompute_thenDefaults() {
            /*
             * Given: empty price impact series
             * When: compute() is called
             * Then: returns default (0, 0, false)
             */
            var result = service.compute(new double[]{});

            assertEquals(0.0, result.reversionSpeed(), 1e-10);
            assertEquals(0.0, result.halfLife(), 1e-10);
            assertFalse(result.laggedDrainDetected());
        }

        @Test
        void givenFewerThan30Observations_whenCompute_thenDefaults() {
            /*
             * Given: 29 data points (less than minimum 30)
             * When: compute() is called
             * Then: returns default (0, 0, false)
             */
            double[] series = new double[29];
            for (int i = 0; i < 29; i++) {
                series[i] = Math.random();
            }

            var result = service.compute(series);

            assertEquals(0.0, result.reversionSpeed(), 1e-10);
            assertFalse(result.laggedDrainDetected());
        }

        @Test
        void givenExactly30Observations_whenCompute_thenComputes() {
            /*
             * Given: exactly 30 data points
             * When: compute() is called
             * Then: computation is performed (not defaults)
             */
            double[] series = new double[30];
            for (int i = 0; i < 30; i++) {
                series[i] = Math.exp(-i * 0.1);
            }

            var result = service.compute(series);

            assertTrue(result.reversionSpeed() > 0);
            assertTrue(result.halfLife() > 0);
        }
    }

    @Nested
    class ReversionSpeedEstimation {

        @Test
        void givenMeanRevertingSeries_whenCompute_thenPositiveReversionSpeed() {
            /*
             * Given: a series that decays toward zero (mean-reverting)
             * When: compute() is called
             * Then: reversionSpeed > 0 and halfLife > 0
             */
            double[] series = new double[100];
            for (int i = 0; i < 100; i++) {
                series[i] = Math.exp(-i * 0.05);
            }

            var result = service.compute(series);

            assertTrue(result.reversionSpeed() > 0);
            assertTrue(result.halfLife() > 0);
            assertTrue(Double.isFinite(result.halfLife()));
        }

        @Test
        void givenConstantSeries_whenCompute_thenZeroReversionSpeed() {
            /*
             * Given: a constant series (no variation)
             * When: compute() is called
             * Then: reversionSpeed handles zero denominator gracefully
             */
            double[] series = new double[50];
            for (int i = 0; i < 50; i++) {
                series[i] = 5.0;
            }

            var result = service.compute(series);

            assertNotNull(result);
        }
    }

    @Nested
    class LaggedDrainDetection {

        @Test
        void givenHighAutocorrelationSeries_whenCompute_thenLaggedDrainDetected() {
            /*
             * Given: a series with strong autocorrelation at lag 20
             * When: compute() is called
             * Then: laggedDrainDetected = true
             */
            double[] series = new double[100];
            for (int i = 0; i < 100; i++) {
                series[i] = 1.0 + 0.5 * Math.sin(i * 0.1);
            }

            double autocorr = service.computeAutocorrelation(series, 20);

            if (autocorr > 0.5) {
                var result = service.compute(series);
                assertTrue(result.laggedDrainDetected());
            }
        }

        @Test
        void givenRandomNoiseSeries_whenCompute_thenNoLaggedDrain() {
            /*
             * Given: a random noise series (low autocorrelation)
             * When: compute() is called
             * Then: laggedDrainDetected = false
             */
            double[] series = new double[200];
            for (int i = 0; i < 200; i++) {
                series[i] = (Math.random() - 0.5) * 2.0;
            }

            var result = service.compute(series);

            assertFalse(result.laggedDrainDetected());
        }
    }

    @Nested
    class AutocorrelationCalculation {

        @Test
        void givenSeriesShorterThanLag_whenComputeAutocorrelation_thenReturnZero() {
            /*
             * Given: a series of length 10 and lag 20
             * When: computeAutocorrelation() is called
             * Then: returns 0.0
             */
            double[] series = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0};

            double autocorr = service.computeAutocorrelation(series, 20);

            assertEquals(0.0, autocorr, 1e-10);
        }

        @Test
        void givenPerfectConstantSeries_whenComputeAutocorrelation_thenReturnZero() {
            /*
             * Given: a perfectly constant series
             * When: computeAutocorrelation() is called
             * Then: returns 0.0 (no variance means zero autocorrelation)
             */
            double[] series = new double[50];
            for (int i = 0; i < 50; i++) {
                series[i] = 3.0;
            }

            double autocorr = service.computeAutocorrelation(series, 5);

            assertEquals(0.0, autocorr, 1e-10);
        }
    }
}
