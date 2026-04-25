package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class MeanReversionStrategy extends BaseEquityStrategy {

    public MeanReversionStrategy() {
        super(EquityStrategyType.MEAN_REVERSION);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double residual = input.get("residual");
        Double sigma = input.get("sigma");

        if (residual == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double denom = (sigma != null && sigma > 0) ? sigma : 1.0;
        double strength = Math.min(1.0, Math.abs(residual) / denom);

        if (strength < 0.01) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        String direction = residual > 0 ? "SHORT" : "LONG";
        return buildSignal("UNIVERSE", direction, strength, ctx.confidence());
    }
}
