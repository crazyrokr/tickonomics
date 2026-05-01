package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class EarningsSurpriseStrategy extends BaseEquityStrategy {

  public EarningsSurpriseStrategy() {
    super(EquityStrategyType.EARNINGS_SURPRISE);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double sue = input.get("sue");
    if (sue == null) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    double strength = Math.min(1.0, Math.abs(sue) / 3.0);
    String direction = mapDirection(sue);
    return buildSignal("UNIVERSE", direction, strength, ctx.confidence());
  }
}
