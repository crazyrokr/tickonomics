package com.tickonomics.computation.strategy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AlphaSignal(
    UUID strategyId,
    String symbol,
    String direction,
    double strength,
    double confidence,
    Instant timestamp,
    Map<String, Double> metrics) {
  public static AlphaSignal neutral(UUID strategyId, String symbol) {
    return new AlphaSignal(strategyId, symbol, "NEUTRAL", 0.0, 0.0, Instant.now(), Map.of());
  }
}
