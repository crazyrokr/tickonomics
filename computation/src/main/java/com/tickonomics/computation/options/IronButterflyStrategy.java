package com.tickonomics.computation.options;

public class IronButterflyStrategy extends BaseOptionStrategy {

    public IronButterflyStrategy() {
        super(StrategyType.IRON_BUTTERFLY);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double straddleMid = midPrice(legs.components().get(0)) + midPrice(legs.components().get(1));
        double wingsMid = midPrice(legs.components().get(2)) + midPrice(legs.components().get(3));
        return straddleMid - wingsMid;
    }
}
