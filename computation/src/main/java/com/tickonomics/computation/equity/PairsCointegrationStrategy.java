package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class PairsCointegrationStrategy extends BaseEquityStrategy {

  public PairsCointegrationStrategy() {
    super(EquityStrategyType.PAIRS_COINTEGRATION);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double spread = input.get("spread");
    Double halfLife = input.get("halfLife");

    if (spread == null || halfLife == null) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    double strength = Math.min(1.0, Math.abs(spread) / (halfLife > 0 ? halfLife : 1.0));

    if (strength < 0.01) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    String direction = spread > 0 ? "SHORT" : "LONG";
    return buildSignal("PAIR", direction, strength, ctx.confidence());
  }
}
