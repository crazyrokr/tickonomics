package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class RSIOscillatorStrategy extends BaseEquityStrategy {

    public RSIOscillatorStrategy() {
        super(EquityStrategyType.RSI_OSCILLATOR);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double rsi = input.get("rsi");

        if (rsi == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        if (rsi > 70.0) {
            double strength = Math.min(1.0, (rsi - 70.0) / 30.0);
            return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
        }
        if (rsi < 30.0) {
            double strength = Math.min(1.0, (30.0 - rsi) / 30.0);
            return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
