package com.tickonomics.computation.optimization;

import com.tickonomics.computation.ili.WeightedWeightStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class BayesianWeightOptimizer {

    private static final Logger log = LoggerFactory.getLogger(BayesianWeightOptimizer.class);
    private static final int MAX_ITERATIONS = 100;
    private static final double CONVERGENCE_THRESHOLD = 0.001;

    private final WeightedWeightStore weightStore;

    public BayesianWeightOptimizer(WeightedWeightStore weightStore) {
        this.weightStore = weightStore;
    }

    public OptimizationResult optimize(OptimizationProfile profile, double[] sharpeHistory) {
        if (sharpeHistory == null || sharpeHistory.length < 10) {
            return new OptimizationResult(weightStore.getCalibratedWeights(), 0.0, "INSUFFICIENT_DATA", 0);
        }

        double[] candidateWeights = weightStore.getCalibratedWeights();
        double bestSharpe = computeFitness(sharpeHistory);
        int iterations = 0;
        boolean converged = false;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            iterations++;
            double[] proposed = proposeUpdate(candidateWeights, profile);

            double[] deltas = new double[proposed.length];
            for (int j = 0; j < proposed.length; j++) {
                deltas[j] = proposed[j] - candidateWeights[j];
            }

            double[] savedWeights = weightStore.getCalibratedWeights();
            weightStore.applyDelta(deltas);
            double[] newWeights = weightStore.getCalibratedWeights();

            double weightChange = maxAbsoluteChange(candidateWeights, newWeights);
            if (weightChange < CONVERGENCE_THRESHOLD) {
                converged = true;
                break;
            }

            candidateWeights = newWeights;
        }

        String status = converged ? "CONVERGED" : "MAX_ITERATIONS";
        log.info("Optimization completed: status={}, iterations={}, profile={}", status, iterations, profile.name());

        return new OptimizationResult(weightStore.getCalibratedWeights(), bestSharpe, status, iterations);
    }

    double[] proposeUpdate(double[] current, OptimizationProfile profile) {
        double[] proposed = new double[current.length];
        double sum = 0;
        for (int i = 0; i < current.length; i++) {
            double perturbation = (Math.random() - 0.5) * profile.learningRate();
            proposed[i] = Math.max(0.01, current[i] + perturbation);
            sum += proposed[i];
        }
        for (int i = 0; i < proposed.length; i++) {
            proposed[i] /= sum;
        }
        return proposed;
    }

    double computeFitness(double[] sharpeHistory) {
        double sum = 0;
        int count = Math.min(sharpeHistory.length, 30);
        for (int i = sharpeHistory.length - count; i < sharpeHistory.length; i++) {
            sum += sharpeHistory[i];
        }
        return sum / count;
    }

    double maxAbsoluteChange(double[] before, double[] after) {
        double maxChange = 0;
        for (int i = 0; i < before.length; i++) {
            maxChange = Math.max(maxChange, Math.abs(before[i] - after[i]));
        }
        return maxChange;
    }

    public record OptimizationResult(double[] weights, double fitness, String status, int iterations) {
        @Override
        public String toString() {
            return "OptimizationResult{status=" + status + ", iterations=" + iterations +
                    ", fitness=" + fitness + ", weights=" + Arrays.toString(weights) + "}";
        }
    }
}
