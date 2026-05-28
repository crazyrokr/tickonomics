package com.tickonomics.computation.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LiquidityPremiumFactor {

    private static final Logger log = LoggerFactory.getLogger(LiquidityPremiumFactor.class);

    static final String RATIONALE_AT_LIQUIDITY_PREMIUM = "AT_LIQUIDITY_PREMIUM";
    static final String RATIONALE_MODERATE_PREMIUM = "MODERATE_PREMIUM";
    static final String RATIONALE_NO_PREMIUM = "NO_PREMIUM";

    public record PremiumFactorResult(double factor, int quintile, String rationale) {
    }

    public PremiumFactorResult compute(int quintile, double spreadCompression) {
        double factor;
        String rationale;

        if (quintile == 5) {
            factor = 1.0 + spreadCompression * 0.1;
            rationale = RATIONALE_AT_LIQUIDITY_PREMIUM;
        } else if (quintile == 4) {
            factor = 1.0 + spreadCompression * 0.05;
            rationale = RATIONALE_MODERATE_PREMIUM;
        } else {
            factor = 1.0;
            rationale = RATIONALE_NO_PREMIUM;
        }

        log.debug("Liquidity premium factor: quintile={}, spreadCompression={}, factor={}, rationale={}",
                quintile, spreadCompression, factor, rationale);

        return new PremiumFactorResult(factor, quintile, rationale);
    }
}
