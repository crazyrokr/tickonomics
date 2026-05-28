package com.tickonomics.computation.risk;

import com.tickonomics.computation.ili.ClimateSensitivityFactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cross-references ClimateSensitivityFactor output with ILI confidence
 * to produce a risk-adjusted confidence assessment.
 */
@Service
public class ClimateRiskGuard {

    private static final Logger log = LoggerFactory.getLogger(ClimateRiskGuard.class);

    static final double LOW_RISK_THRESHOLD = 0.8;
    static final double HIGH_RISK_THRESHOLD = 0.5;

    private final ClimateSensitivityFactor climateSensitivityFactor;

    public ClimateRiskGuard(ClimateSensitivityFactor climateSensitivityFactor) {
        this.climateSensitivityFactor = climateSensitivityFactor;
    }

    /**
     * Evaluates ILI confidence against the current climate sensitivity factor.
     *
     * @param iliConfidence ILI confidence value in [0.0, 1.0]
     * @param rrp           current RRP value
     * @param spread        current spread value
     * @param vol           current volatility value
     * @return ConfidenceAdjustment with original, modifier, adjusted confidence, and risk level
     */
    ConfidenceAdjustment evaluate(double iliConfidence, double rrp, double spread, double vol) {
        double modifier = climateSensitivityFactor.adjustThresholds(rrp, spread, vol).factor();
        double adjusted = iliConfidence * (1.0 - modifier);
        String riskLevel = classifyRisk(adjusted);

        log.debug("Climate risk guard: original={}, modifier={}, adjusted={}, riskLevel={}",
                iliConfidence, modifier, adjusted, riskLevel);

        return new ConfidenceAdjustment(iliConfidence, modifier, adjusted, riskLevel);
    }

    private String classifyRisk(double adjusted) {
        if (adjusted > LOW_RISK_THRESHOLD) {
            return "LOW";
        }
        if (adjusted >= HIGH_RISK_THRESHOLD) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    /**
     * Record carrying the confidence adjustment result.
     *
     * @param original  the original ILI confidence value
     * @param modifier  the climate sensitivity factor applied
     * @param adjusted  the resulting confidence after adjustment
     * @param riskLevel LOW (&gt;0.8), MEDIUM (0.5-0.8), or HIGH (&lt;0.5)
     */
    record ConfidenceAdjustment(double original, double modifier, double adjusted,
                                String riskLevel) {
    }
}
