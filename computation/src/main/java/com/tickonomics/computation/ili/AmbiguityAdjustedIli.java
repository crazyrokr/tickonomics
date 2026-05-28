package com.tickonomics.computation.ili;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Extends ILI with uncertainty bands. Computes upper/lower bounds around
 * an ILI value and classifies the result as CONFIDENT, UNCERTAIN, or SUPPRESSED
 * based on the width of the confidence band relative to a configurable threshold.
 */
@Service
public class AmbiguityAdjustedIli {

    private static final Logger log = LoggerFactory.getLogger(AmbiguityAdjustedIli.class);

    private static final double Z_SCORE_95 = 1.96;

    private final double bandThreshold;

    public AmbiguityAdjustedIli(
            @Value("${computation.ili.ambiguity.band-threshold:0.5}") double bandThreshold) {
        this.bandThreshold = bandThreshold;
    }

    /**
     * Adjusts an ILI value with ambiguity bands derived from historical volatility.
     *
     * @param iliValue           the raw ILI score
     * @param historicalVolatility standard deviation of recent ILI values
     * @param lookbackCount      number of observations used for volatility estimation
     * @return AmbiguityResult with bands and status
     */
    AmbiguityResult adjust(double iliValue, double historicalVolatility, int lookbackCount) {
        if (lookbackCount <= 0) {
            log.warn("Non-positive lookbackCount={}, returning SUPPRESSED", lookbackCount);
            return new AmbiguityResult(iliValue, iliValue, iliValue,
                    "SUPPRESSED", "Non-positive lookback count");
        }

        if (Double.isNaN(iliValue) || Double.isInfinite(iliValue)) {
            log.warn("Invalid iliValue={}, returning SUPPRESSED", iliValue);
            return new AmbiguityResult(0.0, 0.0, 0.0,
                    "SUPPRESSED", "Invalid ILI value");
        }

        if (Double.isNaN(historicalVolatility) || Double.isInfinite(historicalVolatility) || historicalVolatility < 0) {
            log.warn("Invalid historicalVolatility={}, returning SUPPRESSED", historicalVolatility);
            return new AmbiguityResult(iliValue, iliValue, iliValue,
                    "SUPPRESSED", "Invalid historical volatility");
        }

        double stdError = historicalVolatility / Math.sqrt(lookbackCount);
        double upperBand = iliValue + Z_SCORE_95 * stdError;
        double lowerBand = iliValue - Z_SCORE_95 * stdError;
        double bandWidth = upperBand - lowerBand;

        String status;
        String reason;

        if (bandWidth > bandThreshold) {
            status = "UNCERTAIN";
            reason = String.format("Band width %.4f exceeds threshold %.4f", bandWidth, bandThreshold);
            log.debug("ILI {} marked UNCERTAIN: {}", iliValue, reason);
        } else {
            status = "CONFIDENT";
            reason = String.format("Band width %.4f within threshold %.4f", bandWidth, bandThreshold);
            log.debug("ILI {} marked CONFIDENT: {}", iliValue, reason);
        }

        return new AmbiguityResult(iliValue, upperBand, lowerBand, status, reason);
    }

    /**
     * Data carrier for ambiguity-adjusted ILI results.
     *
     * @param ili        the original ILI value
     * @param upperBand  upper 95% confidence band
     * @param lowerBand  lower 95% confidence band
     * @param status     CONFIDENT, UNCERTAIN, or SUPPRESSED
     * @param reason     human-readable explanation of the status
     */
    public record AmbiguityResult(
            double ili,
            double upperBand,
            double lowerBand,
            String status,
            String reason
    ) {
        /**
         * Signals are suppressed when status is UNCERTAIN or SUPPRESSED.
         */
        public boolean isSignalSuppressed() {
            return "UNCERTAIN".equals(status) || "SUPPRESSED".equals(status);
        }
    }
}
