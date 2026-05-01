package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class BollingerWidthStrategy extends BaseEquityStrategy {

  public BollingerWidthStrategy() {
    super(EquityStrategyType.BOLLINGER_WIDTH);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double bandwidth = input.get("bandwidth");
    Double avgBandwidth = input.get("avgBandwidth");

    if (bandwidth == null || avgBandwidth == null || avgBandwidth <= 0) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    double ratio = bandwidth / avgBandwidth;

    if (ratio < 0.5) {
      double strength = Math.min(1.0, 1.0 - ratio);
      return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
    }

    return AlphaSignal.neutral(strategyId(), "UNIVERSE");
  }
}
