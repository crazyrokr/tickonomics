package com.tickonomics.computation.options;

public class BearCallSpreadStrategy extends BaseOptionStrategy {

    public BearCallSpreadStrategy() {
        super(StrategyType.BEAR_CALL_SPREAD);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double higherStrikeMid = midPrice(legs.components().get(0));
        double lowerStrikeMid = midPrice(legs.components().get(1));
        return higherStrikeMid - lowerStrikeMid;
    }
}
