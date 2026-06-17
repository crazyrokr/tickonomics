package com.tickonomics.computation.options;

import com.tickonomics.cdm.model.CdmOptionSnapshot;
import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.BaseStrategy;
import com.tickonomics.computation.strategy.StrategyContext;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public abstract class BaseOptionStrategy implements BaseStrategy<LegGroup> {

  private final UUID id;
  private final StrategyType strategyType;

  protected BaseOptionStrategy(StrategyType strategyType) {
    this.id = UUID.nameUUIDFromBytes(strategyType
        .name()
        .getBytes(StandardCharsets.UTF_8));
    this.strategyType = strategyType;
  }

  @Override
  public UUID strategyId() {
    return id;
  }

  @Override
  public String name() {
    return strategyType.name();
  }

  @Override
  public String category() {
    return "OPTIONS";
  }

  @Override
  public boolean isActive() {
    return true;
  }

  @Override
  public final AlphaSignal compute(LegGroup legs, StrategyContext ctx) {
    if (ctx.irScore() < 0.9) {
      return AlphaSignal.neutral(this.id, legs.getUnderlying());
    }

    double signalStrength = calculateFormula(legs);

    Map<String, Double> metrics = Map.of(
        "net_delta",
        calculateNetDelta(legs),
        "net_theta",
        calculateNetTheta(legs),
        "spread_efficiency",
        calculateSpreadEfficiency(legs));

    String direction = mapDirection(signalStrength);
    return new AlphaSignal(
        this.id,
        legs.getUnderlying(),
        direction,
        Math.abs(signalStrength),
        ctx.confidence(),
        Instant.now(),
        metrics);
  }

  protected abstract double calculateFormula(LegGroup legs);

  protected double calculateNetDelta(LegGroup legs) {
    return legs
        .components()
        .stream()
        .mapToDouble(CdmOptionSnapshot::delta)
        .sum();
  }

  protected double calculateNetTheta(LegGroup legs) {
    return legs
        .components()
        .stream()
        .mapToDouble(CdmOptionSnapshot::theta)
        .sum();
  }

  protected double calculateSpreadEfficiency(LegGroup legs) {
    double netDelta = Math.abs(calculateNetDelta(legs));
    double premium = Math.abs(legs.netPremium());
    return premium > 0 ? netDelta / premium : 0.0;
  }

  protected double midPrice(CdmOptionSnapshot option) {
    return (option.bid() + option.ask()) / 2.0;
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
