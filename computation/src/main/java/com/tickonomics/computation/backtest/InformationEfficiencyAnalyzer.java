package com.tickonomics.computation.backtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InformationEfficiencyAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(InformationEfficiencyAnalyzer.class);
    private static final int PARAM_A = 5;
    private static final int PARAM_B = 20;
    private static final int PARAM_K = 60;

    public enum EfficiencyClass {
        HIGH, MEDIUM, LOW
    }

    public record EfficiencyResult(double pjr, double carShort, double carLong,
                                   EfficiencyClass efficiencyClass) {
    }

    public EfficiencyResult analyze(List<Double> returns, int eventIndex) {
        if (returns == null || returns.isEmpty() || eventIndex < 0 || eventIndex >= returns.size()) {
            log.warn("Invalid input: returns size={}, eventIndex={}",
                    returns != null ? returns.size() : 0, eventIndex);
            return new EfficiencyResult(Double.NaN, Double.NaN, Double.NaN, EfficiencyClass.LOW);
        }

        int shortStart = Math.max(0, eventIndex - PARAM_A);
        int shortEnd = Math.min(returns.size() - 1, eventIndex + PARAM_B);
        int longStart = Math.max(0, eventIndex - PARAM_K);

        double carShort = computeCar(returns, shortStart, shortEnd);
        double carLong = computeCar(returns, longStart, shortEnd);

        double pjr = Double.NaN;
        if (carLong != 0.0) {
            pjr = carShort / carLong;
        }

        EfficiencyClass efficiencyClass = classifyEfficiency(pjr);

        log.debug("PJR={}: carShort={}, carLong={}, class={}", pjr, carShort, carLong, efficiencyClass);

        return new EfficiencyResult(pjr, carShort, carLong, efficiencyClass);
    }

    double computeCar(List<Double> returns, int start, int end) {
        double cumulativeReturn = 0.0;
        for (int i = start; i <= end && i < returns.size(); i++) {
            cumulativeReturn += returns.get(i);
        }
        return cumulativeReturn;
    }

    EfficiencyClass classifyEfficiency(double pjr) {
        if (Double.isNaN(pjr)) {
            return EfficiencyClass.LOW;
        }
        if (pjr > 0.8) {
            return EfficiencyClass.HIGH;
        }
        if (pjr >= 0.4) {
            return EfficiencyClass.MEDIUM;
        }
        return EfficiencyClass.LOW;
    }
}
