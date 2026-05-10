package com.tickonomics.computation.backtest;

import com.tickonomics.computation.signal.DiscreteMonitoringCorrection;
import com.tickonomics.computation.signal.DiscreteMonitoringCorrection.CorrectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarrierHittingTimeAnalyzerTest {

  @Mock
  private DiscreteMonitoringCorrection correction;

  private BarrierHittingTimeAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    analyzer = new BarrierHittingTimeAnalyzer(correction);
  }

  @Nested
  class Analyze {

    @Test
    void givenValidSeries_whenAnalyze_thenReturnResult() {
      when(correction.applyCorrection(anyDouble(), anyDouble(), anyInt()))
          .thenReturn(new CorrectionResult(10.5, 10.0, 0.5, 0.5826, 252));

      List<Double> prices = List.of(100.0, 102.0, 101.0, 103.0, 105.0);
      BarrierHittingTimeAnalyzer.HittingTimeResult result =
          analyzer.analyze(prices, 110.0, 0.2, 252);

      assertTrue(result.hitProbability() >= 0.0);
      assertTrue(result.hitProbability() <= 1.0);
      assertTrue(result.expectedHittingTime() > 0);
      assertEquals(0.5, result.correctionFactor(), 0.001);
    }

    @Test
    void givenEmptyPrices_whenAnalyze_thenReturnZeroedResult() {
      BarrierHittingTimeAnalyzer.HittingTimeResult result =
          analyzer.analyze(List.of(), 110.0, 0.2, 252);

      assertEquals(0.0, result.hitProbability());
      assertEquals(0.0, result.expectedHittingTime());
    }

    @Test
    void givenNullPrices_whenAnalyze_thenReturnZeroedResult() {
      BarrierHittingTimeAnalyzer.HittingTimeResult result =
          analyzer.analyze(null, 110.0, 0.2, 252);

      assertEquals(0.0, result.hitProbability());
    }

    @Test
    void givenZeroSigma_whenAnalyze_thenReturnZeroedResult() {
      BarrierHittingTimeAnalyzer.HittingTimeResult result =
          analyzer.analyze(List.of(100.0, 105.0), 110.0, 0.0, 252);

      assertEquals(0.0, result.hitProbability());
    }

    @Test
    void givenZeroMonitoringPoints_whenAnalyze_thenReturnZeroedResult() {
      BarrierHittingTimeAnalyzer.HittingTimeResult result =
          analyzer.analyze(List.of(100.0, 105.0), 110.0, 0.2, 0);

      assertEquals(0.0, result.hitProbability());
    }

    @Test
    void givenBarrierAlreadyHit_whenAnalyze_thenProbabilityOne() {
      List<Double> prices = List.of(100.0, 110.0);
      BarrierHittingTimeAnalyzer.HittingTimeResult result =
          analyzer.analyze(prices, 110.0, 0.2, 252);

      assertEquals(1.0, result.hitProbability());
      assertEquals(0.0, result.expectedHittingTime());
    }
  }
}
