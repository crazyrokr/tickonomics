package com.tickonomics.computation.signal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AlgorithmicBehaviorAlignmentTest {

    @InjectMocks
    private AlgorithmicBehaviorAlignment alignment;

    @Nested
    class ComputeTimeDecayAlignment {
        @Test
        void givenZeroMinutes_whenCompute_thenMaximumFactor() {
            /* Given minutes to publication = 0 */
            /* When computing time decay alignment */
            /* Then factor = 1 + 0.5 * exp(0) = 1.5 */
            var result = alignment.computeTimeDecayAlignment(0.01, 0.0);

            assertEquals(1.5, result.timeDecayFactor(), 0.001);
            assertEquals(0.015, result.adjustedThreshold(), 0.0001);
            assertEquals(0.0, result.minutesToPublication(), 0.01);
        }

        @Test
        void givenLargeMinutes_whenCompute_thenFactorApproaches1() {
            /* Given very large minutes to publication */
            /* When computing time decay alignment */
            /* Then factor approaches 1.0 */
            var result = alignment.computeTimeDecayAlignment(0.01, 10000.0);

            assertEquals(1.0, result.timeDecayFactor(), 0.001);
            assertEquals(0.01, result.adjustedThreshold(), 0.0001);
        }

        @Test
        void given60Minutes_whenCompute_thenModerateFactor() {
            /* Given 60 minutes to publication */
            /* When computing time decay alignment */
            /* Then factor = 1 + 0.5 * exp(-1) */
            var result = alignment.computeTimeDecayAlignment(0.01, 60.0);

            double expectedFactor = 1.0 + 0.5 * Math.exp(-1.0);
            assertEquals(expectedFactor, result.timeDecayFactor(), 0.001);
            assertEquals(0.01 * expectedFactor, result.adjustedThreshold(), 0.0001);
        }

        @Test
        void given30Minutes_whenCompute_thenHigherFactor() {
            /* Given 30 minutes to publication */
            /* When computing time decay alignment */
            /* Then factor > factor at 60 minutes */
            var result30 = alignment.computeTimeDecayAlignment(0.01, 30.0);
            var result60 = alignment.computeTimeDecayAlignment(0.01, 60.0);

            assertTrue(result30.timeDecayFactor() > result60.timeDecayFactor());
        }

        @Test
        void givenDifferentThresholds_whenCompute_thenProportionalAdjustment() {
            /* Given base threshold 0.02 vs 0.01 */
            /* When computing time decay alignment */
            /* Then adjusted thresholds are proportional */
            var result1 = alignment.computeTimeDecayAlignment(0.01, 60.0);
            var result2 = alignment.computeTimeDecayAlignment(0.02, 60.0);

            assertEquals(result1.timeDecayFactor(), result2.timeDecayFactor(), 0.001);
            assertEquals(2.0 * result1.adjustedThreshold(), result2.adjustedThreshold(), 0.0001);
        }
    }

    @Nested
    class ComputeLiquidityAdjustedSlippage {
        @Test
        void givenZeroLsi_whenCompute_thenBaseSlippage() {
            /* Given liquidity stress index = 0 */
            /* When computing adjusted slippage */
            /* Then result equals base slippage */
            double result = alignment.computeLiquidityAdjustedSlippage(5.0, 0.0);

            assertEquals(5.0, result, 0.01);
        }

        @Test
        void givenPositiveLsi_whenCompute_thenIncreasedSlippage() {
            /* Given positive liquidity stress index */
            /* When computing adjusted slippage */
            /* Then result is higher than base */
            double result = alignment.computeLiquidityAdjustedSlippage(5.0, 0.5);

            assertEquals(7.5, result, 0.01);
        }

        @Test
        void givenNegativeLsi_whenCompute_thenDecreasedSlippage() {
            /* Given negative liquidity stress index */
            /* When computing adjusted slippage */
            /* Then result is lower than base */
            double result = alignment.computeLiquidityAdjustedSlippage(5.0, -0.3);

            assertEquals(3.5, result, 0.01);
        }

        @Test
        void givenLsiOf1_whenCompute_thenDoubleSlippage() {
            /* Given liquidity stress index = 1.0 */
            /* When computing adjusted slippage */
            /* Then result is 2x base */
            double result = alignment.computeLiquidityAdjustedSlippage(10.0, 1.0);

            assertEquals(20.0, result, 0.01);
        }

        @Test
        void givenZeroBase_whenCompute_thenZero() {
            /* Given zero base slippage */
            /* When computing adjusted slippage */
            /* Then result is zero regardless of LSI */
            double result = alignment.computeLiquidityAdjustedSlippage(0.0, 5.0);

            assertEquals(0.0, result, 0.01);
        }
    }
}
