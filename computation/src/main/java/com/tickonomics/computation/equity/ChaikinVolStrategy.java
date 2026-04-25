package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class ChaikinVolStrategy extends BaseEquityStrategy {

    public ChaikinVolStrategy() {
        super(EquityStrategyType.CHAIKIN_VOL);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double volatilityCurrent = input.get("volatilityCurrent");
        Double volatilityPrev = input.get("volatilityPrev");

        if (volatilityCurrent == null || volatilityPrev == null || volatilityPrev <= 0) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double change = (volatilityCurrent - volatilityPrev) / volatilityPrev;

        if (change > 0.2) {
            double strength = Math.min(1.0, change);
            return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
        }
        if (change < -0.2) {
            double strength = Math.min(1.0, Math.abs(change));
            return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
