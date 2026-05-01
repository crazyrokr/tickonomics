package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class TRIXReversalStrategy extends BaseEquityStrategy {

  public TRIXReversalStrategy() {
    super(EquityStrategyType.TRIX_REVERSAL);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double trix = input.get("trix");
    Double trixPrev = input.get("trixPrev");

    if (trix == null || trixPrev == null) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    boolean bullishReversal = trixPrev < 0 && trix > 0;
    boolean bearishReversal = trixPrev > 0 && trix < 0;

    if (bullishReversal) {
      double strength = Math.min(1.0, Math.abs(trix - trixPrev) * 10.0);
      return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
    }
    if (bearishReversal) {
      double strength = Math.min(1.0, Math.abs(trix - trixPrev) * 10.0);
      return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
    }

    return AlphaSignal.neutral(strategyId(), "UNIVERSE");
  }
}
