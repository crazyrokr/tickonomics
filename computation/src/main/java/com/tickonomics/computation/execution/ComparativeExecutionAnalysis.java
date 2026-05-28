package com.tickonomics.computation.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ComparativeExecutionAnalysis {

    private static final Logger log = LoggerFactory.getLogger(ComparativeExecutionAnalysis.class);

    public enum Recommendation {
        PREFER_PASSIVE, PREFER_AGGRESSIVE, NEUTRAL
    }

    public record ExecutionSlippage(String type, double slippageBps, int fillCount) {
    }

    public record ExecutionComparison(double passiveSlippageBps, double aggressiveSlippageBps,
                                      double slippageSavingsBps, Recommendation recommendation) {
    }

    public ExecutionComparison compare(List<ExecutionSlippage> passiveExecutions,
                                       List<ExecutionSlippage> aggressiveExecutions) {
        double passiveSlippageBps = averageSlippage(passiveExecutions);
        double aggressiveSlippageBps = averageSlippage(aggressiveExecutions);
        int passiveFills = totalFills(passiveExecutions);
        int aggressiveFills = totalFills(aggressiveExecutions);

        double slippageSavingsBps = aggressiveSlippageBps - passiveSlippageBps;

        Recommendation recommendation = determineRecommendation(
                slippageSavingsBps, passiveFills, aggressiveFills);

        log.debug("Execution comparison: passive={}.2f bps, aggressive={:.2f} bps, savings={:.2f}, rec={}",
                passiveSlippageBps, aggressiveSlippageBps, slippageSavingsBps, recommendation);

        return new ExecutionComparison(passiveSlippageBps, aggressiveSlippageBps,
                slippageSavingsBps, recommendation);
    }

    double averageSlippage(List<ExecutionSlippage> executions) {
        if (executions == null || executions.isEmpty()) {
            return 0.0;
        }
        return executions.stream()
                .mapToDouble(ExecutionSlippage::slippageBps)
                .average()
                .orElse(0.0);
    }

    int totalFills(List<ExecutionSlippage> executions) {
        if (executions == null || executions.isEmpty()) {
            return 0;
        }
        return executions.stream()
                .mapToInt(ExecutionSlippage::fillCount)
                .sum();
    }

    Recommendation determineRecommendation(double savingsBps, int passiveFills, int aggressiveFills) {
        if (savingsBps > 2.0 && passiveFills > aggressiveFills * 0.5) {
            return Recommendation.PREFER_PASSIVE;
        }
        if (savingsBps < -1.0) {
            return Recommendation.PREFER_AGGRESSIVE;
        }
        return Recommendation.NEUTRAL;
    }
}
