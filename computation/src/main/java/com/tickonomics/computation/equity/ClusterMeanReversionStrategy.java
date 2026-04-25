package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class ClusterMeanReversionStrategy extends BaseEquityStrategy {

    private final UniverseAggregator aggregator;

    public ClusterMeanReversionStrategy(UniverseAggregator aggregator) {
        super(EquityStrategyType.CLUSTER_MEAN_REVERSION);
        this.aggregator = aggregator;
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> returns, StrategyContext ctx) {
        UniverseContext universe = aggregator.getContext();
        if (universe == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        Map.Entry<String, Double> extreme = returns.entrySet().stream()
            .max(java.util.Comparator.comparingDouble(
                e -> Math.abs(e.getValue() - universe.universeMean())))
            .orElse(null);

        if (extreme == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double adjustedReturn = extreme.getValue() - universe.universeMean();
        double denom = universe.universeStd() > 0 ? universe.universeStd() : 1.0;
        double strength = Math.min(1.0, Math.abs(adjustedReturn) / denom);

        if (strength < 0.01) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        String direction = adjustedReturn > 0 ? "SHORT" : "LONG";
        return buildSignal(extreme.getKey(), direction, strength, ctx.confidence());
    }
}
