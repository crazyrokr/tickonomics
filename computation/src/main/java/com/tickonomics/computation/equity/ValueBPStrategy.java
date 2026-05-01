package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class ValueBPStrategy extends BaseEquityStrategy {

  public ValueBPStrategy() {
    super(EquityStrategyType.VALUE_BP);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double bookToPrice = input.get("bookToPrice");
    Double universeMeanBP = input.get("universeMeanBP");

    if (bookToPrice == null) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    double mean = universeMeanBP != null ? universeMeanBP : 1.0;
    double deviation = bookToPrice - mean;
    double strength = Math.min(1.0, Math.abs(deviation) / mean);

    if (strength < 0.01) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    String direction = deviation > 0 ? "LONG" : "SHORT";
    return buildSignal("UNIVERSE", direction, strength, ctx.confidence());
  }
}
