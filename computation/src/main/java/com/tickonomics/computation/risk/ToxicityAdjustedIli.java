package com.tickonomics.computation.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ToxicityAdjustedIli {

    private static final Logger log = LoggerFactory.getLogger(ToxicityAdjustedIli.class);

    public static final String TOXICITY_HARMFUL = "HARMFUL";
    public static final String TOXICITY_BENEFICIAL = "BENEFICIAL";
    public static final String TOXICITY_NEUTRAL = "NEUTRAL";

    private static final double HARMFUL_MODIFIER = 0.7;
    private static final double BENEFICIAL_MODIFIER = 1.1;
    private static final double NEUTRAL_MODIFIER = 1.0;

    public record ToxicityAdjustment(double originalIli, double adjustedIli,
                                     String toxicityClass, double sensitivityModifier) {
    }

    public ToxicityAdjustment adjust(double ili, String toxicityClass) {
        double modifier;
        String normalizedClass;

        if (toxicityClass == null) {
            modifier = NEUTRAL_MODIFIER;
            normalizedClass = TOXICITY_NEUTRAL;
        } else {
            normalizedClass = toxicityClass.toUpperCase();
            modifier = switch (normalizedClass) {
                case TOXICITY_HARMFUL -> HARMFUL_MODIFIER;
                case TOXICITY_BENEFICIAL -> BENEFICIAL_MODIFIER;
                default -> NEUTRAL_MODIFIER;
            };
        }

        double adjustedIli = ili * modifier;
        log.debug("Toxicity adjustment: originalIli={}, class={}, modifier={}, adjusted={}",
                ili, normalizedClass, modifier, adjustedIli);

        return new ToxicityAdjustment(ili, adjustedIli, normalizedClass, modifier);
    }
}
