package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class AroonOscillatorStrategy extends BaseEquityStrategy {

  public AroonOscillatorStrategy() {
    super(EquityStrategyType.AROON_OSCILLATOR);
  }

  @Override
  protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
    Double aroonUp = input.get("aroonUp");
    Double aroonDown = input.get("aroonDown");

    if (aroonUp == null || aroonDown == null) {
      return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }

    double oscillator = aroonUp - aroonDown;
    double strength = Math.min(1.0, Math.abs(oscillator) / 100.0);

    if (oscillator > 50) {
      return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
    }
    if (oscillator < -50) {
      return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
    }

    return AlphaSignal.neutral(strategyId(), "UNIVERSE");
  }
}
