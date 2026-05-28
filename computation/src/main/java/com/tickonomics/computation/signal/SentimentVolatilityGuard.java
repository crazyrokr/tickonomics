package com.tickonomics.computation.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SentimentVolatilityGuard {

    private static final Logger log = LoggerFactory.getLogger(SentimentVolatilityGuard.class);
    private static final double LOW_CERTAINTY = 0.3;
    private static final double HIGH_CERTAINTY = 0.7;
    private static final double VERY_LOW_CERTAINTY = 0.2;
    private static final double WIDEN_FACTOR = 1.5;
    private static final double NARROW_FACTOR = 0.8;
    private static final double MODERATE_FACTOR = 1.0;

    public record SentimentGuardResult(double thresholdModifier, double certainty,
                                       String adjustmentReason) {
    }

    public SentimentGuardResult computeThresholdModifier(double certainty) {
        double modifier;
        String reason;

        if (certainty < LOW_CERTAINTY) {
            modifier = WIDEN_FACTOR;
            reason = "LOW_CERTAINTY_WIDEN";
        } else if (certainty > HIGH_CERTAINTY) {
            modifier = NARROW_FACTOR;
            reason = "HIGH_CERTAINTY_NARROW";
        } else {
            modifier = MODERATE_FACTOR;
            reason = "MODERATE_CERTAINTY_NO_CHANGE";
        }

        log.debug("Threshold modifier: certainty={:.2f}, modifier={:.2f}, reason={}",
                certainty, modifier, reason);

        return new SentimentGuardResult(modifier, certainty, reason);
    }

    public boolean shouldSuppressSignal(double certainty, double signalStrength, double threshold) {
        double adjustedThreshold = threshold * computeThresholdModifier(certainty).thresholdModifier();
        boolean suppress = certainty < VERY_LOW_CERTAINTY && signalStrength < adjustedThreshold;

        log.debug("Suppression check: certainty={:.2f}, strength={:.4f}, threshold={:.4f}, " +
                        "adjusted={:.4f}, suppress={}", certainty, signalStrength, threshold,
                adjustedThreshold, suppress);

        return suppress;
    }
}
