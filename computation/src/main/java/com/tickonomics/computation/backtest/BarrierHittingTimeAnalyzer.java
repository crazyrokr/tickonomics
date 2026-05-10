package com.tickonomics.computation.backtest;

import com.tickonomics.computation.signal.DiscreteMonitoringCorrection;
import com.tickonomics.computation.signal.DiscreteMonitoringCorrection.CorrectionResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BarrierHittingTimeAnalyzer {

  private final DiscreteMonitoringCorrection correction;

  public BarrierHittingTimeAnalyzer(DiscreteMonitoringCorrection correction) {
    this.correction = correction;
  }

  public HittingTimeResult analyze(
      List<Double> priceSeries,
      double barrierLevel,
      double sigma,
      int monitoringPoints) {

    if (priceSeries == null || priceSeries.isEmpty() || sigma <= 0 || monitoringPoints <= 0) {
      return new HittingTimeResult(0.0, 0.0, barrierLevel, 0.0);
    }

    double currentPrice = priceSeries.getLast();
    double continuousDistance = Math.abs(barrierLevel - currentPrice);

    if (continuousDistance == 0) {
      return new HittingTimeResult(1.0, 0.0, barrierLevel, 0.0);
    }

    CorrectionResult corrected = correction.applyCorrection(
        continuousDistance, sigma, monitoringPoints);

    double adjustedBarrier = corrected.adjustedThreshold();
    double correctionFactor = corrected.correctionFactor();

    double drift = estimateDrift(priceSeries);
    double timeToBarrier = adjustedBarrier > 0
        ? continuousDistance / (sigma * Math.sqrt(Math.max(1, monitoringPoints)))
        : Double.MAX_VALUE;

    double hitProbability = computeHitProbability(
        continuousDistance, adjustedBarrier, sigma, drift);

    return new HittingTimeResult(
        hitProbability,
        timeToBarrier,
        barrierLevel - correctionFactor * Math.signum(barrierLevel - currentPrice),
        correctionFactor);
  }

  private double estimateDrift(List<Double> prices) {
    if (prices.size() < 2) {
      return 0.0;
    }
    double sum = 0.0;
    for (int i = 1; i < prices.size(); i++) {
      if (prices.get(i - 1) != 0) {
        sum += (prices.get(i) - prices.get(i - 1)) / prices.get(i - 1);
      }
    }
    return sum / (prices.size() - 1);
  }

  private double computeHitProbability(
      double distance, double adjustedDistance, double sigma, double drift) {

    if (sigma <= 0 || distance <= 0) {
      return 0.0;
    }
    double d = (drift + 0.5 * sigma * sigma) / sigma;
    double exponent = -2.0 * d * adjustedDistance / (sigma * sigma);
    exponent = Math.max(-500, Math.min(500, exponent));
    return Math.exp(exponent);
  }

  public record HittingTimeResult(
      double hitProbability,
      double expectedHittingTime,
      double adjustedBarrier,
      double correctionFactor) {}
}
