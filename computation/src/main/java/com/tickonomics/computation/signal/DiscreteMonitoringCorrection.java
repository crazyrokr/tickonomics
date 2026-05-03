package com.tickonomics.computation.signal;

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DiscreteMonitoringCorrection {

    private static final Logger log = LoggerFactory.getLogger(DiscreteMonitoringCorrection.class);
    private static final double BROADIE_KOU_GLASSERMAN_BETA = 0.5826;

    private final RestClientAnalyticsWorkerClient analyticsClient;

    public DiscreteMonitoringCorrection(RestClientAnalyticsWorkerClient analyticsClient) {
        this.analyticsClient = analyticsClient;
    }

    public CorrectionResult applyCorrection(double continuousThreshold, double sigma, int monitoringPoints) {
        if (sigma <= 0 || monitoringPoints <= 0) {
            return new CorrectionResult(continuousThreshold, continuousThreshold, 0.0, BROADIE_KOU_GLASSERMAN_BETA, monitoringPoints);
        }

        double deltaT = 1.0 / monitoringPoints;
        double correctionFactor = BROADIE_KOU_GLASSERMAN_BETA * sigma * Math.sqrt(deltaT);
        double adjustedThreshold = continuousThreshold + correctionFactor;

        return new CorrectionResult(adjustedThreshold, continuousThreshold, correctionFactor,
                BROADIE_KOU_GLASSERMAN_BETA, monitoringPoints);
    }

    public CorrectionResult applyCorrectionFromWorker(double continuousThreshold, double sigma, int monitoringPoints) {
        try {
            Map<String, Object> payload = Map.of(
                    "continuous_threshold", continuousThreshold,
                    "sigma", sigma,
                    "n_monitoring_points", monitoringPoints
            );

            Map<String, Object> response = analyticsClient.sendAnalysisRequest(
                    "/api/v1/simulate/discrete-correction", payload);

            if (response != null && response.containsKey("adjusted_threshold")) {
                double adjusted = ((Number) response.get("adjusted_threshold")).doubleValue();
                return new CorrectionResult(adjusted, continuousThreshold,
                        adjusted - continuousThreshold, BROADIE_KOU_GLASSERMAN_BETA, monitoringPoints);
            }
        } catch (Exception e) {
            log.debug("Discrete correction from worker failed, using local computation: {}", e.getMessage());
        }

        return applyCorrection(continuousThreshold, sigma, monitoringPoints);
    }

    public record CorrectionResult(
            double adjustedThreshold,
            double continuousThreshold,
            double correctionFactor,
            double beta,
            int monitoringPoints) {}
}
