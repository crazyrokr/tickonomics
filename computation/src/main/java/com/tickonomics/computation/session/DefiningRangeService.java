package com.tickonomics.computation.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefiningRangeService {

    private static final Logger log = LoggerFactory.getLogger(DefiningRangeService.class);

    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.1;
    private static final double NEWS_DECAY_TAU = 30.0;

    static final String CONFIDENCE_HIGH = "HIGH";
    static final String CONFIDENCE_MEDIUM = "MEDIUM";
    static final String CONFIDENCE_LOW = "LOW";

    public record DefiningRangeResult(double sessionHigh, double sessionLow, double definingRange,
                                      double proximityToHigh, double proximityToLow,
                                      String confidence, double newsDecayedConfidence) {
    }

    public DefiningRangeResult evaluate(double sessionHigh, double sessionLow, double currentPrice,
                                        int minutesSinceNewsEvent) {
        double definingRange = sessionHigh - sessionLow;

        double proximityToHigh = (sessionHigh - currentPrice) / Math.max(definingRange, 1e-10);
        double proximityToLow = (currentPrice - sessionLow) / Math.max(definingRange, 1e-10);

        String confidence = determineConfidence(proximityToHigh, proximityToLow, currentPrice,
                sessionHigh, sessionLow);

        double newsDecayedConfidence = applyNewsDecay(confidenceToScore(confidence), minutesSinceNewsEvent);

        log.debug("DR evaluation: high={}, low={}, price={}, proximityHigh={}, proximityLow={}, confidence={}",
                sessionHigh, sessionLow, currentPrice, proximityToHigh, proximityToLow, confidence);

        return new DefiningRangeResult(sessionHigh, sessionLow, definingRange,
                proximityToHigh, proximityToLow, confidence, newsDecayedConfidence);
    }

    String determineConfidence(double proximityToHigh, double proximityToLow,
                               double currentPrice, double sessionHigh, double sessionLow) {
        boolean insideRange = currentPrice >= sessionLow && currentPrice <= sessionHigh;
        if (!insideRange) {
            return CONFIDENCE_LOW;
        }
        if (proximityToLow <= HIGH_CONFIDENCE_THRESHOLD) {
            return CONFIDENCE_HIGH;
        }
        if (proximityToHigh <= HIGH_CONFIDENCE_THRESHOLD) {
            return CONFIDENCE_HIGH;
        }
        return CONFIDENCE_MEDIUM;
    }

    public double applyNewsDecay(double confidence, double minutes) {
        return confidence * Math.exp(-minutes / NEWS_DECAY_TAU);
    }

    private double confidenceToScore(String confidence) {
        return switch (confidence) {
            case CONFIDENCE_HIGH -> 1.0;
            case CONFIDENCE_MEDIUM -> 0.5;
            default -> 0.2;
        };
    }
}
