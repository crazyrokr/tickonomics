package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class ParabolicSARStrategy extends BaseEquityStrategy {

  public ParabolicSARStrategy() {
    super(EquityStrategyType.PARABOLIC_SAR);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double price = input.get("price");
    Double sar = input.get("sar");

    if (price == null || sar == null) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    double diff = price - sar;
    double denom = (price > 0) ? price : 1.0;
    double strength = Math.min(1.0, Math.abs(diff) / denom);

    if (diff > 0) {
      return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
    }
    if (diff < 0) {
      return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
    }

    return AlphaSignal.neutral(strategyId(), "UNIVERSE");
  }
}
