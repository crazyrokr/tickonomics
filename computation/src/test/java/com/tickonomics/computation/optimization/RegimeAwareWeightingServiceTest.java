package com.tickonomics.computation.optimization;

import com.tickonomics.computation.ili.WeightedWeightStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RegimeAwareWeightingServiceTest {

    private static final double EPSILON = 1e-9;
    private static final double[] BASE_WEIGHTS = {0.4, 0.35, 0.25};

    @Mock
    private WeightedWeightStore weightStore;

    private RegimeAwareWeightingService service;

    @BeforeEach
    void setUp() {
        lenient().when(weightStore.getBaseWeights()).thenReturn(BASE_WEIGHTS.clone());
        service = new RegimeAwareWeightingService(weightStore);
    }

    @Nested
    @DisplayName("adjustWeights - HIGH_VOL regime")
    class AdjustWeightsHighVol {

        @Test
        @DisplayName("given HIGH_VOL regime when adjustWeights called then spread and vol boosted by 40% and RRP reduced by 40%")
        /**
         * Given a HIGH_VOL regime with uniform weights.
         * When adjustWeights is called.
         * Then spread and vol weights are boosted by 40%, RRP reduced by 40%, and result is normalized to sum to 1.0.
         */
        void givenHighVolRegime_whenAdjustWeights_thenSpreadVolBoostedRrpReduced() {
            double[] weights = {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0};

            double[] result = service.adjustWeights("HIGH_VOL", weights);

            assertNotNull(result);
            double sum = 0;
            for (double w : result) {
                sum += w;
            }
            assertEquals(1.0, sum, EPSILON);

            double boostedSpread = (1.0 / 3.0) * 1.4;
            double boostedVol = (1.0 / 3.0) * 1.4;
            double reducedRrp = (1.0 / 3.0) * 0.6;
            double total = reducedRrp + boostedSpread + boostedVol;

            assertEquals(reducedRrp / total, result[0], EPSILON);
            assertEquals(boostedSpread / total, result[1], EPSILON);
            assertEquals(boostedVol / total, result[2], EPSILON);
        }

        @Test
        @DisplayName("given HIGH_VOL regime with default weights when adjustWeights called then weights are shifted correctly")
        /**
         * Given a HIGH_VOL regime with the default 3-component weight vector.
         * When adjustWeights is called.
         * Then the RRP weight is reduced and spread/vol weights are increased relative to input.
         */
        void givenHighVolWithDefaultWeights_whenAdjustWeights_thenWeightsShiftedCorrectly() {
            double[] weights = {0.4, 0.35, 0.25};

            double[] result = service.adjustWeights("HIGH_VOL", weights);

            assertTrue(result[1] > weights[1], "Spread weight should increase under HIGH_VOL");
            assertTrue(result[2] > weights[2], "Vol weight should increase under HIGH_VOL");
            assertTrue(result[0] < weights[0], "RRP weight should decrease under HIGH_VOL");
        }
    }

    @Nested
    @DisplayName("adjustWeights - UNSTABLE regime")
    class AdjustWeightsUnstable {

        @Test
        @DisplayName("given UNSTABLE regime when adjustWeights called then spread and vol boosted by 20%")
        /**
         * Given an UNSTABLE regime with uniform weights.
         * When adjustWeights is called.
         * Then spread and vol weights are boosted by 20%, RRP unchanged, and result is normalized to sum to 1.0.
         */
        void givenUnstableRegime_whenAdjustWeights_thenSpreadVolBoostedBy20() {
            double[] weights = {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0};

            double[] result = service.adjustWeights("UNSTABLE", weights);

            assertNotNull(result);
            double sum = 0;
            for (double w : result) {
                sum += w;
            }
            assertEquals(1.0, sum, EPSILON);

            double unadjustedRrp = 1.0 / 3.0;
            double boostedSpread = (1.0 / 3.0) * 1.2;
            double boostedVol = (1.0 / 3.0) * 1.2;
            double total = unadjustedRrp + boostedSpread + boostedVol;

            assertEquals(unadjustedRrp / total, result[0], EPSILON);
            assertEquals(boostedSpread / total, result[1], EPSILON);
            assertEquals(boostedVol / total, result[2], EPSILON);
        }

        @Test
        @DisplayName("given UNSTABLE regime with default weights when adjustWeights called then spread and vol increase")
        /**
         * Given an UNSTABLE regime with default weights.
         * When adjustWeights is called.
         * Then spread and vol weights increase and RRP weight decreases due to normalization.
         */
        void givenUnstableWithDefaultWeights_whenAdjustWeights_thenSpreadVolIncrease() {
            double[] weights = {0.4, 0.35, 0.25};

            double[] result = service.adjustWeights("UNSTABLE", weights);

            assertTrue(result[1] > weights[1], "Spread weight should increase under UNSTABLE");
            assertTrue(result[2] > weights[2], "Vol weight should increase under UNSTABLE");
        }
    }

    @Nested
    @DisplayName("adjustWeights - METASTABLE and LOW_VOL regimes")
    class AdjustWeightsStableRegimes {

        @Test
        @DisplayName("given METASTABLE regime when adjustWeights called then weights unchanged and normalized")
        /**
         * Given a METASTABLE regime with valid weights.
         * When adjustWeights is called.
         * Then weights are returned unchanged.
         */
        void givenMetastableRegime_whenAdjustWeights_thenWeightsUnchanged() {
            double[] weights = {0.5, 0.3, 0.2};

            double[] result = service.adjustWeights("METASTABLE", weights);

            assertArrayEquals(weights, result, EPSILON);
        }

        @Test
        @DisplayName("given LOW_VOL regime when adjustWeights called then weights unchanged and normalized")
        /**
         * Given a LOW_VOL regime with valid weights.
         * When adjustWeights is called.
         * Then weights are returned unchanged.
         */
        void givenLowVolRegime_whenAdjustWeights_thenWeightsUnchanged() {
            double[] weights = {0.5, 0.3, 0.2};

            double[] result = service.adjustWeights("LOW_VOL", weights);

            assertArrayEquals(weights, result, EPSILON);
        }

        @Test
        @DisplayName("given unknown regime type when adjustWeights called then weights unchanged")
        /**
         * Given an unrecognized regime type.
         * When adjustWeights is called.
         * Then weights are returned unchanged, acting as a safe default.
         */
        void givenUnknownRegimeType_whenAdjustWeights_thenWeightsUnchanged() {
            double[] weights = {0.5, 0.3, 0.2};

            double[] result = service.adjustWeights("UNKNOWN", weights);

            assertArrayEquals(weights, result, EPSILON);
        }

        @Test
        @DisplayName("given null regime type when adjustWeights called then weights unchanged")
        /**
         * Given a null regime type.
         * When adjustWeights is called.
         * Then weights are returned unchanged.
         */
        void givenNullRegimeType_whenAdjustWeights_thenWeightsUnchanged() {
            double[] weights = {0.5, 0.3, 0.2};

            double[] result = service.adjustWeights(null, weights);

            assertArrayEquals(weights, result, EPSILON);
        }
    }

    @Nested
    @DisplayName("adjustWeights - normalization")
    class AdjustWeightsNormalization {

        @Test
        @DisplayName("given unnormalized input weights when adjustWeights called then result sums to 1.0")
        /**
         * Given weights that do not sum to 1.0 for a METASTABLE regime.
         * When adjustWeights is called.
         * Then the result is normalized to sum to 1.0.
         */
        void givenUnnormalizedInput_whenAdjustWeights_thenResultSumsToOne() {
            double[] weights = {2.0, 3.0, 5.0};

            double[] result = service.adjustWeights("METASTABLE", weights);

            double sum = 0;
            for (double w : result) {
                sum += w;
            }
            assertEquals(1.0, sum, EPSILON);
            assertEquals(0.2, result[0], EPSILON);
            assertEquals(0.3, result[1], EPSILON);
            assertEquals(0.5, result[2], EPSILON);
        }

        @Test
        @DisplayName("given HIGH_VOL with unnormalized weights when adjustWeights called then result sums to 1.0")
        /**
         * Given unnormalized weights under HIGH_VOL regime.
         * When adjustWeights is called.
         * Then the result is normalized to sum to 1.0 with regime adjustments applied.
         */
        void givenHighVolUnnormalized_whenAdjustWeights_thenResultSumsToOne() {
            double[] weights = {2.0, 3.0, 5.0};

            double[] result = service.adjustWeights("HIGH_VOL", weights);

            double sum = 0;
            for (double w : result) {
                sum += w;
            }
            assertEquals(1.0, sum, EPSILON);
        }

        @Test
        @DisplayName("given already normalized weights when adjustWeights called for METASTABLE then exact same weights returned")
        /**
         * Given already normalized weights for a METASTABLE regime.
         * When adjustWeights is called.
         * Then the exact same values are returned without floating point drift.
         */
        void givenAlreadyNormalized_whenMetastable_thenExactSameWeights() {
            double[] weights = {0.4, 0.35, 0.25};

            double[] result = service.adjustWeights("METASTABLE", weights);

            assertArrayEquals(weights, result, EPSILON);
        }
    }

    @Nested
    @DisplayName("adjustWeights - invalid and edge case inputs")
    class AdjustWeightsInvalidInputs {

        @Test
        @DisplayName("given null weights when adjustWeights called then returns base weights")
        /**
         * Given null current weights.
         * When adjustWeights is called.
         * Then the base weights from the weight store are returned.
         */
        void givenNullWeights_whenAdjustWeights_thenReturnsBaseWeights() {
            double[] result = service.adjustWeights("HIGH_VOL", null);

            assertNotNull(result);
            assertArrayEquals(BASE_WEIGHTS, result, EPSILON);
        }

        @Test
        @DisplayName("given empty weights when adjustWeights called then returns base weights")
        /**
         * Given an empty weight array.
         * When adjustWeights is called.
         * Then the base weights from the weight store are returned.
         */
        void givenEmptyWeights_whenAdjustWeights_thenReturnsBaseWeights() {
            double[] result = service.adjustWeights("HIGH_VOL", new double[0]);

            assertNotNull(result);
            assertArrayEquals(BASE_WEIGHTS, result, EPSILON);
        }

        @Test
        @DisplayName("given single-element weights when adjustWeights called for METASTABLE then normalized single weight")
        /**
         * Given a single-element weight array for a METASTABLE regime.
         * When adjustWeights is called.
         * Then the single weight is normalized to 1.0.
         */
        void givenSingleElementWeights_whenMetastable_thenNormalizedToOne() {
            double[] weights = {3.0};

            double[] result = service.adjustWeights("METASTABLE", weights);

            assertEquals(1, result.length);
            assertEquals(1.0, result[0], EPSILON);
        }

        @Test
        @DisplayName("given single-element weights when adjustWeights called for HIGH_VOL then normalized with RRP reduction")
        /**
         * Given a single-element weight array under HIGH_VOL regime.
         * When adjustWeights is called.
         * Then the RRP weight is reduced by 40% and normalized to 1.0.
         */
        void givenSingleElementWeights_whenHighVol_thenReducedAndNormalized() {
            double[] weights = {1.0};

            double[] result = service.adjustWeights("HIGH_VOL", weights);

            assertEquals(1, result.length);
            assertEquals(1.0, result[0], EPSILON);
        }

        @Test
        @DisplayName("given all-zero weights when adjustWeights called then returns base weights via fallback")
        /**
         * Given all-zero weights that would produce a zero sum after normalization.
         * When adjustWeights is called for METASTABLE.
         * Then the base weights are returned as fallback.
         */
        void givenAllZeroWeights_whenAdjustWeights_thenReturnsBaseWeights() {
            double[] weights = {0.0, 0.0, 0.0};

            double[] result = service.adjustWeights("METASTABLE", weights);

            assertArrayEquals(BASE_WEIGHTS, result, EPSILON);
        }
    }

    @Nested
    @DisplayName("adjustWeights - asymmetric boost factors")
    class AdjustWeightsAsymmetricBoost {

        @Test
        @DisplayName("given HIGH_VOL when adjustWeights called then HIGH_VOL boost is double UNSTABLE boost")
        /**
         * Given the same input weights for HIGH_VOL and UNSTABLE regimes.
         * When adjustWeights is called for each.
         * Then HIGH_VOL produces a larger shift in spread/vol weights than UNSTABLE.
         */
        void givenHighVolVsUnstable_whenAdjustWeights_thenHighVolShiftsMore() {
            double[] weights = {0.4, 0.35, 0.25};

            double[] highVolResult = service.adjustWeights("HIGH_VOL", weights.clone());
            double[] unstableResult = service.adjustWeights("UNSTABLE", weights.clone());

            double highVolSpreadShift = Math.abs(highVolResult[1] - weights[1]);
            double unstableSpreadShift = Math.abs(unstableResult[1] - weights[1]);

            assertTrue(highVolSpreadShift > unstableSpreadShift,
                    "HIGH_VOL should produce a larger spread shift than UNSTABLE");
        }

        @Test
        @DisplayName("given UNSTABLE regime when adjustWeights called then RRP weight stays same before normalization")
        /**
         * Given an UNSTABLE regime.
         * When adjustWeights is called.
         * Then the RRP weight is not directly reduced (only affected by normalization).
         */
        void givenUnstableRegime_whenAdjustWeights_thenRrpNotDirectlyReduced() {
            double[] weights = {0.6, 0.2, 0.2};

            double[] result = service.adjustWeights("UNSTABLE", weights);

            double unnormalizedRrp = 0.6;
            double unnormalizedSpread = 0.2 * 1.2;
            double unnormalizedVol = 0.2 * 1.2;
            double total = unnormalizedRrp + unnormalizedSpread + unnormalizedVol;
            double expectedRrp = unnormalizedRrp / total;

            assertEquals(expectedRrp, result[0], EPSILON);
        }
    }
}
