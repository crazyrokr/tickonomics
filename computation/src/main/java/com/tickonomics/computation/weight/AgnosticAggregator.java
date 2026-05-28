package com.tickonomics.computation.weight;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgnosticAggregator {

    private static final Logger log = LoggerFactory.getLogger(AgnosticAggregator.class);
    private static final double DEFAULT_LEARNING_RATE = 0.1;

    public record AggregationResult(
            Map<String, Double> weights,
            Map<String, Double> regrets,
            String dominantRegime,
            double totalRegret) {}

    public AgnosticAggregator() {}

    public AggregationResult aggregate(
            List<Map<String, Double>> weightSets,
            List<Double> returns,
            double learningRate) {

        if (weightSets == null || weightSets.isEmpty() || returns == null || returns.isEmpty()) {
            log.warn("Insufficient data for aggregation: weightSets={}, returns={}",
                    weightSets == null ? 0 : weightSets.size(),
                    returns == null ? 0 : returns.size());
            return new AggregationResult(Map.of(), Map.of(), "UNKNOWN", 0.0);
        }

        Map<String, Double> currentWeights = initializeWeights(weightSets);
        Map<String, Double> cumulativeRegrets = new LinkedHashMap<>();

        for (String model : currentWeights.keySet()) {
            cumulativeRegrets.put(model, 0.0);
        }

        int steps = Math.min(returns.size(), weightSets.size());

        for (int t = 0; t < steps; t++) {
            double realizedReturn = returns.get(t);
            Map<String, Double> modelWeights = weightSets.get(t);

            double bestReturn = modelWeights.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .max().orElse(0.0);

            for (Map.Entry<String, Double> entry : currentWeights.entrySet()) {
                String model = entry.getKey();
                double modelReturn = modelWeights.getOrDefault(model, 0.0);
                double regret = Math.max(0.0, bestReturn - modelReturn);
                cumulativeRegrets.merge(model, regret, Double::sum);

                double updatedWeight = entry.getValue() * Math.exp(-learningRate * regret);
                currentWeights.put(model, updatedWeight);
            }

            normalizeWeights(currentWeights);
        }

        String dominantRegime = findDominant(currentWeights);
        double totalRegret = cumulativeRegrets.values().stream()
                .mapToDouble(Double::doubleValue).sum();

        log.info("Aggregation complete: models={}, dominant={}, totalRegret={:.4f}",
                currentWeights.size(), dominantRegime, totalRegret);

        return new AggregationResult(currentWeights, cumulativeRegrets, dominantRegime, totalRegret);
    }

    public AggregationResult aggregate(
            List<Map<String, Double>> weightSets,
            List<Double> returns) {
        return aggregate(weightSets, returns, DEFAULT_LEARNING_RATE);
    }

    Map<String, Double> initializeWeights(List<Map<String, Double>> weightSets) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map<String, Double> ws : weightSets) {
            for (String model : ws.keySet()) {
                weights.putIfAbsent(model, 1.0);
            }
        }
        normalizeWeights(weights);
        return weights;
    }

    void normalizeWeights(Map<String, Double> weights) {
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum < 1e-12) {
            double uniform = 1.0 / weights.size();
            weights.replaceAll((k, v) -> uniform);
            return;
        }
        weights.replaceAll((k, v) -> v / sum);
    }

    String findDominant(Map<String, Double> weights) {
        return weights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }
}
