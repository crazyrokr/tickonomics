package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class SupportResistanceStrategy extends BaseEquityStrategy {

    public SupportResistanceStrategy() {
        super(EquityStrategyType.SUPPORT_RESISTANCE);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double price = input.get("price");
        Double high20 = input.get("high20");
        Double low20 = input.get("low20");

        if (price == null || high20 == null || low20 == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double range = high20 - low20;
        if (range <= 0) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double position = (price - low20) / range;

        if (position >= 1.0) {
            return buildSignal("UNIVERSE", "LONG", Math.min(1.0, position - 0.95), ctx.confidence());
        }
        if (position <= 0.0) {
            return buildSignal("UNIVERSE", "SHORT", Math.min(1.0, 0.05 - position), ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
