package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class ZigZagFilterStrategy extends BaseEquityStrategy {

    public ZigZagFilterStrategy() {
        super(EquityStrategyType.ZIGZAG_FILTER);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double price = input.get("price");
        Double lastPivot = input.get("lastPivot");
        Double threshold = input.get("threshold");

        if (price == null || lastPivot == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double minMove = (threshold != null && threshold > 0) ? threshold : 0.05;
        double change = (price - lastPivot) / (lastPivot > 0 ? lastPivot : 1.0);

        if (change > minMove) {
            double strength = Math.min(1.0, change / (minMove * 2.0));
            return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
        }
        if (change < -minMove) {
            double strength = Math.min(1.0, Math.abs(change) / (minMove * 2.0));
            return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
