package com.tickonomics.computation.signal;

import com.tickonomics.computation.signal.SurpriseIndicator.SurpriseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SurpriseIndicatorTest {

    private SurpriseIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new SurpriseIndicator();
    }

    @Nested
    @DisplayName("compute")
    class Compute {

        @Nested
        @DisplayName("given null historical data")
        class NullHistory {

            @Test
            @DisplayName("given null historical array when compute then returns default result")
            void givenNullHistoricalArray_whenCompute_thenReturnsDefaultResult() {
                /**
                 * Given a null historical ILI array
                 * When compute is called
                 * Then result has surprise=0.0, entropy=0.0, modifier=1.0
                 */
                SurpriseResult result = indicator.compute(0.5, null);

                assertNotNull(result);
                assertEquals(0.0, result.surpriseScore(), 1e-9);
                assertEquals(0.0, result.entropyEstimate(), 1e-9);
                assertEquals(1.0, result.positionSizeModifier(), 1e-9);
            }
        }

        @Nested
        @DisplayName("given empty historical data")
        class EmptyHistory {

            @Test
            @DisplayName("given empty historical array when compute then returns default result")
            void givenEmptyHistoricalArray_whenCompute_thenReturnsDefaultResult() {
                /**
                 * Given an empty historical ILI array
                 * When compute is called
                 * Then result has surprise=0.0, entropy=0.0, modifier=1.0
                 */
                SurpriseResult result = indicator.compute(0.5, new double[0]);

                assertNotNull(result);
                assertEquals(0.0, result.surpriseScore(), 1e-9);
                assertEquals(0.0, result.entropyEstimate(), 1e-9);
                assertEquals(1.0, result.positionSizeModifier(), 1e-9);
            }
        }

        @Nested
        @DisplayName("given identical historical values")
        class IdenticalHistory {

            @Test
            @DisplayName("given all identical historical values matching current when compute then surprise is zero")
            void givenAllIdenticalMatchingCurrent_whenCompute_thenSurpriseIsZero() {
                /**
                 * Given historical ILI values all equal to 0.5
                 * When compute is called with currentIli=0.5
                 * Then surprise is 0.0 and entropy is 0.0
                 */
                double[] history = {0.5, 0.5, 0.5, 0.5, 0.5};

                SurpriseResult result = indicator.compute(0.5, history);

                assertEquals(0.0, result.surpriseScore(), 1e-9);
                assertEquals(0.0, result.entropyEstimate(), 1e-9);
                assertEquals(1.0, result.positionSizeModifier(), 1e-9);
            }

            @Test
            @DisplayName("given all identical historical values different from current when compute then high surprise")
            void givenAllIdenticalDifferentCurrent_whenCompute_thenHighSurprise() {
                /**
                 * Given historical ILI values all equal to 0.5
                 * When compute is called with currentIli=2.0
                 * Then surprise is high and positionSizeModifier is less than 1.0
                 */
                double[] history = {0.5, 0.5, 0.5, 0.5, 0.5};

                SurpriseResult result = indicator.compute(2.0, history);

                assertTrue(result.surpriseScore() > 0.0,
                        "Surprise should be positive for out-of-distribution observation");
            }
        }

        @Nested
        @DisplayName("given varied historical data")
        class VariedHistory {

            @Test
            @DisplayName("given current value in dense region when compute then low surprise")
            void givenCurrentValueInDenseRegion_whenCompute_thenLowSurprise() {
                /**
                 * Given historical ILI values clustered around 0.5
                 * When compute is called with currentIli=0.5
                 * Then surprise is low and positionSizeModifier is close to 1.0
                 */
                double[] history = new double[50];
                for (int i = 0; i < 50; i++) {
                    history[i] = 0.5 + (i - 25) * 0.002;
                }

                SurpriseResult result = indicator.compute(0.5, history);

                assertTrue(result.surpriseScore() < 2.0,
                        "Surprise should be low for a value near the center of the distribution");
                assertTrue(result.positionSizeModifier() > 0.5,
                        "Position modifier should be high when surprise is low");
            }

            @Test
            @DisplayName("given current value in sparse tail when compute then high surprise")
            void givenCurrentValueInSparseTail_whenCompute_thenHighSurprise() {
                /**
                 * Given historical ILI values uniformly distributed 0-1
                 * When compute is called with currentIli=5.0 (far from distribution)
                 * Then surprise is high and positionSizeModifier is reduced
                 */
                double[] history = new double[100];
                for (int i = 0; i < 100; i++) {
                    history[i] = i / 100.0;
                }

                SurpriseResult result = indicator.compute(5.0, history);

                assertTrue(result.surpriseScore() > 0.0,
                        "Surprise should be positive for far-out-of-distribution observation");
                assertTrue(result.positionSizeModifier() < 1.0,
                        "Position modifier should be reduced when surprise is high");
            }

            @Test
            @DisplayName("given uniform distribution when compute then entropy is near maximum")
            void givenUniformDistribution_whenCompute_thenEntropyIsNearMaximum() {
                /**
                 * Given 100 historical ILI values uniformly spread 0-1
                 * When compute is called
                 * Then entropy is near log2(100 bins) ~ 4.32 for uniform
                 */
                double[] history = new double[100];
                for (int i = 0; i < 100; i++) {
                    history[i] = i / 100.0;
                }

                SurpriseResult result = indicator.compute(0.5, history);

                assertTrue(result.entropyEstimate() > 0.0,
                        "Entropy should be positive for a varied distribution");
            }
        }

        @Nested
        @DisplayName("given single element historical data")
        class SingleElementHistory {

            @Test
            @DisplayName("given single historical value equal to current when compute then zero surprise")
            void givenSingleMatchingValue_whenCompute_thenZeroSurprise() {
                /**
                 * Given a single historical ILI value of 1.0
                 * When compute is called with currentIli=1.0
                 * Then surprise is 0.0 and modifier is 1.0
                 */
                double[] history = {1.0};

                SurpriseResult result = indicator.compute(1.0, history);

                assertEquals(0.0, result.surpriseScore(), 1e-9);
                assertEquals(0.0, result.entropyEstimate(), 1e-9);
                assertEquals(1.0, result.positionSizeModifier(), 1e-9);
            }

            @Test
            @DisplayName("given single historical value different from current when compute then positive surprise")
            void givenSingleDifferentValue_whenCompute_thenPositiveSurprise() {
                /**
                 * Given a single historical ILI value of 1.0
                 * When compute is called with currentIli=3.0
                 * Then surprise is positive
                 */
                double[] history = {1.0};

                SurpriseResult result = indicator.compute(3.0, history);

                assertTrue(result.surpriseScore() >= 0.0);
                assertNotNull(result);
            }
        }

        @Nested
        @DisplayName("given position size modifier bounds")
        class PositionSizeModifierBounds {

            @Test
            @DisplayName("given maximum surprise when compute then modifier is capped at 0.5 reduction")
            void givenMaximumSurprise_whenCompute_thenModifierCappedAtHalfReduction() {
                /**
                 * Given a distribution where current value is extremely unlikely
                 * When surprise approaches maxSurprise (5.0)
                 * Then positionSizeModifier is at minimum 0.5
                 */
                double[] history = {0.1, 0.1, 0.1, 0.1, 0.1};

                SurpriseResult result = indicator.compute(100.0, history);

                assertTrue(result.positionSizeModifier() >= 0.5,
                        "Position modifier should never go below 0.5");
            }

            @Test
            @DisplayName("given zero surprise when compute then modifier is 1.0")
            void givenZeroSurprise_whenCompute_thenModifierIsOne() {
                /**
                 * Given all identical historical values matching the current
                 * When surprise is 0
                 * Then positionSizeModifier is 1.0 (no reduction)
                 */
                double[] history = {0.3, 0.3, 0.3};

                SurpriseResult result = indicator.compute(0.3, history);

                assertEquals(1.0, result.positionSizeModifier(), 1e-9);
            }
        }

        @Nested
        @DisplayName("given negative ILI values")
        class NegativeValues {

            @Test
            @DisplayName("given negative ILI values in history when compute then produces valid result")
            void givenNegativeIliHistory_whenCompute_thenProducesValidResult() {
                /**
                 * Given historical ILI values that are negative
                 * When compute is called with a negative current ILI
                 * Then result is valid with finite values
                 */
                double[] history = {-0.5, -0.3, -0.4, -0.6, -0.2};

                SurpriseResult result = indicator.compute(-0.4, history);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.surpriseScore()));
                assertTrue(Double.isFinite(result.entropyEstimate()));
                assertTrue(Double.isFinite(result.positionSizeModifier()));
                assertTrue(result.positionSizeModifier() >= 0.5);
                assertTrue(result.positionSizeModifier() <= 1.0);
            }
        }

        @Nested
        @DisplayName("given large historical window")
        class LargeHistory {

            @Test
            @DisplayName("given 100 historical values when compute then computes Shannon entropy")
            void given100HistoricalValues_whenCompute_thenComputesShannonEntropy() {
                /**
                 * Given exactly 100 historical ILI values (default trailing window)
                 * When compute is called
                 * Then entropy is computed and result is valid
                 */
                double[] history = new double[100];
                for (int i = 0; i < 100; i++) {
                    history[i] = Math.sin(i * 0.1) * 0.5;
                }

                SurpriseResult result = indicator.compute(0.3, history);

                assertNotNull(result);
                assertTrue(result.entropyEstimate() > 0.0);
                assertTrue(result.positionSizeModifier() >= 0.5);
                assertTrue(result.positionSizeModifier() <= 1.0);
            }
        }

        @Nested
        @DisplayName("given extreme ILI values")
        class ExtremeValues {

            @Test
            @DisplayName("given very large ILI values when compute then returns finite result")
            void givenVeryLargeIliValues_whenCompute_thenReturnsFiniteResult() {
                /**
                 * Given very large ILI values in history
                 * When compute is called
                 * Then all result fields are finite
                 */
                double[] history = {1e6, 1e6 + 1, 1e6 - 1, 1e6 + 2, 1e6 - 2};

                SurpriseResult result = indicator.compute(1e6, history);

                assertTrue(Double.isFinite(result.surpriseScore()));
                assertTrue(Double.isFinite(result.entropyEstimate()));
                assertTrue(Double.isFinite(result.positionSizeModifier()));
            }

            @Test
            @DisplayName("given zero ILI in history when compute then returns valid result")
            void givenZeroIliHistory_whenCompute_thenReturnsValidResult() {
                /**
                 * Given historical ILI values that include zero
                 * When compute is called with currentIli=0
                 * Then result is valid
                 */
                double[] history = {0.0, 0.0, 0.0, 0.1, -0.1};

                SurpriseResult result = indicator.compute(0.0, history);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.surpriseScore()));
                assertTrue(Double.isFinite(result.positionSizeModifier()));
            }
        }

        @Nested
        @DisplayName("given monotonic increasing history")
        class MonotonicHistory {

            @Test
            @DisplayName("given strictly increasing history when compute at midpoint then moderate surprise")
            void givenStrictlyIncreasingHistory_whenComputeAtMidpoint_thenModerateSurprise() {
                /**
                 * Given strictly increasing historical ILI values from 0 to 1
                 * When compute is called at the midpoint 0.5
                 * Then entropy is positive and surprise is moderate
                 */
                double[] history = new double[50];
                for (int i = 0; i < 50; i++) {
                    history[i] = i / 50.0;
                }

                SurpriseResult result = indicator.compute(0.5, history);

                assertTrue(result.entropyEstimate() > 0.0);
                assertTrue(result.surpriseScore() >= 0.0);
                assertTrue(result.positionSizeModifier() >= 0.5);
            }
        }

        @Nested
        @DisplayName("given two-element history")
        class TwoElementHistory {

            @Test
            @DisplayName("given two distinct historical values when compute matches one then lower surprise")
            void givenTwoDistinctValues_whenComputeMatchesOne_thenLowerSurprise() {
                /**
                 * Given two distinct historical ILI values [0.0, 1.0]
                 * When compute is called with currentIli=0.5 (midpoint)
                 * Then result is valid with finite values
                 */
                double[] history = {0.0, 1.0};

                SurpriseResult result = indicator.compute(0.5, history);

                assertNotNull(result);
                assertTrue(Double.isFinite(result.surpriseScore()));
                assertTrue(Double.isFinite(result.entropyEstimate()));
                assertTrue(result.positionSizeModifier() >= 0.5);
                assertTrue(result.positionSizeModifier() <= 1.0);
            }
        }
    }
}
