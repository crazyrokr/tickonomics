package com.tickonomics.computation.kpi;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AlgorithmicIntensityMetric {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmicIntensityMetric.class);
    private static final int QUINTILE_COUNT = 5;
    private static final int DEFAULT_QUINTILE = 3;
    private static final String[] INTENSITY_LABELS = {"VERY_LOW", "LOW", "MEDIUM", "HIGH", "VERY_HIGH"};

    public record AtIntensityResult(double atProxy, int quintile, String intensityLevel) {
    }

    public AtIntensityResult compute(double dollarVolume, long messageCount, double[] historicalAtProxies) {
        double atProxy = Math.abs(dollarVolume) / Math.max(1.0, messageCount);
        int quintile = rankQuintile(atProxy, historicalAtProxies);
        String intensityLevel = INTENSITY_LABELS[quintile - 1];

        log.debug("AT proxy={}, quintile={}, level={}", atProxy, quintile, intensityLevel);
        return new AtIntensityResult(atProxy, quintile, intensityLevel);
    }

    int rankQuintile(double atProxy, double[] historicalAtProxies) {
        if (historicalAtProxies == null || historicalAtProxies.length < QUINTILE_COUNT) {
            log.debug("Insufficient history for quintile ranking, using default={}", DEFAULT_QUINTILE);
            return DEFAULT_QUINTILE;
        }

        double[] sorted = historicalAtProxies.clone();
        Arrays.sort(sorted);

        double[] boundaries = new double[QUINTILE_COUNT - 1];
        for (int i = 1; i < QUINTILE_COUNT; i++) {
            int index = (int) Math.ceil(sorted.length * i / (double) QUINTILE_COUNT) - 1;
            index = Math.max(0, Math.min(index, sorted.length - 1));
            boundaries[i - 1] = sorted[index];
        }

        for (int q = 0; q < boundaries.length; q++) {
            if (atProxy <= boundaries[q]) {
                return q + 1;
            }
        }
        return QUINTILE_COUNT;
    }
}
