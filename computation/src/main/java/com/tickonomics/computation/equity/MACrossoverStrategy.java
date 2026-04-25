package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class MACrossoverStrategy extends BaseEquityStrategy {

    public MACrossoverStrategy() {
        super(EquityStrategyType.MA_CROSSOVER);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double emaFast = input.get("emaFast");
        Double emaSlow = input.get("emaSlow");

        if (emaFast == null || emaSlow == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double crossover = emaFast - emaSlow;
        double denom = (emaSlow > 0) ? emaSlow : 1.0;
        double strength = Math.min(1.0, Math.abs(crossover) / denom);
        String direction = mapDirection(crossover);
        return buildSignal("UNIVERSE", direction, strength, ctx.confidence());
    }
}
