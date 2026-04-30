package com.tickonomics.computation.kpi;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

@Service
public class NormalizationService {

    public ZscoreResult normalize(double[] historicalValues, String component, double currentValue, LookbackTier tier) {
        if (historicalValues == null || historicalValues.length < 2) {
            return ZscoreResult.invalid(component, currentValue, tier.days());
        }

        double mean = computeMean(historicalValues);
        double stdDev = computeStdDev(historicalValues, mean);

        return ZscoreResult.of(component, currentValue, mean, stdDev, tier.days());
    }

    public Map<LookbackTier, ZscoreResult> normalizeAllTiers(double[] fullHistory, String component, double currentValue) {
        Map<LookbackTier, ZscoreResult> results = new EnumMap<>(LookbackTier.class);
        for (LookbackTier tier : LookbackTier.values()) {
            double[] window = lastN(fullHistory, tier.days());
            results.put(tier, normalize(window, component, currentValue, tier));
        }
        return results;
    }

    public double[] computeAllZscores(double[] values) {
        if (values == null || values.length < 2) {
            return new double[0];
        }

        double mean = computeMean(values);
        double stdDev = computeStdDev(values, mean);

        if (stdDev == 0.0) {
            double[] result = new double[values.length];
            Arrays.fill(result, Double.NaN);
            return result;
        }

        double[] zscores = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            zscores[i] = (values[i] - mean) / stdDev;
        }
        return zscores;
    }

    public double computePercentileRank(double[] values, double currentValue) {
        if (values == null || values.length == 0) {
            return Double.NaN;
        }
        long below = Arrays.stream(values).filter(v -> v < currentValue).count();
        return (double) below / values.length * 100.0;
    }

    double computeMean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    double computeStdDev(double[] values, double mean) {
        double sumSq = 0;
        for (double v : values) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / values.length);
    }

    private double[] lastN(double[] values, int n) {
        if (values.length <= n) {
            return values;
        }
        return Arrays.copyOfRange(values, values.length - n, values.length);
    }
}
