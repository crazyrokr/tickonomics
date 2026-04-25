package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class MACDDivergenceStrategy extends BaseEquityStrategy {

    public MACDDivergenceStrategy() {
        super(EquityStrategyType.MACD_DIVERGENCE);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double priceChange = input.get("priceChange");
        Double macdChange = input.get("macdChange");

        if (priceChange == null || macdChange == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        boolean divergent = (priceChange > 0 && macdChange < 0) || (priceChange < 0 && macdChange > 0);

        if (!divergent) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double strength = Math.min(1.0, Math.abs(priceChange - macdChange));
        String direction = priceChange > 0 ? "SHORT" : "LONG";
        return buildSignal("UNIVERSE", direction, strength, ctx.confidence());
    }
}
