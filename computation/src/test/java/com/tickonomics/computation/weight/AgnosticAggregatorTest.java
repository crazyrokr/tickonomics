package com.tickonomics.computation.weight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AgnosticAggregatorTest {

    private AgnosticAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new AgnosticAggregator();
    }

    private List<Map<String, Double>> buildWeightSets(int steps, String[] models) {
        List<Map<String, Double>> weightSets = new ArrayList<>();
        for (int t = 0; t < steps; t++) {
            Map<String, Double> ws = new LinkedHashMap<>();
            for (int m = 0; m < models.length; m++) {
                ws.put(models[m], 0.01 * (m + 1) + 0.001 * t);
            }
            weightSets.add(ws);
        }
        return weightSets;
    }

    @Nested
    class Aggregate {

        @Test
        void givenValidInputs_whenAggregate_thenReturnNormalizedWeights() {
            // Given 10 steps with 3 models
            String[] models = {"momentum", "mean_rev", "value"};
            List<Map<String, Double>> weightSets = buildWeightSets(10, models);
            List<Double> returns = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                returns.add(0.001 * (i + 1));
            }

            // When aggregating
            AgnosticAggregator.AggregationResult result = aggregator.aggregate(weightSets, returns, 0.1);

            // Then weights sum to ~1.0 and all models present
            assertEquals(3, result.weights().size());
            double weightSum = result.weights().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, weightSum, 0.001);
            assertTrue(result.totalRegret() >= 0.0);
            assertNotNull(result.dominantRegime());
        }

        @Test
        void givenBestModel_whenAggregate_thenDominantIsBestModel() {
            // Given one model consistently outperforms
            List<Map<String, Double>> weightSets = new ArrayList<>();
            List<Double> returns = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                Map<String, Double> ws = new LinkedHashMap<>();
                ws.put("good_model", 0.05);
                ws.put("bad_model", -0.01);
                weightSets.add(ws);
                returns.add(0.02);
            }

            // When aggregating
            AgnosticAggregator.AggregationResult result = aggregator.aggregate(weightSets, returns, 0.1);

            // Then good_model has higher weight
            double goodWeight = result.weights().get("good_model");
            double badWeight = result.weights().get("bad_model");
            assertTrue(goodWeight > badWeight, "Good model should have higher weight");
            assertEquals("good_model", result.dominantRegime());
        }

        @Test
        void givenEmptyInputs_whenAggregate_thenReturnEmptyResult() {
            // Given empty inputs
            // When aggregating
            AgnosticAggregator.AggregationResult result = aggregator.aggregate(List.of(), List.of(), 0.1);

            // Then empty result returned
            assertTrue(result.weights().isEmpty());
            assertEquals("UNKNOWN", result.dominantRegime());
            assertEquals(0.0, result.totalRegret());
        }

        @Test
        void givenNullInputs_whenAggregate_thenReturnEmptyResult() {
            // Given null inputs
            AgnosticAggregator.AggregationResult result = aggregator.aggregate(null, null, 0.1);

            // Then empty result
            assertTrue(result.weights().isEmpty());
        }

        @Test
        void givenDefaultLearningRate_whenAggregate_thenReturnResult() {
            // Given weight sets and returns
            String[] models = {"a", "b"};
            List<Map<String, Double>> weightSets = buildWeightSets(5, models);
            List<Double> returns = List.of(0.01, 0.02, 0.015, 0.005, 0.01);

            // When aggregating with default learning rate
            AgnosticAggregator.AggregationResult result = aggregator.aggregate(weightSets, returns);

            // Then uses default lr=0.1
            assertFalse(result.weights().isEmpty());
        }
    }

    @Nested
    class InitializeWeights {

        @Test
        void givenWeightSets_whenInitialize_thenUniformWeights() {
            // Given weight sets with 3 models
            Map<String, Double> ws1 = Map.of("m1", 0.5, "m2", 0.3, "m3", 0.2);
            Map<String, Double> ws2 = Map.of("m1", 0.4, "m2", 0.6);

            // When initializing
            Map<String, Double> weights = aggregator.initializeWeights(List.of(ws1, ws2));

            // Then all models present with uniform weight
            assertEquals(3, weights.size());
            assertTrue(weights.containsKey("m1"));
            assertTrue(weights.containsKey("m2"));
            assertTrue(weights.containsKey("m3"));
            double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.001);
        }
    }

    @Nested
    class NormalizeWeights {

        @Test
        void givenUnnormalizedWeights_whenNormalize_thenSumToOne() {
            // Given unnormalized weights
            Map<String, Double> weights = new LinkedHashMap<>();
            weights.put("a", 3.0);
            weights.put("b", 1.0);

            // When normalizing
            aggregator.normalizeWeights(weights);

            // Then sum is 1.0
            double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.001);
            assertEquals(0.75, weights.get("a"), 0.001);
            assertEquals(0.25, weights.get("b"), 0.001);
        }

        @Test
        void givenAllZeroWeights_whenNormalize_thenUniform() {
            // Given all zero weights
            Map<String, Double> weights = new LinkedHashMap<>();
            weights.put("a", 0.0);
            weights.put("b", 0.0);

            // When normalizing
            aggregator.normalizeWeights(weights);

            // Then uniform distribution
            assertEquals(0.5, weights.get("a"), 0.001);
            assertEquals(0.5, weights.get("b"), 0.001);
        }
    }

    @Nested
    class FindDominant {

        @Test
        void givenWeightedModels_whenFindDominant_thenReturnHighestWeight() {
            // Given weights with clear dominant
            Map<String, Double> weights = Map.of("a", 0.5, "b", 0.3, "c", 0.2);

            // When finding dominant
            String dominant = aggregator.findDominant(weights);

            // Then returns highest weight model
            assertEquals("a", dominant);
        }

        @Test
        void givenEmptyWeights_whenFindDominant_thenReturnUnknown() {
            // Given empty weights
            Map<String, Double> weights = Map.of();

            // When finding dominant
            String dominant = aggregator.findDominant(weights);

            // Then UNKNOWN
            assertEquals("UNKNOWN", dominant);
        }
    }

    @Nested
    class RegretTracking {

        @Test
        void givenVaryingPerformance_whenAggregate_thenRegretsReflectDifferences() {
            // Given 2 models where one always underperforms
            List<Map<String, Double>> weightSets = new ArrayList<>();
            List<Double> returns = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                Map<String, Double> ws = new LinkedHashMap<>();
                ws.put("strong", 0.10);
                ws.put("weak", 0.01);
                weightSets.add(ws);
                returns.add(0.05);
            }

            // When aggregating
            AgnosticAggregator.AggregationResult result = aggregator.aggregate(weightSets, returns, 0.1);

            // Then weak model accumulates more regret
            Double weakRegret = result.regrets().get("weak");
            Double strongRegret = result.regrets().get("strong");
            assertNotNull(weakRegret);
            assertNotNull(strongRegret);
            assertTrue(weakRegret > strongRegret, "Weak model should have higher cumulative regret");
        }
    }
}
