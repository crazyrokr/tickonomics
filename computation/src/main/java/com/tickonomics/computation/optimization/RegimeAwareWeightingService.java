package com.tickonomics.computation.optimization;

import com.tickonomics.computation.ili.WeightedWeightStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * Adjusts ILI component weights based on the current market regime.
 * HIGH_VOL boosts spread/vol weights by 40% and reduces RRP.
 * UNSTABLE boosts by 20%. METASTABLE and LOW_VOL leave weights unchanged.
 * All outputs are normalized to sum to 1.0.
 */
@Service
public class RegimeAwareWeightingService {

    private static final Logger log = LoggerFactory.getLogger(RegimeAwareWeightingService.class);

    static final double HIGH_VOL_BOOST = 0.40;
    static final double UNSTABLE_BOOST = 0.20;
    static final double HIGH_VOL_RRP_REDUCTION = 0.40;

    private final WeightedWeightStore weightStore;

    public RegimeAwareWeightingService(WeightedWeightStore weightStore) {
        this.weightStore = weightStore;
    }

    /**
     * Adjusts weights based on regime type.
     *
     * @param regimeType     the current regime (HIGH_VOL, UNSTABLE, METASTABLE, LOW_VOL)
     * @param currentWeights the current weight vector [RRP, spread, vol]
     * @return adjusted and normalized weight vector
     */
    double[] adjustWeights(String regimeType, double[] currentWeights) {
        if (currentWeights == null || currentWeights.length == 0) {
            log.warn("Null or empty currentWeights, returning base weights");
            return weightStore.getBaseWeights();
        }

        double[] adjusted = Arrays.copyOf(currentWeights, currentWeights.length);

        if ("HIGH_VOL".equals(regimeType)) {
            applyHighVolAdjustment(adjusted);
        } else if ("UNSTABLE".equals(regimeType)) {
            applyUnstableAdjustment(adjusted);
        } else {
            log.debug("Regime {} requires no weight adjustment", regimeType);
        }

        normalize(adjusted);
        log.info("Weights adjusted for regime {}: {}", regimeType, Arrays.toString(adjusted));
        return adjusted;
    }

    private void applyHighVolAdjustment(double[] weights) {
        for (int i = 1; i < weights.length; i++) {
            weights[i] *= (1.0 + HIGH_VOL_BOOST);
        }
        if (weights.length > 0) {
            weights[0] *= (1.0 - HIGH_VOL_RRP_REDUCTION);
        }
        log.debug("Applied HIGH_VOL adjustment: boost={}, rrp_reduction={}", HIGH_VOL_BOOST, HIGH_VOL_RRP_REDUCTION);
    }

    private void applyUnstableAdjustment(double[] weights) {
        for (int i = 1; i < weights.length; i++) {
            weights[i] *= (1.0 + UNSTABLE_BOOST);
        }
        log.debug("Applied UNSTABLE adjustment: boost={}", UNSTABLE_BOOST);
    }

    private void normalize(double[] weights) {
        double sum = Arrays.stream(weights).sum();
        if (sum <= 0.0) {
            double[] base = weightStore.getBaseWeights();
            System.arraycopy(base, 0, weights, 0, Math.min(base.length, weights.length));
            return;
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= sum;
        }
    }
}
