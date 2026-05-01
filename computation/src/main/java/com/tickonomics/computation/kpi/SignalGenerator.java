package com.tickonomics.computation.kpi;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SignalGenerator {

  private final NormalizationService normalizationService;

  private double buyThreshold = 80.0;
  private double sellThreshold = 20.0;
  private double transactionCostBps = 5.0;
  private long cooldownMs = 3600_000;

  private final Map<String, Instant> lastSignalTime = new HashMap<>();

  public SignalGenerator(NormalizationService normalizationService) {
    this.normalizationService = normalizationService;
  }

  public Optional<SignalResult> evaluate(String symbol, IliResult iliResult, double[] historicalIliValues) {
    if (historicalIliValues == null || historicalIliValues.length < 20) {
      return Optional.of(new SignalResult(
          symbol,
          SignalResult.DIR_BUY,
          SignalResult.STATUS_INSUFFICIENT,
          Double.NaN,
          iliResult.iliValue(),
          0,
          0,
          0));
    }

    double percentile = normalizationService.computePercentileRank(historicalIliValues, iliResult.iliValue());
    if (Double.isNaN(percentile)) {
      return Optional.of(new SignalResult(
          symbol,
          SignalResult.DIR_BUY,
          SignalResult.STATUS_INSUFFICIENT,
          Double.NaN,
          iliResult.iliValue(),
          0,
          0,
          0));
    }

    if (IliResult.STATUS_DISLOCATED.equals(iliResult.dataStatus())) {
      return Optional.empty();
    }

    String direction = determineDirection(percentile);
    if (direction == null) {
      return Optional.empty();
    }

    double expectedMove = estimateExpectedMove(historicalIliValues, percentile);
    double estimatedCost = transactionCostBps;

    String status = evaluateStatus(iliResult, expectedMove, estimatedCost, symbol);
    if (SignalResult.STATUS_COOLDOWN.equals(status)) {
      return Optional.empty();
    }

    double strength = Math.abs(percentile - 50.0) / 50.0;

    return Optional.of(new SignalResult(
        symbol,
        direction,
        status,
        percentile,
        iliResult.iliValue(),
        expectedMove,
        estimatedCost,
        strength));
  }

  String determineDirection(double percentile) {
    if (percentile >= buyThreshold) {
      return SignalResult.DIR_BUY;
    }
    if (percentile <= sellThreshold) {
      return SignalResult.DIR_SELL;
    }
    return null;
  }

  String evaluateStatus(IliResult iliResult, double expectedMove, double estimatedCost, String symbol) {
    if (isOnCooldown(symbol)) {
      return SignalResult.STATUS_COOLDOWN;
    }

    if (IliResult.STATUS_DEGRADED.equals(iliResult.dataStatus())) {
      return SignalResult.STATUS_SPECULATIVE;
    }

    if (estimatedCost > expectedMove) {
      return SignalResult.STATUS_COST_EXCEEDS;
    }

    recordSignal(symbol);
    return SignalResult.STATUS_ACTIONABLE;
  }

  double estimateExpectedMove(double[] historicalIliValues, double currentPercentile) {
    double mean = 0;
    for (double v : historicalIliValues) {
      mean += v;
    }
    mean /= historicalIliValues.length;

    double stdDev = 0;
    for (double v : historicalIliValues) {
      stdDev += Math.pow(v - mean, 2);
    }
    stdDev = Math.sqrt(stdDev / historicalIliValues.length);

    return stdDev * Math.abs(currentPercentile - 50.0) / 50.0;
  }

  boolean isOnCooldown(String symbol) {
    Instant last = lastSignalTime.get(symbol);
    return last != null && Instant
        .now()
        .isBefore(last.plusMillis(cooldownMs));
  }

  void recordSignal(String symbol) {
    lastSignalTime.put(symbol, Instant.now());
  }

  public void setThresholds(double buyThreshold, double sellThreshold) {
    this.buyThreshold = buyThreshold;
    this.sellThreshold = sellThreshold;
  }

  public void setTransactionCostBps(double bps) {
    this.transactionCostBps = bps;
  }

  public void setCooldownMs(long ms) {
    this.cooldownMs = ms;
  }
}
