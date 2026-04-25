package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class AccumDistStrategy extends BaseEquityStrategy {

    public AccumDistStrategy() {
        super(EquityStrategyType.ACCUM_DIST);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double accumDistCurrent = input.get("accumDistCurrent");
        Double accumDistPrev = input.get("accumDistPrev");

        if (accumDistCurrent == null || accumDistPrev == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double change = accumDistCurrent - accumDistPrev;
        double denom = Math.abs(accumDistPrev) > 0 ? Math.abs(accumDistPrev) : 1.0;
        double strength = Math.min(1.0, Math.abs(change) / denom);
        String direction = mapDirection(change);
        return buildSignal("UNIVERSE", direction, strength, ctx.confidence());
    }
}
