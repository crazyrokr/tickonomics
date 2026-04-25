package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class VolumeMomentumStrategy extends BaseEquityStrategy {

    public VolumeMomentumStrategy() {
        super(EquityStrategyType.VOLUME_MOMENTUM);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double volumeCurrent = input.get("volumeCurrent");
        Double volumeAvg = input.get("volumeAvg");

        if (volumeCurrent == null || volumeAvg == null || volumeAvg <= 0) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double ratio = volumeCurrent / volumeAvg;

        if (ratio > 1.5) {
            double strength = Math.min(1.0, (ratio - 1.0) / 2.0);
            return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
        }
        if (ratio < 0.5) {
            double strength = Math.min(1.0, (1.0 - ratio) / 2.0);
            return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
