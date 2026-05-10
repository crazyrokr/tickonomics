package com.tickonomics.computation.backtest;

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WeightOptimizer {

  private final RestClientAnalyticsWorkerClient analyticsClient;

  public WeightOptimizer(RestClientAnalyticsWorkerClient analyticsClient) {
    this.analyticsClient = analyticsClient;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> sobolRobustnessScan(
      List<Double> strategyReturns,
      Map<String, List<Double>> parameterRanges,
      int nSamples) {

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("strategy_returns", strategyReturns);
    payload.put("parameter_ranges", parameterRanges);
    payload.put("n_samples", nSamples);
    payload.put("seed", 42);

    return analyticsClient.sendAnalysisRequest(
        "/api/v1/backtest/robustness-scan", payload);
  }

  @SuppressWarnings("unchecked")
  public OptimizationResult optimizeWeights(
      List<Double> strategyReturns,
      Map<String, List<Double>> parameterRanges,
      int nSamples) {

    Map<String, Object> scanResult = sobolRobustnessScan(
        strategyReturns, parameterRanges, nSamples);

    if (scanResult.containsKey("error")) {
      return new OptimizationResult(Map.of(), 0.0, 0.0, 0);
    }

    Map<String, Object> bestParams = scanResult.get("best_params") instanceof Map
        ? (Map<String, Object>) scanResult.get("best_params")
        : Map.of();
    double meanSharpe = scanResult.get("mean_sharpe") instanceof Number
        ? ((Number) scanResult.get("mean_sharpe")).doubleValue()
        : 0.0;
    double stdSharpe = scanResult.get("std_sharpe") instanceof Number
        ? ((Number) scanResult.get("std_sharpe")).doubleValue()
        : 0.0;
    int actualSamples = scanResult.get("n_samples") instanceof Number
        ? ((Number) scanResult.get("n_samples")).intValue()
        : 0;

    return new OptimizationResult(bestParams, meanSharpe, stdSharpe, actualSamples);
  }

  public record OptimizationResult(
      Map<String, Object> bestParams,
      double meanSharpe,
      double stdSharpe,
      int samples) {}
}
