package com.tickonomics.computation.risk;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RiskPremiumResidualMonitor {

    private static final Logger log = LoggerFactory.getLogger(RiskPremiumResidualMonitor.class);
    private static final double DEFAULT_THRESHOLD = 2.0;

    public record ResidualResult(
            double observedYield,
            double fairValueYield,
            double residual,
            double residualStd,
            boolean dislocated,
            String status) {}

    public RiskPremiumResidualMonitor() {}

    public ResidualResult monitor(double observedYield, double fairValueYield, List<Double> historicalResiduals) {
        return monitor(observedYield, fairValueYield, historicalResiduals, DEFAULT_THRESHOLD);
    }

    public ResidualResult monitor(
            double observedYield,
            double fairValueYield,
            List<Double> historicalResiduals,
            double threshold) {

        if (historicalResiduals == null || historicalResiduals.size() < 2) {
            log.warn("Insufficient historical residuals for dislocation analysis: {}",
                    historicalResiduals == null ? 0 : historicalResiduals.size());
            return new ResidualResult(observedYield, fairValueYield, 0.0, 0.0, false, "INSUFFICIENT_DATA");
        }

        double residual = observedYield - fairValueYield;
        double residualStd = computeStd(historicalResiduals);
        boolean dislocated = Math.abs(residual) > threshold * residualStd;
        String status = classifyDislocation(residual, dislocated);

        log.info("Residual monitor: observed={:.4f}, fairValue={:.4f}, residual={:.4f}, "
                        + "std={:.4f}, dislocated={}, status={}",
                observedYield, fairValueYield, residual, residualStd, dislocated, status);

        return new ResidualResult(observedYield, fairValueYield, residual, residualStd, dislocated, status);
    }

    double computeStd(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average().orElse(0.0);
        return Math.sqrt(variance);
    }

    String classifyDislocation(double residual, boolean dislocated) {
        if (!dislocated) {
            return "NORMAL";
        }
        if (residual > 0) {
            return "PREMIUM_DISLOCATED";
        }
        return "DISCOUNT_DISLOCATED";
    }
}
