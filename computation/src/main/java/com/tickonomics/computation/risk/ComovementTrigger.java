package com.tickonomics.computation.risk;

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ComovementTrigger {

    private static final Logger log = LoggerFactory.getLogger(ComovementTrigger.class);

    private static final double PERCENTILE_DEFENSIVE = 90.0;
    private static final double PERCENTILE_MONITOR = 75.0;

    static final String RECOMMENDATION_SWITCH_TO_DEFENSIVE = "SWITCH_TO_DEFENSIVE";
    static final String RECOMMENDATION_MONITOR_CLOSELY = "MONITOR_CLOSELY";
    static final String RECOMMENDATION_NORMAL_OPS = "NORMAL_OPS";

    private final RestClientAnalyticsWorkerClient analyticsClient;

    public ComovementTrigger(RestClientAnalyticsWorkerClient analyticsClient) {
        this.analyticsClient = analyticsClient;
    }

    public record ComovementVerdict(boolean elevatedRisk, double factor,
                                    double percentile, String recommendation) {
    }

    public ComovementVerdict evaluate(double comovementFactor, double[] historicalFactors) {
        double percentile = computePercentile(comovementFactor, historicalFactors);
        String recommendation = determineRecommendation(percentile);
        boolean elevatedRisk = percentile > PERCENTILE_MONITOR;

        log.debug("Comovement factor={}, percentile={}, recommendation={}",
                comovementFactor, percentile, recommendation);

        return new ComovementVerdict(elevatedRisk, comovementFactor, percentile, recommendation);
    }

    public ComovementVerdict evaluateFromRemote(String symbol, double comovementFactor, double[] historicalFactors) {
        try {
            Map<String, Object> response = analyticsClient.sendAnalysisRequest(
                    "/api/v1/comovement",
                    Map.of("symbol", symbol, "factor", comovementFactor));
            if (response != null && !response.containsKey("error")) {
                log.debug("Remote comovement data received for symbol={}", symbol);
            }
        } catch (Exception e) {
            log.warn("Remote comovement fetch failed, using local calculation: {}", e.getMessage());
        }
        return evaluate(comovementFactor, historicalFactors);
    }

    double computePercentile(double factor, double[] historicalFactors) {
        if (historicalFactors == null || historicalFactors.length == 0) {
            return 50.0;
        }

        long countBelow = 0;
        for (double h : historicalFactors) {
            if (h < factor) {
                countBelow++;
            }
        }
        return (countBelow / (double) historicalFactors.length) * 100.0;
    }

    String determineRecommendation(double percentile) {
        if (percentile > PERCENTILE_DEFENSIVE) {
            return RECOMMENDATION_SWITCH_TO_DEFENSIVE;
        }
        if (percentile > PERCENTILE_MONITOR) {
            return RECOMMENDATION_MONITOR_CLOSELY;
        }
        return RECOMMENDATION_NORMAL_OPS;
    }
}
