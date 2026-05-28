package com.tickonomics.computation.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BehaviouralRiskProcessor {

    private static final Logger log = LoggerFactory.getLogger(BehaviouralRiskProcessor.class);

    private static final double W_OFI = 0.25;
    private static final double W_SPREAD_VOL = 0.25;
    private static final double W_SENTIMENT = 0.20;
    private static final double W_CANCELLATION = 0.15;
    private static final double W_SYSTEMIC = 0.15;

    private static final double THRESHOLD_LOW = 0.3;
    private static final double THRESHOLD_HIGH = 0.7;

    public record BriResult(double briScore, double ofiZscore, double spreadVolatility,
                            double sentimentPolarity, double cancellationRatio,
                            double systemicRiskContribution, String riskLevel) {
    }

    public BriResult compute(double ofiZscore, double spreadVolatility, double sentimentPolarity,
                             double cancellationRatio, double systemicRiskContribution) {

        double bri = W_OFI * clamp01(ofiZscore)
                + W_SPREAD_VOL * clamp01(spreadVolatility)
                + W_SENTIMENT * clamp01(sentimentPolarity)
                + W_CANCELLATION * clamp01(cancellationRatio)
                + W_SYSTEMIC * clamp01(systemicRiskContribution);

        String riskLevel = classifyRisk(bri);

        log.debug("BRI score={}, riskLevel={}, ofi={}, spreadVol={}, sentiment={}, cancel={}, systemic={}",
                bri, riskLevel, ofiZscore, spreadVolatility, sentimentPolarity,
                cancellationRatio, systemicRiskContribution);

        return new BriResult(bri, ofiZscore, spreadVolatility, sentimentPolarity,
                cancellationRatio, systemicRiskContribution, riskLevel);
    }

    String classifyRisk(double bri) {
        if (bri > THRESHOLD_HIGH) {
            return "HIGH";
        }
        if (bri >= THRESHOLD_LOW) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
