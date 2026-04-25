package com.tickonomics.computation.equity;

import com.tickonomics.computation.strategy.AlphaSignal;
import com.tickonomics.computation.strategy.StrategyContext;

import java.util.Map;

public class IchimokuCloudStrategy extends BaseEquityStrategy {

    public IchimokuCloudStrategy() {
        super(EquityStrategyType.ICHIMOKU_CLOUD);
    }

    @Override
    protected AlphaSignal computeSignal(Map<String, Double> input, StrategyContext ctx) {
        Double price = input.get("price");
        Double senkouA = input.get("senkouA");
        Double senkouB = input.get("senkouB");
        Double tenkan = input.get("tenkan");
        Double kijun = input.get("kijun");

        if (price == null || senkouA == null || senkouB == null || tenkan == null || kijun == null) {
            return AlphaSignal.neutral(strategyId(), "UNIVERSE");
        }

        double cloudTop = Math.max(senkouA, senkouB);
        double cloudBottom = Math.min(senkouA, senkouB);
        double cloudWidth = cloudTop - cloudBottom;
        double denom = cloudWidth > 0 ? cloudWidth : 1.0;

        boolean aboveCloud = price > cloudTop;
        boolean belowCloud = price < cloudBottom;
        boolean tkCross = tenkan > kijun;

        if (aboveCloud && tkCross) {
            double strength = Math.min(1.0, (price - cloudTop) / denom);
            return buildSignal("UNIVERSE", "LONG", strength, ctx.confidence());
        }
        if (belowCloud && !tkCross) {
            double strength = Math.min(1.0, (cloudBottom - price) / denom);
            return buildSignal("UNIVERSE", "SHORT", strength, ctx.confidence());
        }

        return AlphaSignal.neutral(strategyId(), "UNIVERSE");
    }
}
