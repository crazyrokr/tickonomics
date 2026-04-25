package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class KeltnerChannelStrategy extends BaseEquityStrategy {

    public KeltnerChannelStrategy() {
        super(EquityStrategyType.KELTNER_CHANNEL);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double price = input.get("price");
        Double upperChannel = input.get("upperChannel");
        Double lowerChannel = input.get("lowerChannel");

        if (price == null || upperChannel == null || lowerChannel == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double channelWidth = upperChannel - lowerChannel;
        if (channelWidth <= 0) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        if (price > upperChannel) {
            double strength = Math.min(1.0, (price - upperChannel) / channelWidth);
            return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
        }
        if (price < lowerChannel) {
            double strength = Math.min(1.0, (lowerChannel - price) / channelWidth);
            return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
