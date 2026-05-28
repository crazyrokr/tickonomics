package com.tickonomics.computation.backtest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CrossModelValidator {

    private static final Logger log = LoggerFactory.getLogger(CrossModelValidator.class);
    private static final double ANNUALIZATION_FACTOR = Math.sqrt(252);

    public record ModelPerformance(
            String modelName,
            double sharpe,
            double hitRate,
            double maxDrawdown,
            double calmarRatio) {}

    public record TournamentResult(
            List<ModelPerformance> rankings,
            String winner,
            String regimeType) {}

    public CrossModelValidator() {}

    public TournamentResult runTournament(
            List<String> modelNames,
            Map<String, List<Double>> allReturns,
            List<String> regimeLabels) {

        if (modelNames == null || modelNames.isEmpty()) {
            log.warn("No models provided for tournament");
            return new TournamentResult(List.of(), "NONE", "UNKNOWN");
        }

        String dominantRegime = determineDominantRegime(regimeLabels);

        List<ModelPerformance> performances = new ArrayList<>();
        for (String model : modelNames) {
            List<Double> returns = allReturns.getOrDefault(model, List.of());
            performances.add(computePerformance(model, returns));
        }

        List<ModelPerformance> ranked = performances.stream()
                .sorted(Comparator.comparingDouble(ModelPerformance::sharpe).reversed())
                .collect(Collectors.toList());

        String winner = ranked.isEmpty() ? "NONE" : ranked.getFirst().modelName();

        log.info("Tournament complete: winner={}, models={}, regime={}",
                winner, ranked.size(), dominantRegime);

        return new TournamentResult(ranked, winner, dominantRegime);
    }

    public TournamentResult runRegimeSpecific(
            List<String> modelNames,
            Map<String, List<Double>> allReturns,
            List<String> regimeLabels,
            String targetRegime) {

        if (targetRegime == null || regimeLabels == null) {
            log.warn("Null regime labels or target regime for regime-specific tournament");
            return new TournamentResult(List.of(), "NONE", targetRegime);
        }

        List<Integer> regimeIndices = new ArrayList<>();
        for (int i = 0; i < regimeLabels.size(); i++) {
            if (targetRegime.equals(regimeLabels.get(i))) {
                regimeIndices.add(i);
            }
        }

        if (regimeIndices.isEmpty()) {
            log.warn("No data points found for regime: {}", targetRegime);
            return new TournamentResult(List.of(), "NONE", targetRegime);
        }

        Map<String, List<Double>> filteredReturns = new LinkedHashMap<>();
        for (String model : modelNames) {
            List<Double> modelReturns = allReturns.getOrDefault(model, List.of());
            List<Double> regimeReturns = new ArrayList<>();
            for (int idx : regimeIndices) {
                if (idx < modelReturns.size()) {
                    regimeReturns.add(modelReturns.get(idx));
                }
            }
            filteredReturns.put(model, regimeReturns);
        }

        TournamentResult result = runTournament(modelNames, filteredReturns, List.of());
        return new TournamentResult(result.rankings(), result.winner(), targetRegime);
    }

    ModelPerformance computePerformance(String modelName, List<Double> returns) {
        if (returns == null || returns.size() < 2) {
            return new ModelPerformance(modelName, 0.0, 0.0, 0.0, 0.0);
        }

        double sharpe = computeSharpe(returns);
        double hitRate = computeHitRate(returns);
        double maxDrawdown = computeMaxDrawdown(returns);
        double calmarRatio = maxDrawdown > 1e-12 ? sharpe / maxDrawdown : 0.0;

        return new ModelPerformance(modelName, sharpe, hitRate, maxDrawdown, calmarRatio);
    }

    double computeSharpe(List<Double> returns) {
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(r -> (r - mean) * (r - mean))
                .average().orElse(0.0);
        double std = Math.sqrt(variance);

        if (std < 1e-12) {
            return 0.0;
        }
        return (mean / std) * ANNUALIZATION_FACTOR;
    }

    double computeHitRate(List<Double> returns) {
        long positive = returns.stream().filter(r -> r > 0).count();
        return (double) positive / returns.size();
    }

    double computeMaxDrawdown(List<Double> returns) {
        double peak = 0.0;
        double maxDd = 0.0;
        double cumulative = 0.0;

        for (double r : returns) {
            cumulative += r;
            if (cumulative > peak) {
                peak = cumulative;
            }
            double drawdown = peak - cumulative;
            if (drawdown > maxDd) {
                maxDd = drawdown;
            }
        }
        return maxDd;
    }

    String determineDominantRegime(List<String> regimeLabels) {
        if (regimeLabels == null || regimeLabels.isEmpty()) {
            return "UNKNOWN";
        }

        Map<String, Long> counts = new HashMap<>();
        for (String label : regimeLabels) {
            counts.merge(label, 1L, Long::sum);
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }
}
