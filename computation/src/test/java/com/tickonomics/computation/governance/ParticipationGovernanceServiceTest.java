package com.tickonomics.computation.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ParticipationGovernanceServiceTest {

    private ParticipationGovernanceService service;

    @BeforeEach
    void setUp() {
        service = new ParticipationGovernanceService();
    }

    @Nested
    class Evaluate {

        @Nested
        class GivenAdmissibleSignal_whenEvaluated_thenPasses {

            @Test
            void givenValidInputs_whenEvaluated_thenAdmissible() {
                // Given: a valid signal with no governance violations
                double signalStrength = 1.5;
                double proxyDivergence = 0.5;
                String regimeType = "LOW_VOL";
                boolean ambiguityFlag = false;
                boolean exogenousShock = false;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        signalStrength, proxyDivergence, regimeType,
                        ambiguityFlag, exogenousShock);

                // Then: the signal is admissible with the original strength
                assertTrue(verdict.admissible());
                assertNull(verdict.suppressionReason());
                assertEquals(1.5, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenMetastableRegime_whenEvaluated_thenAdmissible() {
                // Given: a METASTABLE regime with clean signal
                double signalStrength = 2.0;
                double proxyDivergence = 0.1;
                String regimeType = "METASTABLE";

                // When: evaluating the signal
                var verdict = service.evaluate(
                        signalStrength, proxyDivergence, regimeType,
                        false, false);

                // Then: the signal is admissible
                assertTrue(verdict.admissible());
                assertEquals(2.0, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenZeroSignalStrength_whenEvaluated_thenAdmissible() {
                // Given: a zero-strength signal with no governance violations
                double signalStrength = 0.0;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        signalStrength, 0.0, "LOW_VOL", false, false);

                // Then: the signal is still admissible (zero is valid)
                assertTrue(verdict.admissible());
                assertEquals(0.0, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenNegativeSignalStrength_whenEvaluated_thenAdmissible() {
                // Given: a negative signal strength (sell signal) with no violations
                double signalStrength = -1.2;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        signalStrength, 0.3, "LOW_VOL", false, false);

                // Then: the signal is admissible
                assertTrue(verdict.admissible());
                assertEquals(-1.2, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenProxyDivergenceAtExactThreshold_whenEvaluated_thenAdmissible() {
                // Given: proxy divergence exactly at the threshold (2.0) is not > 2.0
                double proxyDivergence = 2.0;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, proxyDivergence, "LOW_VOL", false, false);

                // Then: the signal is admissible (not strictly greater than threshold)
                assertTrue(verdict.admissible());
                assertEquals(1.0, verdict.adjustedSignal(), 1e-9);
            }
        }

        @Nested
        class GivenProxyDislocation_whenEvaluated_thenSuppressed {

            @Test
            void givenDivergenceAboveThreshold_whenEvaluated_thenProxyDislocation() {
                // Given: proxy divergence exceeding the 2.0 threshold
                double proxyDivergence = 2.5;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, proxyDivergence, "LOW_VOL", false, false);

                // Then: the signal is suppressed for proxy dislocation
                assertFalse(verdict.admissible());
                assertEquals("PROXY_DISLOCATION", verdict.suppressionReason());
                assertEquals(0.0, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenVeryHighDivergence_whenEvaluated_thenProxyDislocation() {
                // Given: extremely high proxy divergence
                double proxyDivergence = 10.0;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        3.0, proxyDivergence, "LOW_VOL", false, false);

                // Then: the signal is suppressed
                assertFalse(verdict.admissible());
                assertEquals("PROXY_DISLOCATION", verdict.suppressionReason());
            }
        }

        @Nested
        class GivenDegradedIliRegime_whenEvaluated_thenSuppressed {

            @Test
            void givenHighVolRegime_whenEvaluated_thenDegradedIli() {
                // Given: a HIGH_VOL regime type
                String regimeType = "HIGH_VOL";

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, regimeType, false, false);

                // Then: the signal is suppressed for degraded ILI
                assertFalse(verdict.admissible());
                assertEquals("DEGRADED_ILI", verdict.suppressionReason());
                assertEquals(0.0, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenUnstableRegime_whenEvaluated_thenDegradedIli() {
                // Given: an UNSTABLE regime type
                String regimeType = "UNSTABLE";

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, regimeType, false, false);

                // Then: the signal is suppressed for degraded ILI
                assertFalse(verdict.admissible());
                assertEquals("DEGRADED_ILI", verdict.suppressionReason());
            }
        }

        @Nested
        class GivenUnknownRegime_whenEvaluated_thenSuppressed {

            @Test
            void givenUnknownRegimeType_whenEvaluated_thenRegimeCheckFailure() {
                // Given: a regime type of UNKNOWN
                String regimeType = "UNKNOWN";

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, regimeType, false, false);

                // Then: the signal is suppressed for regime check failure
                assertFalse(verdict.admissible());
                assertEquals("REGIME_CHECK_FAILURE", verdict.suppressionReason());
                assertEquals(0.0, verdict.adjustedSignal(), 1e-9);
            }
        }

        @Nested
        class GivenAmbiguityFlag_whenEvaluated_thenSuppressed {

            @Test
            void givenAmbiguityFlagTrue_whenEvaluated_thenAmbiguitySuppressed() {
                // Given: ambiguity flag is set to true
                boolean ambiguityFlag = true;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, "LOW_VOL", ambiguityFlag, false);

                // Then: the signal is suppressed for ambiguity
                assertFalse(verdict.admissible());
                assertEquals("AMBIGUITY", verdict.suppressionReason());
                assertEquals(0.0, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenAmbiguityFlagFalse_whenEvaluated_thenNotSuppressedForAmbiguity() {
                // Given: ambiguity flag is false with otherwise clean inputs
                boolean ambiguityFlag = false;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, "LOW_VOL", ambiguityFlag, false);

                // Then: the signal is not suppressed for ambiguity
                assertTrue(verdict.admissible());
            }
        }

        @Nested
        class GivenExogenousShock_whenEvaluated_thenSuppressed {

            @Test
            void givenExogenousShockTrue_whenEvaluated_thenExogenousShockSuppressed() {
                // Given: an exogenous shock is active
                boolean exogenousShock = true;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, "LOW_VOL", false, exogenousShock);

                // Then: the signal is suppressed for exogenous shock
                assertFalse(verdict.admissible());
                assertEquals("EXOGENOUS_SHOCK", verdict.suppressionReason());
                assertEquals(0.0, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenExogenousShockFalse_whenEvaluated_thenNotSuppressedForShock() {
                // Given: no exogenous shock with otherwise clean inputs
                boolean exogenousShock = false;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, "LOW_VOL", false, exogenousShock);

                // Then: the signal is not suppressed for shock
                assertTrue(verdict.admissible());
            }
        }

        @Nested
        class GivenMultipleViolations_whenEvaluated_thenFirstCheckWins {

            @Test
            void givenProxyDislocationAndAmbiguity_whenEvaluated_thenProxyDislocationFirst() {
                // Given: both proxy dislocation and ambiguity flag are active
                double proxyDivergence = 3.0;
                boolean ambiguityFlag = true;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, proxyDivergence, "LOW_VOL", ambiguityFlag, false);

                // Then: proxy dislocation takes precedence (checked first)
                assertEquals("PROXY_DISLOCATION", verdict.suppressionReason());
            }

            @Test
            void givenDegradedIliAndAmbiguity_whenEvaluated_thenDegradedIliFirst() {
                // Given: degraded ILI regime and ambiguity both active
                String regimeType = "HIGH_VOL";
                boolean ambiguityFlag = true;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, regimeType, ambiguityFlag, false);

                // Then: degraded ILI takes precedence over ambiguity
                assertEquals("DEGRADED_ILI", verdict.suppressionReason());
            }

            @Test
            void givenUnknownRegimeAndShock_whenEvaluated_thenRegimeCheckFirst() {
                // Given: unknown regime and exogenous shock both active
                String regimeType = "UNKNOWN";
                boolean exogenousShock = true;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, regimeType, false, exogenousShock);

                // Then: regime check failure takes precedence over shock
                assertEquals("REGIME_CHECK_FAILURE", verdict.suppressionReason());
            }

            @Test
            void givenAmbiguityAndShock_whenEvaluated_thenAmbiguityFirst() {
                // Given: ambiguity and exogenous shock both active
                boolean ambiguityFlag = true;
                boolean exogenousShock = true;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, "LOW_VOL", ambiguityFlag, exogenousShock);

                // Then: ambiguity takes precedence over shock
                assertEquals("AMBIGUITY", verdict.suppressionReason());
            }
        }

        @Nested
        class GivenBoundaryValues_whenEvaluated_thenCorrectResult {

            @Test
            void givenDivergenceJustAboveThreshold_whenEvaluated_thenSuppressed() {
                // Given: proxy divergence just above the 2.0 threshold
                double proxyDivergence = 2.000001;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, proxyDivergence, "LOW_VOL", false, false);

                // Then: the signal is suppressed
                assertFalse(verdict.admissible());
                assertEquals("PROXY_DISLOCATION", verdict.suppressionReason());
            }

            @Test
            void givenDivergenceJustBelowThreshold_whenEvaluated_thenAdmissible() {
                // Given: proxy divergence just below the 2.0 threshold
                double proxyDivergence = 1.999999;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, proxyDivergence, "LOW_VOL", false, false);

                // Then: the signal is admissible
                assertTrue(verdict.admissible());
            }

            @Test
            void givenMaxDoubleSignal_whenEvaluated_thenAdmissible() {
                // Given: maximum double value as signal strength
                double signalStrength = Double.MAX_VALUE;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        signalStrength, 0.0, "LOW_VOL", false, false);

                // Then: the signal is admissible with max value preserved
                assertTrue(verdict.admissible());
                assertEquals(Double.MAX_VALUE, verdict.adjustedSignal(), 0.0);
            }

            @Test
            void givenMinDoubleSignal_whenEvaluated_thenAdmissible() {
                // Given: minimum double value as signal strength
                double signalStrength = -Double.MAX_VALUE;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        signalStrength, 0.0, "LOW_VOL", false, false);

                // Then: the signal is admissible
                assertTrue(verdict.admissible());
                assertEquals(-Double.MAX_VALUE, verdict.adjustedSignal(), 0.0);
            }
        }

        @Nested
        class GivenNullAndEmptyInputs_whenEvaluated_thenHandled {

            @Test
            void givenNullRegimeType_whenEvaluated_thenNotSuppressedByRegime() {
                // Given: a null regime type (not matching any suppression condition)
                String regimeType = null;

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, regimeType, false, false);

                // Then: null regime does not match HIGH_VOL/UNSTABLE/UNKNOWN, so admissible
                assertTrue(verdict.admissible());
                assertEquals(1.0, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenEmptyRegimeType_whenEvaluated_thenAdmissible() {
                // Given: an empty string regime type
                String regimeType = "";

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, regimeType, false, false);

                // Then: empty string does not match any suppression condition
                assertTrue(verdict.admissible());
                assertEquals(1.0, verdict.adjustedSignal(), 1e-9);
            }

            @Test
            void givenArbitraryRegimeType_whenEvaluated_thenAdmissible() {
                // Given: a custom regime type that is not in the suppression list
                String regimeType = "CUSTOM_REGIME";

                // When: evaluating the signal
                var verdict = service.evaluate(
                        1.0, 0.5, regimeType, false, false);

                // Then: unrecognized regime types are admissible
                assertTrue(verdict.admissible());
            }
        }
    }

    @Nested
    class GetSuppressionCounts {

        @Test
        void givenNoSuppressions_whenGetCounts_thenEmptyMap() {
            // Given: a fresh service with no evaluations performed
            var service = new ParticipationGovernanceService();

            // When: retrieving suppression counts
            var counts = service.getSuppressionCounts();

            // Then: the map is empty
            assertNotNull(counts);
            assertTrue(counts.isEmpty());
        }

        @Test
        void givenProxyDislocationSuppression_whenGetCounts_thenCounterIncremented() {
            // Given: a signal was suppressed for proxy dislocation
            service.evaluate(1.0, 3.0, "LOW_VOL", false, false);

            // When: retrieving suppression counts
            var counts = service.getSuppressionCounts();

            // Then: the PROXY_DISLOCATION counter is 1
            assertEquals(1L, counts.get("PROXY_DISLOCATION"));
        }

        @Test
        void givenMultipleSuppressions_whenGetCounts_thenAllCountersTracked() {
            // Given: signals were suppressed for multiple reasons
            service.evaluate(1.0, 3.0, "LOW_VOL", false, false);
            service.evaluate(1.0, 0.5, "HIGH_VOL", false, false);
            service.evaluate(1.0, 0.5, "UNKNOWN", false, false);

            // When: retrieving suppression counts
            var counts = service.getSuppressionCounts();

            // Then: all three counters are tracked
            assertEquals(1L, counts.get("PROXY_DISLOCATION"));
            assertEquals(1L, counts.get("DEGRADED_ILI"));
            assertEquals(1L, counts.get("REGIME_CHECK_FAILURE"));
        }

        @Test
        void givenRepeatedSameSuppression_whenGetCounts_thenCounterAccumulates() {
            // Given: two signals were suppressed for the same reason
            service.evaluate(1.0, 3.0, "LOW_VOL", false, false);
            service.evaluate(1.0, 2.5, "LOW_VOL", false, false);

            // When: retrieving suppression counts
            var counts = service.getSuppressionCounts();

            // Then: the counter shows 2 suppressions
            assertEquals(2L, counts.get("PROXY_DISLOCATION"));
        }

        @Test
        void givenAdmissibleSignal_whenGetCounts_thenNoCountersAdded() {
            // Given: an admissible signal was evaluated
            service.evaluate(1.0, 0.5, "LOW_VOL", false, false);

            // When: retrieving suppression counts
            var counts = service.getSuppressionCounts();

            // Then: no suppression counters exist
            assertTrue(counts.isEmpty());
        }
    }
}
