package com.tickonomics.computation.backtest;

import com.tickonomics.computation.client.RestClientAnalyticsWorkerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeightOptimizerTest {

  @Mock
  private RestClientAnalyticsWorkerClient analyticsClient;

  private WeightOptimizer optimizer;

  @BeforeEach
  void setUp() {
    optimizer = new WeightOptimizer(analyticsClient);
  }

  @Nested
  class SobolRobustnessScan {

    @Test
    @SuppressWarnings("unchecked")
    void givenValidInput_whenScan_thenReturnResult() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of(
              "mean_sharpe", 1.5,
              "std_sharpe", 0.3,
              "n_samples", 128,
              "best_params", Map.of("lr", 0.01)));

      Map<String, Object> result = optimizer.sobolRobustnessScan(
          List.of(0.01, 0.02, -0.01),
          Map.of("window", List.of(10.0, 50.0)),
          128);

      assertEquals(1.5, ((Number) result.get("mean_sharpe")).doubleValue());
      assertEquals(128, ((Number) result.get("n_samples")).intValue());
    }

    @Test
    void givenWorkerError_whenScan_thenReturnErrorMap() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of("error", "connection refused"));

      Map<String, Object> result = optimizer.sobolRobustnessScan(
          List.of(0.01), Map.of(), 10);

      assertTrue(result.containsKey("error"));
    }
  }

  @Nested
  class OptimizeWeights {

    @Test
    void givenValidScan_whenOptimize_thenReturnOptimizationResult() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of(
              "best_params", Map.of("threshold", 0.5),
              "mean_sharpe", 2.0,
              "std_sharpe", 0.4,
              "n_samples", 256));

      WeightOptimizer.OptimizationResult result = optimizer.optimizeWeights(
          List.of(0.01, 0.02), Map.of("threshold", List.of(0.1, 1.0)), 256);

      assertEquals(2.0, result.meanSharpe());
      assertEquals(0.4, result.stdSharpe());
      assertEquals(256, result.samples());
    }

    @Test
    void givenError_whenOptimize_thenReturnEmptyResult() {
      when(analyticsClient.sendAnalysisRequest(anyString(), any()))
          .thenReturn(Map.of("error", "timeout"));

      WeightOptimizer.OptimizationResult result = optimizer.optimizeWeights(
          List.of(0.01), Map.of(), 10);

      assertEquals(0.0, result.meanSharpe());
      assertEquals(0, result.samples());
      assertTrue(result.bestParams().isEmpty());
    }
  }
}
