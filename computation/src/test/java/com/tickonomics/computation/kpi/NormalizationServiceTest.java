package com.tickonomics.computation.kpi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NormalizationServiceTest {

    private NormalizationService service;

    @BeforeEach
    void setUp() {
        service = new NormalizationService();
    }

    @Nested
    class Normalize {
        @Test
        void givenValidHistory_whenNormalize_thenReturnsZscore() {
            double[] history = {1.0, 2.0, 3.0, 4.0, 5.0};
            var result = service.normalize(history, "Z_RRP", 4.5, LookbackTier.FLOW);
            assertTrue(result.valid());
            assertEquals("Z_RRP", result.component());
            assertEquals(4.5, result.rawValue());
            assertEquals(LookbackTier.FLOW.days(), result.lookbackDays());
        }

        @Test
        void givenCurrentValueAtMean_whenNormalize_thenZscoreZero() {
            double[] history = {2.0, 4.0, 2.0, 4.0};
            double mean = service.computeMean(history);
            var result = service.normalize(history, "Z_SPREAD", mean, LookbackTier.VOLATILITY);
            assertEquals(0.0, result.zScore(), 1e-9);
        }

        @Test
        void givenNullHistory_whenNormalize_thenInvalid() {
            var result = service.normalize(null, "Z_RRP", 4.5, LookbackTier.MACRO);
            assertFalse(result.valid());
            assertTrue(Double.isNaN(result.zScore()));
        }

        @Test
        void givenSingleValueHistory_whenNormalize_thenInvalid() {
            var result = service.normalize(new double[]{4.5}, "Z_RRP", 4.5, LookbackTier.FLOW);
            assertFalse(result.valid());
        }

        @Test
        void givenConstantHistory_whenNormalize_thenInvalid() {
            var result = service.normalize(new double[]{3.0, 3.0, 3.0, 3.0}, "Z_VOL", 3.0, LookbackTier.VOLATILITY);
            assertFalse(result.valid());
        }
    }

    @Nested
    class NormalizeAllTiers {
        @Test
        void givenSufficientHistory_whenNormalizeAll_thenAllTiersPresent() {
            double[] history = new double[300];
            for (int i = 0; i < history.length; i++) {
                history[i] = Math.sin(i * 0.1) + 4.0;
            }
            Map<LookbackTier, ZscoreResult> results = service.normalizeAllTiers(history, "Z_RRP", 4.5);
            assertEquals(3, results.size());
            assertTrue(results.containsKey(LookbackTier.MACRO));
            assertTrue(results.containsKey(LookbackTier.FLOW));
            assertTrue(results.containsKey(LookbackTier.VOLATILITY));
        }
    }

    @Nested
    class ComputeAllZscores {
        @Test
        void givenValidData_whenComputeAll_thenCorrectLength() {
            double[] values = {1.0, 2.0, 3.0, 4.0, 5.0};
            double[] zscores = service.computeAllZscores(values);
            assertEquals(5, zscores.length);
        }

        @Test
        void givenConstantData_whenComputeAll_thenAllNaN() {
            double[] values = {3.0, 3.0, 3.0};
            double[] zscores = service.computeAllZscores(values);
            for (double z : zscores) {
                assertTrue(Double.isNaN(z));
            }
        }

        @Test
        void givenNullData_whenComputeAll_thenEmpty() {
            assertEquals(0, service.computeAllZscores(null).length);
        }
    }

    @Nested
    class ComputePercentileRank {
        @Test
        void givenKnownValues_whenCompute_thenCorrectRank() {
            double[] values = {1.0, 2.0, 3.0, 4.0, 5.0};
            double rank = service.computePercentileRank(values, 3.0);
            assertEquals(40.0, rank, 1e-9);
        }

        @Test
        void givenHighestValue_whenCompute_then100() {
            double[] values = {1.0, 2.0, 3.0};
            double rank = service.computePercentileRank(values, 3.0);
            assertEquals(66.666, rank, 0.1);
        }

        @Test
        void givenLowestValue_whenCompute_thenZero() {
            double[] values = {1.0, 2.0, 3.0};
            double rank = service.computePercentileRank(values, 0.5);
            assertEquals(0.0, rank, 1e-9);
        }

        @Test
        void givenEmptyArray_whenCompute_thenNaN() {
            assertTrue(Double.isNaN(service.computePercentileRank(new double[0], 1.0)));
        }
    }
}
