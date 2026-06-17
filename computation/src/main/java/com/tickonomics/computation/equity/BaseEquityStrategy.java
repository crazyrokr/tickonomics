package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.BaseStrategy;
import com.tickonomics.computation.strategy.StrategyContext;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public abstract class BaseEquityStrategy implements BaseStrategy<Map<String, Double>> {

  private final UUID id;
  private final EquityStrategyType type;

  protected BaseEquityStrategy(EquityStrategyType type) {
    this.id = UUID.nameUUIDFromBytes(type
        .name()
        .getBytes(StandardCharsets.UTF_8));
    this.type = type;
  }

  @Override
  public UUID strategyId() {
    return id;
  }

  @Override
  public String name() {
    return type.name();
  }

  @Override
  public String category() {
    return "EQUITY";
  }

  @Override
  public boolean isActive() {
    return true;
  }

  @Override
  public AlphaSignal compute(Map<String, Double> input, StrategyContext ctx) {
    if (ctx.irScore() < 0.9) {
      return AlphaSignal.neutral(id, "UNIVERSE");
    }
    return computeSignal(input, ctx);
  }

  protected abstract AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx);

  protected AlphaSignal buildSignal(String symbol, String direction, double strength, double confidence) {
    return new AlphaSignal(id, symbol, direction, strength, confidence, Instant.now(), Map.of());
  }

  protected String mapDirection(double signalStrength) {
    if (signalStrength > 0.01) {
      return "LONG";
    }
    if (signalStrength < -0.01) {
      return "SHORT";
    }
    return "NEUTRAL";
  }
}
