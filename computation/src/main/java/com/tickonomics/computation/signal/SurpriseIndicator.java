package com.tickonomics.computation.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Information-theory entropy/surprise scoring on ILI observations.
 *
 * <p>surprise = -log2(P(observation)) where P from empirical distribution of recent ILI values.
 * entropy_estimate = running Shannon entropy over trailing window (default 100).
 * positionSizeModifier = 1.0 - min(0.5, surprise / maxSurprise) where maxSurprise=5.0.</p>
 */
@Service
public class SurpriseIndicator {

    private static final Logger log = LoggerFactory.getLogger(SurpriseIndicator.class);

    private static final double MAX_SURPRISE = 5.0;
    private static final double MAX_POSITION_REDUCTION = 0.5;

    /**
     * Record carrying the output of the surprise computation.
     */
    public record SurpriseResult(
            double surpriseScore,
            double entropyEstimate,
            double positionSizeModifier
    ) {}

    /**
     * Compute surprise, Shannon entropy, and position-size modifier for a current ILI value
     * given a trailing window of historical ILI observations.
     *
     * @param currentIli          the latest ILI observation
     * @param historicalIliValues trailing window of prior ILI values (may be null or empty)
     * @return SurpriseResult with computed scores; defaults (0.0, 0.0, 1.0) when no history
     */
    SurpriseResult compute(double currentIli, double[] historicalIliValues) {
        if (historicalIliValues == null || historicalIliValues.length == 0) {
            log.debug("No historical ILI data available; returning default surprise result");
            return new SurpriseResult(0.0, 0.0, 1.0);
        }

        double probability = estimateProbability(currentIli, historicalIliValues);

        double surpriseScore = computeSurprise(probability);
        double entropyEstimate = computeEntropy(historicalIliValues);
        double positionSizeModifier = computePositionSizeModifier(surpriseScore);

        log.debug("Surprise computed: surprise={}, entropy={}, modifier={}",
                surpriseScore, entropyEstimate, positionSizeModifier);

        return new SurpriseResult(surpriseScore, entropyEstimate, positionSizeModifier);
    }

    /**
     * Estimate empirical probability of observing the current ILI value using a Gaussian kernel
     * over the histogram of historical values.
     */
    private double estimateProbability(double currentIli, double[] historicalIliValues) {
        int n = historicalIliValues.length;

        double mean = 0.0;
        for (double v : historicalIliValues) {
            mean += v;
        }
        mean /= n;

        double variance = 0.0;
        for (double v : historicalIliValues) {
            variance += (v - mean) * (v - mean);
        }
        variance /= n;

        double stdDev = Math.sqrt(variance);

        if (stdDev == 0.0) {
            boolean allSame = true;
            for (double v : historicalIliValues) {
                if (v != currentIli) {
                    allSame = false;
                    break;
                }
            }
            return allSame ? 1.0 : (1.0 / n);
        }

        double bandwidth = 1.06 * stdDev * Math.pow(n, -0.2);
        if (bandwidth == 0.0) {
            bandwidth = 1e-10;
        }

        double kernelSum = 0.0;
        for (double v : historicalIliValues) {
            double u = (currentIli - v) / bandwidth;
            kernelSum += Math.exp(-0.5 * u * u) / (bandwidth * Math.sqrt(2.0 * Math.PI));
        }

        double density = kernelSum / n;

        if (density <= 0.0) {
            return 1e-10;
        }

        double probability = density * (2.0 * stdDev);
        return Math.min(probability, 1.0);
    }

    /**
     * Compute surprise as -log2(P).
     */
    private double computeSurprise(double probability) {
        if (probability <= 0.0) {
            return MAX_SURPRISE;
        }
        double surprise = -Math.log(probability) / Math.log(2.0);
        return Math.min(surprise, MAX_SURPRISE);
    }

    /**
     * Compute Shannon entropy over the empirical distribution of historical ILI values
     * using histogram binning.
     */
    private double computeEntropy(double[] historicalIliValues) {
        int n = historicalIliValues.length;

        double min = historicalIliValues[0];
        double max = historicalIliValues[0];
        for (double v : historicalIliValues) {
            if (v < min) {
                min = v;
            }
            if (v > max) {
                max = v;
            }
        }

        if (min == max) {
            return 0.0;
        }

        int numBins = Math.min(n, 20);
        double binWidth = (max - min) / numBins;
        if (binWidth == 0.0) {
            return 0.0;
        }

        int[] counts = new int[numBins];
        for (double v : historicalIliValues) {
            int bin = (int) ((v - min) / binWidth);
            if (bin >= numBins) {
                bin = numBins - 1;
            }
            counts[bin]++;
        }

        double entropy = 0.0;
        for (int count : counts) {
            if (count > 0) {
                double p = (double) count / n;
                entropy -= p * Math.log(p) / Math.log(2.0);
            }
        }

        return entropy;
    }

    /**
     * Compute position size modifier: 1.0 - min(0.5, surprise / maxSurprise).
     * Higher surprise reduces position size (max 50% reduction).
     */
    private double computePositionSizeModifier(double surpriseScore) {
        double reduction = Math.min(MAX_POSITION_REDUCTION, surpriseScore / MAX_SURPRISE);
        return 1.0 - reduction;
    }
}
