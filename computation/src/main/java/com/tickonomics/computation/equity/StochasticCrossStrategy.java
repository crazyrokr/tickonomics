package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class StochasticCrossStrategy extends BaseEquityStrategy {

  public StochasticCrossStrategy() {
    super(EquityStrategyType.STOCHASTIC_CROSS);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double kCurrent = input.get("kCurrent");
    Double dCurrent = input.get("dCurrent");
    Double kPrev = input.get("kPrev");
    Double dPrev = input.get("dPrev");

    if (kCurrent == null || dCurrent == null || kPrev == null || dPrev == null) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    boolean bullishCross = kPrev <= dPrev && kCurrent > dCurrent;
    boolean bearishCross = kPrev >= dPrev && kCurrent < dCurrent;

    if (bullishCross) {
      double strength = Math.min(1.0, Math.abs(kCurrent - dCurrent) / 50.0);
      return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
    }
    if (bearishCross) {
      double strength = Math.min(1.0, Math.abs(kCurrent - dCurrent) / 50.0);
      return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
    }

    return AlphaSignal.neutral(strategyId(), "UNIVERSE");
  }
}
