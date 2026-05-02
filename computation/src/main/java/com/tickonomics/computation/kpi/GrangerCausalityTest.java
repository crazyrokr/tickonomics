package com.tickonomics.computation.kpi;

import com.tickonomics.contracts.client.AnalyticsWorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GrangerCausalityTest {

    private static final Logger log = LoggerFactory.getLogger(GrangerCausalityTest.class);

    private final AnalyticsWorkerClient analyticsClient;

    public GrangerCausalityTest(AnalyticsWorkerClient analyticsClient) {
        this.analyticsClient = analyticsClient;
    }

    public GrangerResult test(List<Double> x, List<Double> y, int maxLags) {
        if (x.size() != y.size()) {
            return new GrangerResult(0.0, 1.0, 0, false, "SERIES_LENGTH_MISMATCH", x.size());
        }
        if (x.size() < 15) {
            return new GrangerResult(0.0, 1.0, 0, false, "INSUFFICIENT_DATA", x.size());
        }

        Map<String, Object> payload = Map.of(
                "x", x,
                "y", y,
                "max_lags", maxLags
        );

        Map<String, Object> response = analyticsClient.sendAnalysisRequest(
                "/api/v1/econometrics/granger", payload);

        if (response.containsKey("error")) {
            log.warn("Granger test failed: {}", response.get("error"));
            return new GrangerResult(0.0, 1.0, 0, false, "WORKER_ERROR", x.size());
        }

        double fStat = ((Number) response.getOrDefault("f_statistic", 0.0)).doubleValue();
        double pValue = ((Number) response.getOrDefault("p_value", 1.0)).doubleValue();
        int lags = ((Number) response.getOrDefault("lags", 0)).intValue();
        boolean causal = Boolean.TRUE.equals(response.get("causal"));

        return new GrangerResult(fStat, pValue, lags, causal, "OK", x.size());
    }

    public record GrangerResult(
            double fStatistic,
            double pValue,
            int lags,
            boolean causal,
            String status,
            int nObservations
    ) {}
}
