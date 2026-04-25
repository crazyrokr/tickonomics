package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.List;
import java.util.Map;

public class MomentumDecileStrategy extends BaseEquityStrategy {

    private final UniverseAggregator aggregator;

    public MomentumDecileStrategy(UniverseAggregator aggregator) {
        super(EquityStrategyType.PRICE_MOMENTUM);
        this.aggregator = aggregator;
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> returns, StrategyContext ctx) {
        UniverseContext universe = aggregator.getContext();
        if (universe == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        if (returns.isEmpty()) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        List<Map.Entry<String, Double>> sorted = returns.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .toList();

        var topEntry = sorted.getFirst();
        var bottomEntry = sorted.getLast();
        double momentumSpread = topEntry.getValue() - bottomEntry.getValue();
        double denom = universe.universeStd() > 0 ? universe.universeStd() : 1.0;
        double strength = Math.min(1.0, Math.abs(momentumSpread) / denom);
        String direction = strength > 0.01 ? "LONG" : "NEUTRAL";
        return buildSignal("UNIVERSE", direction, strength, ctx.confidence());
    }
}
