package com.tickonomics.computation.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RiskPremiumResidualMonitorTest {

    private RiskPremiumResidualMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new RiskPremiumResidualMonitor();
    }

    private List<Double> generateResiduals(int count, double mean, double std, long seed) {
        Random rng = new Random(seed);
        List<Double> residuals = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            residuals.add(mean + std * rng.nextGaussian());
        }
        return residuals;
    }

    @Nested
    class ComputeStd {

        @Test
        void givenVaryingValues_whenComputeStd_thenReturnCorrectStd() {
            // Given values with known std
            List<Double> values = List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);

            // When computing std
            double std = monitor.computeStd(values);

            // Then returns population std
            double mean = 5.0;
            double expectedVar = ((9 + 1 + 1 + 1 + 0 + 0 + 4 + 16) / 8.0);
            double expectedStd = Math.sqrt(expectedVar);
            assertEquals(expectedStd, std, 0.001);
        }

        @Test
        void givenConstantValues_whenComputeStd_thenReturnZero() {
            // Given all same values
            List<Double> values = List.of(3.0, 3.0, 3.0, 3.0);

            // When computing std
            double std = monitor.computeStd(values);

            // Then zero std
            assertEquals(0.0, std, 0.001);
        }
    }

    @Nested
    class ClassifyDislocation {

        @Test
        void givenNotDislocated_whenClassify_thenReturnNormal() {
            // Given not dislocated
            // When classifying
            String result = monitor.classifyDislocation(0.01, false);

            // Then NORMAL
            assertEquals("NORMAL", result);
        }

        @Test
        void givenPositiveResidualAndDislocated_whenClassify_thenReturnPremiumDislocated() {
            // Given positive residual, dislocated
            // When classifying
            String result = monitor.classifyDislocation(0.5, true);

            // Then PREMIUM_DISLOCATED
            assertEquals("PREMIUM_DISLOCATED", result);
        }

        @Test
        void givenNegativeResidualAndDislocated_whenClassify_thenReturnDiscountDislocated() {
            // Given negative residual, dislocated
            // When classifying
            String result = monitor.classifyDislocation(-0.5, true);

            // Then DISCOUNT_DISLOCATED
            assertEquals("DISCOUNT_DISLOCATED", result);
        }

        @Test
        void givenZeroResidualAndDislocated_whenClassify_thenReturnDiscountDislocated() {
            // Given zero residual, dislocated (edge: residual=0 is NOT >0, so falls to discount branch)
            // When classifying
            String result = monitor.classifyDislocation(0.0, true);

            // Then DISCOUNT_DISLOCATED (residual > 0 is false)
            assertEquals("DISCOUNT_DISLOCATED", result);
        }
    }

    @Nested
    class Monitor {

        @Test
        void givenNormalResidual_whenMonitor_thenReturnNormal() {
            // Given observed close to fair value with tight historical residuals
            List<Double> historical = generateResiduals(100, 0.0, 0.01, 42L);

            // When monitoring with small residual
            RiskPremiumResidualMonitor.ResidualResult result =
                    monitor.monitor(5.02, 5.01, historical);

            // Then status is NORMAL
            assertFalse(result.dislocated());
            assertEquals("NORMAL", result.status());
            assertEquals(0.01, result.residual(), 0.001);
        }

        @Test
        void givenLargePositiveResidual_whenMonitor_thenReturnPremiumDislocated() {
            // Given observed well above fair value
            List<Double> historical = generateResiduals(100, 0.0, 0.01, 42L);

            // When monitoring with large positive residual
            RiskPremiumResidualMonitor.ResidualResult result =
                    monitor.monitor(5.10, 5.01, historical);

            // Then PREMIUM_DISLOCATED
            assertTrue(result.dislocated());
            assertEquals("PREMIUM_DISLOCATED", result.status());
            assertTrue(result.residual() > 0);
        }

        @Test
        void givenLargeNegativeResidual_whenMonitor_thenReturnDiscountDislocated() {
            // Given observed well below fair value
            List<Double> historical = generateResiduals(100, 0.0, 0.01, 42L);

            // When monitoring with large negative residual
            RiskPremiumResidualMonitor.ResidualResult result =
                    monitor.monitor(4.90, 5.01, historical);

            // Then DISCOUNT_DISLOCATED
            assertTrue(result.dislocated());
            assertEquals("DISCOUNT_DISLOCATED", result.status());
            assertTrue(result.residual() < 0);
        }

        @Test
        void givenInsufficientHistorical_whenMonitor_thenReturnInsufficientData() {
            // Given too few historical residuals
            // When monitoring
            RiskPremiumResidualMonitor.ResidualResult result =
                    monitor.monitor(5.0, 4.5, List.of(0.01));

            // Then INSUFFICIENT_DATA
            assertFalse(result.dislocated());
            assertEquals("INSUFFICIENT_DATA", result.status());
        }

        @Test
        void givenNullHistorical_whenMonitor_thenReturnInsufficientData() {
            // Given null historical residuals
            // When monitoring
            RiskPremiumResidualMonitor.ResidualResult result =
                    monitor.monitor(5.0, 4.5, null);

            // Then INSUFFICIENT_DATA
            assertEquals("INSUFFICIENT_DATA", result.status());
        }

        @Test
        void givenCustomThreshold_whenMonitor_thenUsesCustomThreshold() {
            // Given a wider threshold that avoids dislocation
            List<Double> historical = generateResiduals(100, 0.0, 0.01, 42L);

            // When monitoring with large threshold
            RiskPremiumResidualMonitor.ResidualResult result =
                    monitor.monitor(5.10, 5.01, historical, 10.0);

            // Then NORMAL because threshold is very wide
            assertFalse(result.dislocated());
            assertEquals("NORMAL", result.status());
        }

        @Test
        void givenMonitoringResult_whenCheckFields_thenAllFieldsPopulated() {
            // Given valid inputs
            List<Double> historical = generateResiduals(50, 0.0, 0.01, 42L);

            // When monitoring
            RiskPremiumResidualMonitor.ResidualResult result =
                    monitor.monitor(5.02, 5.01, historical);

            // Then all fields populated
            assertEquals(5.02, result.observedYield(), 0.001);
            assertEquals(5.01, result.fairValueYield(), 0.001);
            assertEquals(0.01, result.residual(), 0.001);
            assertTrue(result.residualStd() > 0.0);
        }
    }
}
