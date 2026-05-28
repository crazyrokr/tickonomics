package com.tickonomics.computation.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.tickonomics.computation.ili.ClimateSensitivityFactor;
import com.tickonomics.computation.ili.ClimateSensitivityFactor.ThresholdAdjustment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClimateRiskGuardTest {

    @Mock
    private ClimateSensitivityFactor climateSensitivityFactor;

    private ClimateRiskGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ClimateRiskGuard(climateSensitivityFactor);
    }

    private ThresholdAdjustment adjustmentWithFactor(double factor) {
        return new ThresholdAdjustment(factor, 0.0, 0.0, 0.0);
    }

    @Nested
    class Evaluate {

        @Nested
        class GivenLowClimateSensitivity_whenEvaluated_thenLowRisk {

            @Test
            void givenHighConfidenceAndZeroFactor_whenEvaluated_thenLowRisk() {
                // Given: high ILI confidence with zero climate modifier
                double iliConfidence = 0.95;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.0));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: confidence is unchanged and risk level is LOW
                assertEquals(0.95, result.original(), 1e-9);
                assertEquals(0.0, result.modifier(), 1e-9);
                assertEquals(0.95, result.adjusted(), 1e-9);
                assertEquals("LOW", result.riskLevel());
            }

            @Test
            void givenConfidenceAboveThreshold_whenEvaluated_thenLowRisk() {
                // Given: ILI confidence just above 0.8 with small climate modifier
                double iliConfidence = 0.85;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.05));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.85 * (1 - 0.05) = 0.8075 > 0.8, risk is LOW
                assertEquals(0.8075, result.adjusted(), 1e-9);
                assertEquals("LOW", result.riskLevel());
            }

            @Test
            void givenExactLowRiskBoundary_whenEvaluated_thenLowRisk() {
                // Given: ILI confidence and factor produce adjusted exactly above 0.8
                double iliConfidence = 0.9;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.1));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.9 * 0.9 = 0.81 > 0.8, risk is LOW
                assertEquals(0.81, result.adjusted(), 1e-9);
                assertEquals("LOW", result.riskLevel());
            }
        }

        @Nested
        class GivenModerateClimateSensitivity_whenEvaluated_thenMediumRisk {

            @Test
            void givenMediumConfidenceAndZeroFactor_whenEvaluated_thenMediumRisk() {
                // Given: moderate ILI confidence with zero climate modifier
                double iliConfidence = 0.65;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.0));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.65, which is in [0.5, 0.8], risk is MEDIUM
                assertEquals(0.65, result.adjusted(), 1e-9);
                assertEquals("MEDIUM", result.riskLevel());
            }

            @Test
            void givenHighConfidenceAndLargeFactor_whenEvaluated_thenMediumRisk() {
                // Given: high confidence but significant climate sensitivity
                double iliConfidence = 0.9;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.3));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.9 * 0.7 = 0.63, risk is MEDIUM
                assertEquals(0.63, result.adjusted(), 1e-9);
                assertEquals("MEDIUM", result.riskLevel());
            }

            @Test
            void givenExactMediumUpperBoundary_whenEvaluated_thenMediumRisk() {
                // Given: adjusted value exactly at 0.8 upper boundary of MEDIUM
                double iliConfidence = 0.8;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.0));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.8 is NOT > 0.8, falls into MEDIUM
                assertEquals(0.8, result.adjusted(), 1e-9);
                assertEquals("MEDIUM", result.riskLevel());
            }

            @Test
            void givenExactMediumLowerBoundary_whenEvaluated_thenMediumRisk() {
                // Given: adjusted value exactly at 0.5 lower boundary of MEDIUM
                double iliConfidence = 0.5;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.0));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.5 >= 0.5, still MEDIUM
                assertEquals(0.5, result.adjusted(), 1e-9);
                assertEquals("MEDIUM", result.riskLevel());
            }
        }

        @Nested
        class GivenHighClimateSensitivity_whenEvaluated_thenHighRisk {

            @Test
            void givenLowConfidenceAndZeroFactor_whenEvaluated_thenHighRisk() {
                // Given: low ILI confidence with zero climate modifier
                double iliConfidence = 0.3;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.0));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.3 < 0.5, risk is HIGH
                assertEquals(0.3, result.adjusted(), 1e-9);
                assertEquals("HIGH", result.riskLevel());
            }

            @Test
            void givenModerateConfidenceAndLargeFactor_whenEvaluated_thenHighRisk() {
                // Given: moderate confidence but very high climate sensitivity
                double iliConfidence = 0.6;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.5));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.6 * 0.5 = 0.3 < 0.5, risk is HIGH
                assertEquals(0.3, result.adjusted(), 1e-9);
                assertEquals("HIGH", result.riskLevel());
            }

            @Test
            void givenZeroConfidence_whenEvaluated_thenHighRisk() {
                // Given: zero ILI confidence
                double iliConfidence = 0.0;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.0));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.0, risk is HIGH
                assertEquals(0.0, result.adjusted(), 1e-9);
                assertEquals("HIGH", result.riskLevel());
            }

            @Test
            void givenHighConfidenceAndExtremeFactor_whenEvaluated_thenHighRisk() {
                // Given: high confidence but near-total climate modifier
                double iliConfidence = 0.9;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.95));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.9 * 0.05 = 0.045, risk is HIGH
                assertEquals(0.045, result.adjusted(), 1e-9);
                assertEquals("HIGH", result.riskLevel());
            }
        }

        @Nested
        class GivenBoundaryValues_whenEvaluated_thenCorrectClassification {

            @Test
            void givenJustBelowHighRiskThreshold_whenEvaluated_thenHighRisk() {
                // Given: adjusted value just below 0.5
                double iliConfidence = 0.51;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.02));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.51 * 0.98 = 0.4998 < 0.5, risk is HIGH
                assertEquals(0.4998, result.adjusted(), 1e-9);
                assertEquals("HIGH", result.riskLevel());
            }

            @Test
            void givenJustAboveMediumBoundary_whenEvaluated_thenMediumRisk() {
                // Given: adjusted value just above 0.5
                double iliConfidence = 0.55;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.08));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 0.55 * 0.92 = 0.506 >= 0.5, risk is MEDIUM
                assertEquals(0.506, result.adjusted(), 1e-9);
                assertEquals("MEDIUM", result.riskLevel());
            }

            @Test
            void givenPerfectConfidenceAndFullFactor_whenEvaluated_thenZeroAdjusted() {
                // Given: perfect confidence but factor of 1.0 eliminates it entirely
                double iliConfidence = 1.0;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(1.0));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: adjusted = 1.0 * 0.0 = 0.0, risk is HIGH
                assertEquals(0.0, result.adjusted(), 1e-9);
                assertEquals("HIGH", result.riskLevel());
                assertEquals(1.0, result.modifier(), 1e-9);
            }
        }

        @Nested
        class GivenResultFields_whenEvaluated_thenAllPopulated {

            @Test
            void givenAnyInput_whenEvaluated_thenAllFieldsSet() {
                // Given: valid inputs for evaluation
                double iliConfidence = 0.7;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.2));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: all fields in the ConfidenceAdjustment are populated
                assertNotNull(result);
                assertEquals(0.7, result.original(), 1e-9);
                assertEquals(0.2, result.modifier(), 1e-9);
                assertEquals(0.56, result.adjusted(), 1e-9);
                assertNotNull(result.riskLevel());
            }

            @Test
            void givenConfidence_whenEvaluated_thenOriginalPreserved() {
                // Given: a specific confidence value
                double iliConfidence = 0.42;
                when(climateSensitivityFactor.adjustThresholds(anyDouble(), anyDouble(), anyDouble()))
                        .thenReturn(adjustmentWithFactor(0.1));

                // When: evaluating climate risk
                var result = guard.evaluate(iliConfidence, 1.0, 2.0, 3.0);

                // Then: original field preserves the input confidence unchanged
                assertEquals(0.42, result.original(), 1e-9);
                assertEquals(0.378, result.adjusted(), 1e-9);
            }
        }

        @Nested
        class GivenDifferentMarketParameters_whenEvaluated_thenFactorRetrieved {

            @Test
            void givenDifferentRrpSpreadVol_whenEvaluated_thenClimateFactorConsulted() {
                // Given: specific market parameters that should be forwarded
                when(climateSensitivityFactor.adjustThresholds(eq(5.25), eq(1.5), eq(12.0)))
                        .thenReturn(adjustmentWithFactor(0.15));

                // When: evaluating with explicit market data
                var result = guard.evaluate(0.9, 5.25, 1.5, 12.0);

                // Then: the climate factor is applied correctly
                assertEquals(0.9 * 0.85, result.adjusted(), 1e-9);
                assertEquals("MEDIUM", result.riskLevel());
            }
        }
    }
}
