package com.tickonomics.computation.options;

public class SeagullSpreadStrategy extends BaseOptionStrategy {

    public SeagullSpreadStrategy() {
        super(StrategyType.SEAGULL_SPREAD);
    }

    @Override
    protected double calculateFormula(LegGroup legs) {
        double shortPutMid = midPrice(legs.components().get(0));
        double longCallMid = midPrice(legs.components().get(1));
        double longPutMid = midPrice(legs.components().get(2));
        return shortPutMid - longCallMid - longPutMid;
    }
}
