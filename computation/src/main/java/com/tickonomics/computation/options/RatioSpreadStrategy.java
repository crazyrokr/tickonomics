package com.tickonomics.computation.options;

public class RatioSpreadStrategy extends BaseOptionStrategy {

    public RatioSpreadStrategy() {
        super(StrategyType.RATIO_SPREAD);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double shortMid = midPrice(legs.components().get(0));
        double longMid = midPrice(legs.components().get(1));
        double denominator = longMid == 0.0 ? 1.0 : longMid;
        return (2.0 * shortMid - longMid) / denominator;
    }
}
