package com.tickonomics.computation.backtest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CrossModelValidatorTest {

    private CrossModelValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CrossModelValidator();
    }

    private Map<String, List<Double>> buildReturns(
            List<String> models, double[] bases, int size) {
        Map<String, List<Double>> returns = new HashMap<>();
        for (int m = 0; m < models.size(); m++) {
            List<Double> modelReturns = new java.util.ArrayList<>();
            for (int i = 0; i < size; i++) {
                modelReturns.add(bases[m] + 0.01 * (i % 5 - 2));
            }
            returns.put(models.get(m), modelReturns);
        }
        return returns;
    }

    @Nested
    class RunTournament {

        @Test
        void givenMultipleModels_whenRunTournament_thenRanksBySharpe() {
            // Given 3 models with different return profiles
            List<String> models = List.of("momentum", "mean_reversion", "buy_hold");
            Map<String, List<Double>> returns = new HashMap<>();
            returns.put("momentum", List.of(0.02, 0.03, -0.01, 0.04, 0.01, 0.02, 0.03, 0.01, -0.005, 0.025));
            returns.put("mean_reversion", List.of(0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005, 0.005));
            returns.put("buy_hold", List.of(0.01, -0.01, 0.02, -0.02, 0.01, -0.01, 0.02, -0.02, 0.01, -0.01));

            // When running tournament
            CrossModelValidator.TournamentResult result = validator.runTournament(models, returns, List.of("BULL"));

            // Then models are ranked by Sharpe ratio
            assertEquals(3, result.rankings().size());
            assertEquals("momentum", result.winner());
            assertEquals("BULL", result.regimeType());
            assertEquals(result.rankings().get(0).modelName(), result.winner());
        }

        @Test
        void givenEmptyModels_whenRunTournament_thenReturnNone() {
            // Given empty model list
            // When running tournament
            CrossModelValidator.TournamentResult result = validator.runTournament(
                    List.of(), Map.of(), List.of());

            // Then no winner
            assertEquals("NONE", result.winner());
            assertTrue(result.rankings().isEmpty());
        }

        @Test
        void givenNullModels_whenRunTournament_thenReturnNone() {
            // Given null model list
            // When running tournament
            CrossModelValidator.TournamentResult result = validator.runTournament(null, Map.of(), List.of());

            // Then no winner
            assertEquals("NONE", result.winner());
        }

        @Test
        void givenModels_whenRunTournament_thenEachPerformanceHasValidMetrics() {
            // Given 2 models
            List<String> models = List.of("alpha", "beta");
            Map<String, List<Double>> returns = new HashMap<>();
            returns.put("alpha", List.of(0.01, 0.02, 0.015, -0.005, 0.01));
            returns.put("beta", List.of(-0.01, -0.02, 0.03, -0.01, 0.005));

            // When running tournament
            CrossModelValidator.TournamentResult result = validator.runTournament(models, returns, List.of("NEUTRAL"));

            // Then each model has computed metrics
            for (CrossModelValidator.ModelPerformance perf : result.rankings()) {
                assertNotNull(perf.modelName());
                assertFalse(Double.isNaN(perf.sharpe()));
                assertTrue(perf.hitRate() >= 0.0 && perf.hitRate() <= 1.0);
                assertTrue(perf.maxDrawdown() >= 0.0);
            }
        }
    }

    @Nested
    class RunRegimeSpecific {

        @Test
        void givenTargetRegime_whenRunRegimeSpecific_thenFilterToRegime() {
            // Given regime labels and model returns
            List<String> models = List.of("model_a", "model_b");
            Map<String, List<Double>> returns = new HashMap<>();
            returns.put("model_a", List.of(0.01, 0.02, -0.01, 0.03, -0.02, 0.01));
            returns.put("model_b", List.of(0.005, 0.005, 0.005, 0.005, 0.005, 0.005));
            List<String> regimes = List.of("BULL", "BULL", "BEAR", "BULL", "BEAR", "BEAR");

            // When running regime-specific for BULL
            CrossModelValidator.TournamentResult result = validator.runRegimeSpecific(
                    models, returns, regimes, "BULL");

            // Then result targets BULL regime
            assertEquals("BULL", result.regimeType());
            assertFalse(result.rankings().isEmpty());
        }

        @Test
        void givenNonExistentRegime_whenRunRegimeSpecific_thenReturnNone() {
            // Given regime labels without target
            List<String> models = List.of("model_a");
            Map<String, List<Double>> returns = Map.of("model_a", List.of(0.01, 0.02));
            List<String> regimes = List.of("BULL", "BULL");

            // When running for non-existent regime
            CrossModelValidator.TournamentResult result = validator.runRegimeSpecific(
                    models, returns, regimes, "CRISIS");

            // Then no winner
            assertEquals("CRISIS", result.regimeType());
            assertEquals("NONE", result.winner());
        }

        @Test
        void givenNullRegimeLabels_whenRunRegimeSpecific_thenReturnNone() {
            // Given null regime labels
            List<String> models = List.of("model_a");
            Map<String, List<Double>> returns = Map.of("model_a", List.of(0.01));

            // When running regime-specific with null labels
            CrossModelValidator.TournamentResult result = validator.runRegimeSpecific(
                    models, returns, null, "BULL");

            // Then returns NONE
            assertEquals("NONE", result.winner());
        }
    }

    @Nested
    class ComputePerformance {

        @Test
        void givenEmptyReturns_whenComputePerformance_thenReturnZeros() {
            // Given empty returns
            CrossModelValidator.ModelPerformance perf = validator.computePerformance("test", List.of());

            // Then all metrics are zero
            assertEquals("test", perf.modelName());
            assertEquals(0.0, perf.sharpe());
            assertEquals(0.0, perf.hitRate());
            assertEquals(0.0, perf.maxDrawdown());
            assertEquals(0.0, perf.calmarRatio());
        }

        @Test
        void givenNullReturns_whenComputePerformance_thenReturnZeros() {
            // Given null returns
            CrossModelValidator.ModelPerformance perf = validator.computePerformance("test", null);

            // Then all metrics are zero
            assertEquals(0.0, perf.sharpe());
        }
    }

    @Nested
    class ComputeMetrics {

        @Test
        void givenPositiveReturns_whenComputeSharpe_thenPositive() {
            // Given all positive returns
            double sharpe = validator.computeSharpe(List.of(0.01, 0.02, 0.015, 0.005, 0.01));

            // Then Sharpe is positive
            assertTrue(sharpe > 0.0);
        }

        @Test
        void givenAllNegativeReturns_whenComputeSharpe_thenNegative() {
            // Given all negative returns
            double sharpe = validator.computeSharpe(List.of(-0.01, -0.02, -0.015, -0.005, -0.01));

            // Then Sharpe is negative
            assertTrue(sharpe < 0.0);
        }

        @Test
        void givenMixedReturns_whenComputeHitRate_thenCorrectRatio() {
            // Given 3 positive out of 5 returns
            double hitRate = validator.computeHitRate(List.of(0.01, -0.01, 0.02, -0.02, 0.005));

            // Then hit rate is 0.6
            assertEquals(0.6, hitRate, 0.001);
        }

        @Test
        void givenDrawdownSeries_whenComputeMaxDrawdown_thenCorrectValue() {
            // Given returns: +1, -0.5, +0.2, -0.8 (cumulative: 1, 0.5, 0.7, -0.1)
            double maxDd = validator.computeMaxDrawdown(List.of(1.0, -0.5, 0.2, -0.8));

            // Then max drawdown is 1.1 (from peak 1.0 to trough -0.1)
            assertEquals(1.1, maxDd, 0.001);
        }
    }

    @Nested
    class DetermineDominantRegime {

        @Test
        void givenRegimeLabels_whenDetermineDominant_thenReturnMostFrequent() {
            // Given regime labels with BULL dominant
            List<String> labels = List.of("BULL", "BEAR", "BULL", "BULL", "NEUTRAL");

            // When determining dominant regime
            String dominant = validator.determineDominantRegime(labels);

            // Then BULL is dominant
            assertEquals("BULL", dominant);
        }

        @Test
        void givenEmptyLabels_whenDetermineDominant_thenReturnUnknown() {
            // Given empty labels
            String dominant = validator.determineDominantRegime(List.of());

            // Then UNKNOWN
            assertEquals("UNKNOWN", dominant);
        }

        @Test
        void givenNullLabels_whenDetermineDominant_thenReturnUnknown() {
            // Given null labels
            String dominant = validator.determineDominantRegime(null);

            // Then UNKNOWN
            assertEquals("UNKNOWN", dominant);
        }
    }
}
