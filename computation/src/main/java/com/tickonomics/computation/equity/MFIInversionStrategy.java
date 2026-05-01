package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class MFIInversionStrategy extends BaseEquityStrategy {

  public MFIInversionStrategy() {
    super(EquityStrategyType.MFI_INVERSION);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double mfi = input.get("mfi");

    if (mfi == null) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    if (mfi > 80.0) {
      double strength = Math.min(1.0, (mfi - 80.0) / 20.0);
      return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
    }
    if (mfi < 20.0) {
      double strength = Math.min(1.0, (20.0 - mfi) / 20.0);
      return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
    }

    return AlphaSignal.neutral(strategyId(), "UNIVERSE");
  }
}
