package com.tickonomics.computation.ili;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AmbiguityAdjustedIliTest {

    private static final double DEFAULT_BAND_THRESHOLD = 0.5;
    private static final double EPSILON = 1e-9;

    private AmbiguityAdjustedIli service;

    @BeforeEach
    void setUp() {
        service = new AmbiguityAdjustedIli(DEFAULT_BAND_THRESHOLD);
    }

    @Nested
    @DisplayName("adjust - happy path")
    class AdjustHappyPath {

        @Test
        @DisplayName("given valid low-volatility ILI when adjust called then returns CONFIDENT")
            /** Given a valid ILI value with low historical volatility and sufficient lookback.
             *  When adjust is called.
             *  Then the result has status CONFIDENT with correct upper/lower bands.
             */
        void givenValidLowVolatilityIli_whenAdjust_thenReturnsConfident() {
            double ili = 0.5;
            double volatility = 0.1;
            int lookback = 100;

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertNotNull(result);
            assertEquals(ili, result.ili(), EPSILON);
            assertEquals("CONFIDENT", result.status());
            assertFalse(result.isSignalSuppressed());

            double expectedStdError = volatility / Math.sqrt(lookback);
            double expectedUpper = ili + 1.96 * expectedStdError;
            double expectedLower = ili - 1.96 * expectedStdError;
            assertEquals(expectedUpper, result.upperBand(), EPSILON);
            assertEquals(expectedLower, result.lowerBand(), EPSILON);
        }

        @Test
        @DisplayName("given valid high-volatility ILI when adjust called then returns UNCERTAIN")
            /** Given a valid ILI value with high historical volatility making band width exceed threshold.
             *  When adjust is called.
             *  Then the result has status UNCERTAIN and signals are suppressed.
             */
        void givenValidHighVolatilityIli_whenAdjust_thenReturnsUncertain() {
            double ili = 0.5;
            double volatility = 5.0;
            int lookback = 10;

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertNotNull(result);
            assertEquals(ili, result.ili(), EPSILON);
            assertEquals("UNCERTAIN", result.status());
            assertTrue(result.isSignalSuppressed());
            assertNotNull(result.reason());
            assertTrue(result.reason().contains("exceeds threshold"));
        }

        @Test
        @DisplayName("given zero volatility when adjust called then returns CONFIDENT with zero-width band")
            /** Given a valid ILI with zero historical volatility.
             *  When adjust is called.
             *  Then the result has status CONFIDENT with upper == lower == ili.
             */
        void givenZeroVolatility_whenAdjust_thenReturnsConfidentWithZeroWidthBand() {
            double ili = 0.75;
            double volatility = 0.0;
            int lookback = 50;

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertNotNull(result);
            assertEquals(ili, result.ili(), EPSILON);
            assertEquals(ili, result.upperBand(), EPSILON);
            assertEquals(ili, result.lowerBand(), EPSILON);
            assertEquals("CONFIDENT", result.status());
            assertFalse(result.isSignalSuppressed());
        }
    }

    @Nested
    @DisplayName("adjust - boundary band width")
    class AdjustBoundaryBandWidth {

        @Test
        @DisplayName("given volatility producing exact threshold band width when adjust called then returns CONFIDENT")
            /** Given volatility and lookback that produce a band width exactly equal to the threshold.
             *  When adjust is called.
             *  Then the result has status CONFIDENT since band width equals (not exceeds) threshold.
             */
        void givenExactThresholdBandWidth_whenAdjust_thenReturnsConfident() {
            double ili = 0.5;
            double bandWidth = DEFAULT_BAND_THRESHOLD;
            double stdError = bandWidth / (2.0 * 1.96);
            int lookback = 25;
            double volatility = stdError * Math.sqrt(lookback);

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertEquals("CONFIDENT", result.status());
        }

        @Test
        @DisplayName("given volatility producing just-above-threshold band width when adjust called then returns UNCERTAIN")
            /** Given volatility and lookback that produce a band width just above the threshold.
             *  When adjust is called.
             *  Then the result has status UNCERTAIN.
             */
        void givenJustAboveThresholdBandWidth_whenAdjust_thenReturnsUncertain() {
            double ili = 0.5;
            double bandWidth = DEFAULT_BAND_THRESHOLD + 0.01;
            double stdError = bandWidth / (2.0 * 1.96);
            int lookback = 25;
            double volatility = stdError * Math.sqrt(lookback);

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertEquals("UNCERTAIN", result.status());
        }

        @Test
        @DisplayName("given volatility producing just-below-threshold band width when adjust called then returns CONFIDENT")
            /** Given volatility and lookback that produce a band width just below the threshold.
             *  When adjust is called.
             *  Then the result has status CONFIDENT.
             */
        void givenJustBelowThresholdBandWidth_whenAdjust_thenReturnsConfident() {
            double ili = 0.5;
            double bandWidth = DEFAULT_BAND_THRESHOLD - 0.01;
            double stdError = bandWidth / (2.0 * 1.96);
            int lookback = 25;
            double volatility = stdError * Math.sqrt(lookback);

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertEquals("CONFIDENT", result.status());
        }
    }

    @Nested
    @DisplayName("adjust - invalid and edge case inputs")
    class AdjustInvalidInputs {

        @Test
        @DisplayName("given zero lookback count when adjust called then returns SUPPRESSED")
            /** Given a valid ILI value but lookbackCount of zero.
             *  When adjust is called.
             *  Then the result has status SUPPRESSED with reason about non-positive lookback.
             */
        void givenZeroLookbackCount_whenAdjust_thenReturnsSuppressed() {
            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(0.5, 0.1, 0);

            assertEquals("SUPPRESSED", result.status());
            assertTrue(result.isSignalSuppressed());
            assertTrue(result.reason().contains("Non-positive lookback"));
        }

        @Test
        @DisplayName("given negative lookback count when adjust called then returns SUPPRESSED")
            /** Given a valid ILI value but negative lookbackCount.
             *  When adjust is called.
             *  Then the result has status SUPPRESSED.
             */
        void givenNegativeLookbackCount_whenAdjust_thenReturnsSuppressed() {
            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(0.5, 0.1, -10);

            assertEquals("SUPPRESSED", result.status());
            assertTrue(result.isSignalSuppressed());
        }

        @Test
        @DisplayName("given NaN ILI value when adjust called then returns SUPPRESSED")
            /** Given a NaN ILI value with valid volatility and lookback.
             *  When adjust is called.
             *  Then the result has status SUPPRESSED with reason about invalid ILI.
             */
        void givenNaNIliValue_whenAdjust_thenReturnsSuppressed() {
            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(Double.NaN, 0.1, 100);

            assertEquals("SUPPRESSED", result.status());
            assertTrue(result.isSignalSuppressed());
            assertTrue(result.reason().contains("Invalid ILI"));
        }

        @Test
        @DisplayName("given infinite ILI value when adjust called then returns SUPPRESSED")
            /** Given a positive infinite ILI value with valid volatility and lookback.
             *  When adjust is called.
             *  Then the result has status SUPPRESSED.
             */
        void givenInfiniteIliValue_whenAdjust_thenReturnsSuppressed() {
            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(Double.POSITIVE_INFINITY, 0.1, 100);

            assertEquals("SUPPRESSED", result.status());
            assertTrue(result.isSignalSuppressed());
        }

        @Test
        @DisplayName("given NaN volatility when adjust called then returns SUPPRESSED")
            /** Given a valid ILI value but NaN historical volatility.
             *  When adjust is called.
             *  Then the result has status SUPPRESSED with reason about invalid volatility.
             */
        void givenNaNVolatility_whenAdjust_thenReturnsSuppressed() {
            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(0.5, Double.NaN, 100);

            assertEquals("SUPPRESSED", result.status());
            assertTrue(result.isSignalSuppressed());
            assertTrue(result.reason().contains("Invalid historical volatility"));
        }

        @Test
        @DisplayName("given negative volatility when adjust called then returns SUPPRESSED")
            /** Given a valid ILI value but negative historical volatility.
             *  When adjust is called.
             *  Then the result has status SUPPRESSED.
             */
        void givenNegativeVolatility_whenAdjust_thenReturnsSuppressed() {
            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(0.5, -0.1, 100);

            assertEquals("SUPPRESSED", result.status());
            assertTrue(result.isSignalSuppressed());
        }

        @Test
        @DisplayName("given infinite volatility when adjust called then returns SUPPRESSED")
            /** Given a valid ILI value but infinite historical volatility.
             *  When adjust is called.
             *  Then the result has status SUPPRESSED.
             */
        void givenInfiniteVolatility_whenAdjust_thenReturnsSuppressed() {
            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(0.5, Double.POSITIVE_INFINITY, 100);

            assertEquals("SUPPRESSED", result.status());
            assertTrue(result.isSignalSuppressed());
        }
    }

    @Nested
    @DisplayName("adjust - lookback count variations")
    class AdjustLookbackVariations {

        @Test
        @DisplayName("given lookback of 1 when adjust called then returns valid result with wide bands")
            /** Given a valid ILI with lookbackCount of 1 producing maximum standard error.
             *  When adjust is called.
             *  Then the result has correctly computed wide bands based on stdError = volatility / 1.
             */
        void givenLookbackOfOne_whenAdjust_thenReturnsValidResultWithWideBands() {
            double ili = 0.3;
            double volatility = 0.2;
            int lookback = 1;

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertNotNull(result);
            assertEquals(ili, result.ili(), EPSILON);
            double expectedStdError = volatility / 1.0;
            assertEquals(ili + 1.96 * expectedStdError, result.upperBand(), EPSILON);
            assertEquals(ili - 1.96 * expectedStdError, result.lowerBand(), EPSILON);
        }

        @Test
        @DisplayName("given very large lookback when adjust called then returns narrow bands")
            /** Given a valid ILI with a very large lookbackCount producing a tiny standard error.
             *  When adjust is called.
             *  Then the result has status CONFIDENT with bands very close to the ILI value.
             */
        void givenVeryLargeLookback_whenAdjust_thenReturnsNarrowBands() {
            double ili = 0.5;
            double volatility = 0.1;
            int lookback = 1_000_000;

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertEquals("CONFIDENT", result.status());
            double bandWidth = result.upperBand() - result.lowerBand();
            assertTrue(bandWidth < 0.001);
        }
    }

    @Nested
    @DisplayName("adjust - band threshold configuration")
    class AdjustBandThresholdConfig {

        @Test
        @DisplayName("given custom high threshold when adjust called then volatile input still returns CONFIDENT")
            /** Given a service configured with a high band threshold of 10.0 and volatile input.
             *  When adjust is called.
             *  Then the result has status CONFIDENT because the band width fits within the wide threshold.
             */
        void givenHighThreshold_whenAdjust_thenVolatileInputReturnsConfident() {
            AmbiguityAdjustedIli highThresholdService = new AmbiguityAdjustedIli(10.0);

            AmbiguityAdjustedIli.AmbiguityResult result = highThresholdService.adjust(0.5, 5.0, 10);

            assertEquals("CONFIDENT", result.status());
            assertFalse(result.isSignalSuppressed());
        }

        @Test
        @DisplayName("given custom zero threshold when adjust called then any non-zero band returns UNCERTAIN")
            /** Given a service configured with a zero band threshold and minimal volatility.
             *  When adjust is called.
             *  Then the result has status UNCERTAIN because any non-zero band width exceeds zero.
             */
        void givenZeroThreshold_whenAdjust_thenAnyNonZeroBandReturnsUncertain() {
            AmbiguityAdjustedIli zeroThresholdService = new AmbiguityAdjustedIli(0.0);

            AmbiguityAdjustedIli.AmbiguityResult result = zeroThresholdService.adjust(0.5, 0.001, 100);

            assertEquals("UNCERTAIN", result.status());
            assertTrue(result.isSignalSuppressed());
        }
    }

    @Nested
    @DisplayName("AmbiguityResult record")
    class AmbiguityResultRecord {

        @Test
        @DisplayName("given SUPPRESSED status when isSignalSuppressed called then returns true")
            /** Given an AmbiguityResult with SUPPRESSED status.
             *  When isSignalSuppressed is called.
             *  Then it returns true.
             */
        void givenSuppressedStatus_whenIsSignalSuppressed_thenReturnsTrue() {
            var result = new AmbiguityAdjustedIli.AmbiguityResult(0.5, 0.5, 0.5, "SUPPRESSED", "test");

            assertTrue(result.isSignalSuppressed());
        }

        @Test
        @DisplayName("given UNCERTAIN status when isSignalSuppressed called then returns true")
            /** Given an AmbiguityResult with UNCERTAIN status.
             *  When isSignalSuppressed is called.
             *  Then it returns true.
             */
        void givenUncertainStatus_whenIsSignalSuppressed_thenReturnsTrue() {
            var result = new AmbiguityAdjustedIli.AmbiguityResult(0.5, 0.6, 0.4, "UNCERTAIN", "test");

            assertTrue(result.isSignalSuppressed());
        }

        @Test
        @DisplayName("given CONFIDENT status when isSignalSuppressed called then returns false")
            /** Given an AmbiguityResult with CONFIDENT status.
             *  When isSignalSuppressed is called.
             *  Then it returns false.
             */
        void givenConfidentStatus_whenIsSignalSuppressed_thenReturnsFalse() {
            var result = new AmbiguityAdjustedIli.AmbiguityResult(0.5, 0.55, 0.45, "CONFIDENT", "test");

            assertFalse(result.isSignalSuppressed());
        }
    }

    @Nested
    @DisplayName("adjust - negative ILI values")
    class AdjustNegativeIli {

        @Test
        @DisplayName("given negative ILI value when adjust called then bands are correctly computed")
            /** Given a negative ILI value with moderate volatility.
             *  When adjust is called.
             *  Then the bands are correctly computed around the negative ILI value.
             */
        void givenNegativeIli_whenAdjust_thenBandsCorrectlyComputed() {
            double ili = -0.3;
            double volatility = 0.05;
            int lookback = 50;

            AmbiguityAdjustedIli.AmbiguityResult result = service.adjust(ili, volatility, lookback);

            assertNotNull(result);
            assertEquals(ili, result.ili(), EPSILON);
            assertTrue(result.upperBand() > ili);
            assertTrue(result.lowerBand() < ili);
            assertEquals("CONFIDENT", result.status());
        }
    }
}
