package com.tickonomics.computation.optimization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Population-based metaheuristic optimizer using the firefly algorithm.
 * Each firefly represents a candidate weight set. Fitness is measured by
 * the Sharpe ratio computed against historical returns.
 */
@Service
public class FireflyWeightOptimizer {

    private static final Logger log = LoggerFactory.getLogger(FireflyWeightOptimizer.class);

    private static final int DEFAULT_POPULATION_SIZE = 15;
    private static final int DEFAULT_GENERATIONS = 50;
    private static final double DEFAULT_ALPHA = 0.2;
    private static final double DEFAULT_ABSORPTION = 1.0;
    private static final double MAX_DRIFT_FRACTION = 0.10;

    private final int populationSize;
    private final int generations;
    private final double alpha;
    private final double absorption;

    public FireflyWeightOptimizer() {
        this(DEFAULT_POPULATION_SIZE, DEFAULT_GENERATIONS, DEFAULT_ALPHA, DEFAULT_ABSORPTION);
    }

    public FireflyWeightOptimizer(int populationSize, int generations, double alpha, double absorption) {
        this.populationSize = populationSize;
        this.generations = generations;
        this.alpha = alpha;
        this.absorption = absorption;
    }

    /**
     * Optimise weights using the firefly algorithm.
     *
     * @param historicalReturns rows = periods, columns = assets
     * @param currentWeights    starting weight vector
     * @return optimised weight vector normalised to sum to 1.0
     */
    public double[] optimize(double[][] historicalReturns, double[] currentWeights) {
        if (currentWeights == null || currentWeights.length == 0) {
            log.warn("Invalid input: null or empty current weights");
            return currentWeights;
        }
        if (historicalReturns == null || historicalReturns.length == 0) {
            log.warn("Invalid input: returning current weights unchanged");
            return Arrays.copyOf(currentWeights, currentWeights.length);
        }

        int assetCount = currentWeights.length;
        double[][] population = initialisePopulation(currentWeights, assetCount);
        double[] fitness = evaluatePopulation(population, historicalReturns);

        for (int gen = 0; gen < generations; gen++) {
            population = evolvePopulation(population, fitness, currentWeights, historicalReturns);
            fitness = evaluatePopulation(population, historicalReturns);
        }

        int bestIndex = findBestIndex(fitness);
        double[] bestWeights = enforceDriftConstraint(population[bestIndex], currentWeights);
        normalizeAndClamp(bestWeights, currentWeights);

        log.info("Firefly optimisation completed: bestFitness={}", fitness[bestIndex]);
        return bestWeights;
    }

    double[][] initialisePopulation(double[] currentWeights, int assetCount) {
        double[][] population = new double[populationSize][assetCount];
        population[0] = Arrays.copyOf(currentWeights, assetCount);

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 1; i < populationSize; i++) {
            for (int j = 0; j < assetCount; j++) {
                double lower = currentWeights[j] * (1.0 - MAX_DRIFT_FRACTION);
                double upper = currentWeights[j] * (1.0 + MAX_DRIFT_FRACTION);
                population[i][j] = Math.max(0.001, lower + rng.nextDouble() * (upper - lower));
            }
            normalize(population[i]);
        }
        return population;
    }

    double[][] evolvePopulation(double[][] population, double[] fitness,
                                double[] currentWeights, double[][] historicalReturns) {
        int n = population.length;
        int assetCount = population[0].length;
        double[][] newPopulation = new double[n][assetCount];

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double[] moved = Arrays.copyOf(population[i], assetCount);
            for (int j = 0; j < n; j++) {
                if (fitness[j] > fitness[i]) {
                    double distance = euclideanDistance(population[i], population[j]);
                    double beta = 1.0 / (1.0 + absorption * distance * distance);
                    for (int k = 0; k < assetCount; k++) {
                        double attraction = beta * (population[j][k] - population[i][k]);
                        double randomness = alpha * (rng.nextDouble() - 0.5);
                        moved[k] += attraction + randomness;
                        moved[k] = Math.max(0.001, moved[k]);
                    }
                }
            }
            normalize(moved);
            moved = enforceDriftConstraint(moved, currentWeights);
            normalize(moved);
            newPopulation[i] = moved;
        }
        return newPopulation;
    }

    double[] evaluatePopulation(double[][] population, double[][] historicalReturns) {
        double[] fitness = new double[population.length];
        for (int i = 0; i < population.length; i++) {
            fitness[i] = computeSharpeRatio(population[i], historicalReturns);
        }
        return fitness;
    }

    double computeSharpeRatio(double[] weights, double[][] historicalReturns) {
        if (historicalReturns.length == 0) {
            return 0.0;
        }

        double[] portfolioReturns = new double[historicalReturns.length];
        for (int t = 0; t < historicalReturns.length; t++) {
            double sum = 0.0;
            for (int a = 0; a < weights.length; a++) {
                if (a < historicalReturns[t].length) {
                    sum += weights[a] * historicalReturns[t][a];
                }
            }
            portfolioReturns[t] = sum;
        }

        double mean = Arrays.stream(portfolioReturns).average().orElse(0.0);
        double variance = 0.0;
        for (double r : portfolioReturns) {
            variance += (r - mean) * (r - mean);
        }
        variance /= portfolioReturns.length;

        double stdDev = Math.sqrt(variance);
        if (stdDev < 1e-12) {
            return 0.0;
        }
        return mean / stdDev;
    }

    int findBestIndex(double[] fitness) {
        int bestIdx = 0;
        for (int i = 1; i < fitness.length; i++) {
            if (fitness[i] > fitness[bestIdx]) {
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    double[] enforceDriftConstraint(double[] candidate, double[] current) {
        double[] constrained = new double[candidate.length];
        for (int i = 0; i < candidate.length; i++) {
            double maxDrift = current[i] * MAX_DRIFT_FRACTION;
            double lower = current[i] - maxDrift;
            double upper = current[i] + maxDrift;
            constrained[i] = Math.max(lower, Math.min(upper, candidate[i]));
            constrained[i] = Math.max(0.001, constrained[i]);
        }
        return constrained;
    }

    void normalize(double[] weights) {
        double sum = 0.0;
        for (double w : weights) {
            sum += w;
        }
        if (sum <= 0.0) {
            Arrays.fill(weights, 1.0 / weights.length);
            return;
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= sum;
        }
    }

    void normalizeAndClamp(double[] weights, double[] currentWeights) {
        for (int iter = 0; iter < 20; iter++) {
            normalize(weights);
            boolean changed = false;
            for (int i = 0; i < weights.length; i++) {
                double maxDrift = currentWeights[i] * MAX_DRIFT_FRACTION;
                double lower = currentWeights[i] - maxDrift;
                double upper = currentWeights[i] + maxDrift;
                if (weights[i] < lower) {
                    weights[i] = lower;
                    changed = true;
                } else if (weights[i] > upper) {
                    weights[i] = upper;
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
        }
        normalize(weights);
    }

    double euclideanDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
