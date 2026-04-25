package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class BBBreakoutStrategy extends BaseEquityStrategy {

    public BBBreakoutStrategy() {
        super(EquityStrategyType.BB_BREAKOUT);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double price = input.get("price");
        Double upperBand = input.get("upperBand");
        Double lowerBand = input.get("lowerBand");

        if (price == null || upperBand == null || lowerBand == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double bandwidth = upperBand - lowerBand;
        if (bandwidth <= 0) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        if (price > upperBand) {
            double strength = Math.min(1.0, (price - upperBand) / bandwidth);
            return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
        }
        if (price < lowerBand) {
            double strength = Math.min(1.0, (lowerBand - price) / bandwidth);
            return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
