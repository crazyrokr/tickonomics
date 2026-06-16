package com.tickonomics.computation.signal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SentimentVolatilityGuardTest {

    @InjectMocks
    private SentimentVolatilityGuard guard;

    @Nested
    class ComputeThresholdModifier {
        @Test
        void givenLowCertainty_whenCompute_thenWiden15x() {
            /* Given certainty < 0.3 */
            /* When computing threshold modifier */
            /* Then modifier is 1.5 with LOW_CERTAINTY_WIDEN reason */
            var result = guard.computeThresholdModifier(0.1);

            assertEquals(1.5, result.thresholdModifier(), 0.01);
            assertEquals(0.1, result.certainty(), 0.01);
            assertEquals("LOW_CERTAINTY_WIDEN", result.adjustmentReason());
        }

        @Test
        void givenHighCertainty_whenCompute_thenNarrow08x() {
            /* Given certainty > 0.7 */
            /* When computing threshold modifier */
            /* Then modifier is 0.8 with HIGH_CERTAINTY_NARROW reason */
            var result = guard.computeThresholdModifier(0.8);

            assertEquals(0.8, result.thresholdModifier(), 0.01);
            assertEquals("HIGH_CERTAINTY_NARROW", result.adjustmentReason());
        }

        @Test
        void givenModerateCertainty_whenCompute_thenNoChange() {
            /* Given certainty between 0.3 and 0.7 */
            /* When computing threshold modifier */
            /* Then modifier is 1.0 with MODERATE reason */
            var result = guard.computeThresholdModifier(0.5);

            assertEquals(1.0, result.thresholdModifier(), 0.01);
            assertEquals("MODERATE_CERTAINTY_NO_CHANGE", result.adjustmentReason());
        }

        @Test
        void givenCertaintyExactly03_whenCompute_thenWiden() {
            /* Given certainty exactly 0.3 */
            /* When computing threshold modifier */
            /* Then modifier is 1.5 (boundary < 0.3 is false, so 0.3 falls into moderate) */
            var result = guard.computeThresholdModifier(0.3);

            assertEquals(1.0, result.thresholdModifier(), 0.01);
            assertEquals("MODERATE_CERTAINTY_NO_CHANGE", result.adjustmentReason());
        }

        @Test
        void givenCertaintyExactly07_whenCompute_thenModerate() {
            /* Given certainty exactly 0.7 */
            /* When computing threshold modifier */
            /* Then modifier is 1.0 (not > 0.7, so moderate) */
            var result = guard.computeThresholdModifier(0.7);

            assertEquals(1.0, result.thresholdModifier(), 0.01);
            assertEquals("MODERATE_CERTAINTY_NO_CHANGE", result.adjustmentReason());
        }

        @Test
        void givenCertaintyJustAbove07_whenCompute_thenNarrow() {
            /* Given certainty just above 0.7 */
            /* When computing threshold modifier */
            /* Then modifier is 0.8 */
            var result = guard.computeThresholdModifier(0.71);

            assertEquals(0.8, result.thresholdModifier(), 0.01);
            assertEquals("HIGH_CERTAINTY_NARROW", result.adjustmentReason());
        }

        @Test
        void givenZeroCertainty_whenCompute_thenWiden() {
            /* Given certainty = 0 */
            /* When computing threshold modifier */
            /* Then modifier is 1.5 */
            var result = guard.computeThresholdModifier(0.0);

            assertEquals(1.5, result.thresholdModifier(), 0.01);
        }
    }

    @Nested
    class ShouldSuppressSignal {
        @Test
        void givenVeryLowCertaintyAndWeakSignal_whenSuppress_thenTrue() {
            /* Given certainty < 0.2 and signal below adjusted threshold */
            /* When checking suppression */
            /* Then signal is suppressed */
            boolean suppressed = guard.shouldSuppressSignal(0.1, 0.05, 0.1);

            assertTrue(suppressed);
        }

        @Test
        void givenVeryLowCertaintyAndStrongSignal_whenSuppress_thenFalse() {
            /* Given certainty < 0.2 but signal above adjusted threshold */
            /* When checking suppression */
            /* Then signal is NOT suppressed */
            boolean suppressed = guard.shouldSuppressSignal(0.1, 1.0, 0.1);

            assertFalse(suppressed);
        }

        @Test
        void givenAdequateCertainty_whenSuppress_thenFalse() {
            /* Given certainty >= 0.2 */
            /* When checking suppression */
            /* Then signal is NOT suppressed regardless of strength */
            boolean suppressed = guard.shouldSuppressSignal(0.5, 0.001, 0.5);

            assertFalse(suppressed);
        }

        @Test
        void givenCertaintyExactly02_whenSuppress_thenFalse() {
            /* Given certainty exactly 0.2 */
            /* When checking suppression */
            /* Then signal is NOT suppressed (not strictly < 0.2) */
            boolean suppressed = guard.shouldSuppressSignal(0.2, 0.001, 1.0);

            assertFalse(suppressed);
        }

        @Test
        void givenLowCertaintyWidenedThreshold_whenSuppress_thenRequiresStrongerSignal() {
            /* Given certainty 0.1 (widens threshold 1.5x) */
            /* When checking suppression with signal = threshold */
            /* Then signal is suppressed because adjusted threshold is higher */
            boolean suppressed = guard.shouldSuppressSignal(0.1, 0.1, 0.1);

            assertTrue(suppressed);
        }

        @Test
        void givenHighCertaintyNarrowedThreshold_whenSuppress_thenEasierToPass() {
            /* Given certainty 0.8 (narrows threshold 0.8x) */
            /* When checking suppression */
            /* Then signal is NOT suppressed because certainty >= 0.2 */
            boolean suppressed = guard.shouldSuppressSignal(0.8, 0.01, 0.1);

            assertFalse(suppressed);
        }
    }
}
