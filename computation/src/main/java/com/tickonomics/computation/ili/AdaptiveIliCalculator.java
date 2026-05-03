package com.tickonomics.computation.ili;

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient;
import com.tickonomics.computation.kpi.IliCalculator;
import com.tickonomics.computation.kpi.IliResult;
import com.tickonomics.computation.kpi.ZscoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AdaptiveIliCalculator {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveIliCalculator.class);

    private final IliCalculator delegate;
    private final WeightedWeightStore weightStore;
    private final RestClientAnalyticsWorkerClient analyticsClient;

    public AdaptiveIliCalculator(
            IliCalculator delegate,
            WeightedWeightStore weightStore,
            RestClientAnalyticsWorkerClient analyticsClient) {
        this.delegate = delegate;
        this.weightStore = weightStore;
        this.analyticsClient = analyticsClient;
    }

    public IliResult calculate(ZscoreResult zRrp, ZscoreResult zSpread, ZscoreResult zVol) {
        requestWeightDelta();
        double[] weights = weightStore.getCalibratedWeights();
        return delegate.calculate(zRrp, zSpread, zVol, weights);
    }

    private void requestWeightDelta() {
        try {
            double[] current = weightStore.getCalibratedWeights();
            Map<String, Object> payload = Map.of(
                    "current_weights", current,
                    "lookback_days", 60
            );

            Map<String, Object> response = analyticsClient.sendAnalysisRequest(
                    "/api/v1/optimizer/weight-delta", payload);

            if (response != null && response.containsKey("weight_deltas")) {
                @SuppressWarnings("unchecked")
                var deltaList = (java.util.List<Number>) response.get("weight_deltas");
                double[] deltas = deltaList.stream().mapToDouble(Number::doubleValue).toArray();
                weightStore.applyDelta(deltas);
                log.debug("Applied weight deltas from optimizer");
            }
        } catch (Exception e) {
            log.debug("Weight delta request failed, using current weights: {}", e.getMessage());
        }
    }
}
