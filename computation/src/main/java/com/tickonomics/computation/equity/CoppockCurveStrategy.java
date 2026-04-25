package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class CoppockCurveStrategy extends BaseEquityStrategy {

    public CoppockCurveStrategy() {
        super(EquityStrategyType.COPPOCK_CURVE);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double coppock = input.get("coppock");
        Double coppockPrev = input.get("coppockPrev");

        if (coppock == null || coppockPrev == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        boolean buySignal = coppockPrev < 0 && coppock > coppockPrev;

        if (buySignal) {
            double strength = Math.min(1.0, Math.abs(coppock - coppockPrev));
            return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
