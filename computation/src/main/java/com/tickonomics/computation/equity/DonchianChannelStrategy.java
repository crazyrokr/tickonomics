package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class DonchianChannelStrategy extends BaseEquityStrategy {

    public DonchianChannelStrategy() {
        super(EquityStrategyType.DONCHIAN_CHANNEL);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double price = input.get("price");
        Double highN = input.get("highN");
        Double lowN = input.get("lowN");

        if (price == null || highN == null || lowN == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        if (price >= highN) {
            return buildSignal("UNIVERSE", "LONG", 1.0, ctx.confidence());
        }
        if (price <= lowN) {
            return buildSignal("UNIVERSE", "SHORT", 1.0, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
